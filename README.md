# Configurable Workflow Decision Platform

A high-performance, generic decision platform built with **Java 17, Spring Boot 3, and JdbcTemplate**. The platform accepts structured requests, validates payloads against schemas, evaluates configurable business rules, runs workflow stages, tracks state transitions with full history, records comprehensive audit trails, and handles retries, idempotency, and failures.

Different business use cases (e.g. Loan Approvals, Insurance Claim Processing) run on the **exact same engine purely through YAML configuration**, requiring zero Java code changes.

---

## Prerequisites
- **Java 17+** (OpenJDK or Oracle JDK)
- **Maven 3.8+** (or bundled Maven wrapper)

---

## Quick Start: How to Run

### 1. Build and Run the Application
From the `decision-platform` directory:
```bash
mvn clean package
java -jar target/decision-platform-1.0.0.jar
```
Or run directly using Spring Boot Maven plugin:
```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

### 2. Interactive Swagger UI
The ONLY user interface provided is Swagger UI at:
**[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

---

## How to Run Tests

Run the complete JUnit 5 test suite (12 test scenarios covering all capabilities):
```bash
mvn test
```

Test coverage includes:
1. Happy path (approved)
2. Reject path (threshold failure)
3. Manual review routing and human resolution
4. Invalid input handling -> 422 with audit logging
5. Duplicate request detection (same key + same payload -> cached result, dependency not re-called)
6. Idempotency conflict (same key + different payload -> 409)
7. Dependency failure -> RETRY_PENDING
8. Retry succeeds after dependency recovers
9. Retries exhausted -> MANUAL_REVIEW
10. Partial save failure -> atomic rollback, no inconsistent state
11. Rule change scenario (hot-reload YAML from directory, new request gets new result, old request preserves version & explanation)
12. Multi-workflow execution (claim_processing runs on the exact same engine)

---

## API Usage with cURL Examples

### 1. Submit a Request (`POST /requests`)
```bash
curl -X POST http://localhost:8080/requests \
  -H "Content-Type: application/json" \
  -d '{
    "workflow": "loan_approval",
    "idempotencyKey": "req-loan-1001",
    "payload": {
      "applicantId": "APP-9988",
      "creditScore": 750,
      "annualIncome": 85000,
      "loanAmount": 200000,
      "employmentStatus": "EMPLOYED",
      "requestedTermMonths": 36
    }
  }'
```

### 2. Query Request State & History (`GET /requests/{id}`)
```bash
curl -X GET http://localhost:8080/requests/c2cb517f-2636-4078-8f44-d1959e4e07da
```

### 3. Get Full Audit Trail (`GET /requests/{id}/audit`)
```bash
curl -X GET http://localhost:8080/requests/c2cb517f-2636-4078-8f44-d1959e4e07da/audit
```

### 4. Get Decision Explanation (`GET /requests/{id}/explanation`)
```bash
curl -X GET http://localhost:8080/requests/c2cb517f-2636-4078-8f44-d1959e4e07da/explanation
```
Returns:
- Config version used at execution time
- Input payload snapshot
- List of triggered rules with field, operator, expected value, actual value, and pass/fail reason
- Final reasoning narrative

### 5. Retry a Pending Request (`POST /requests/{id}/retry`)
Re-runs external verification for a request in `RETRY_PENDING`:
```bash
curl -X POST http://localhost:8080/requests/c2cb517f-2636-4078-8f44-d1959e4e07da/retry
```
*Note: If retries exceed `maxRetries` from the workflow config, the request automatically escalates to `MANUAL_REVIEW`.*

### 6. Resolve a Manual Review (`POST /requests/{id}/review`)
```bash
curl -X POST http://localhost:8080/requests/387973b6-7292-4855-9c2b-db68611506a2/review \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "APPROVED",
    "reviewer": "sarah_underwriter",
    "note": "Verified tax returns and asset portfolio."
  }'
```

### 7. Hot-Reload Workflow Configs (`POST /admin/reload`)
Reloads all workflow YAML definitions without restarting the JVM:
```bash
curl -X POST http://localhost:8080/admin/reload
```

---

## How to Add or Change a Workflow via YAML

All business logic lives in declarative YAML files. To add a new workflow or change an existing one:

1. Create or edit a YAML file in `src/main/resources/workflows/` (or your configured `workflow.config-dir` directory):
```yaml
name: credit_limit_increase
version: "1.0.0"
maxRetries: 3
dependencies:
  - verificationService

inputSchema:
  customerId:
    type: STRING
    required: true
  currentLimit:
    type: DECIMAL
    required: true
  requestedLimit:
    type: DECIMAL
    required: true
  accountAgeMonths:
    type: INTEGER
    required: true

stages:
  - id: ELIGIBILITY
    name: "Account Standing"

routing:
  defaultOutcome: APPROVED
  failureOutcome: REJECTED

ruleSteps:
  - id: rule-min-account-age
    stage: ELIGIBILITY
    description: "Account must be open at least 6 months"
    field: accountAgeMonths
    operator: ">="
    value: 6
    onFailure: REJECT
    failureReason: "Account must have at least 6 months of tenure"

  - id: rule-limit-cap
    stage: ELIGIBILITY
    description: "Requested limit exceeding 25000 requires underwriter review"
    field: requestedLimit
    operator: "<="
    value: 25000
    onFailure: MANUAL_REVIEW
    failureReason: "Requested limit exceeds automatic approval limit"
```

2. Invoke `POST /admin/reload` or restart the application.
3. The new workflow is immediately active and available under `/requests` using `"workflow": "credit_limit_increase"`.

---

## Project Structure

```
decision-platform/
  pom.xml
  src/main/java/com/example/decision/
    DecisionPlatformApplication.java    # Spring Boot entry point & OpenAPI config
    api/
      WorkflowController.java           # REST endpoints (/requests, /requests/{id}...)
      AdminController.java              # Administrative operations (/admin/reload)
      GlobalExceptionHandler.java       # Centralized HTTP status code mapping
    model/
      RequestRecord.java                # Requests domain entity
      RequestState.java                 # Lifecycle enum (RECEIVED -> VALIDATED -> ...)
      RuleResult.java                   # Individual rule evaluation outcome
      AuditEntry.java                   # Audit trail entry
      StateHistoryEntry.java            # State machine transition history entry
      CreateRequestDto.java             # Request intake DTO with validations
      ReviewRequestDto.java             # Manual review resolution DTO
      RequestResponseDto.java           # Complete request response payload
      ExplanationResponseDto.java       # Human-readable decision explanation
      ErrorResponseDto.java             # Standardized error structure
    config/
      WorkflowConfig.java               # Top-level workflow schema POJO
      FieldSchemaConfig.java            # Field type & requiredness definition
      StageConfig.java                  # Workflow stage metadata
      RoutingConfig.java                # Routing decisions mapping
      RuleStepConfig.java               # Individual rule configuration
      ConditionConfig.java              # Conditional branching criteria (when)
      WorkflowConfigLoader.java         # YAML loader and hot-reloader
    rules/
      RuleEngine.java                   # Decoupled generic rule evaluation engine
    workflow/
      WorkflowEngine.java               # Orchestrator: intake, states, retries, audit
      IdempotencyConflictException.java # 409 Conflict
      PayloadValidationException.java   # 422 Unprocessable Entity
      ResourceNotFoundException.java    # 404 Not Found
      IllegalWorkflowStateException.java# 400 Bad Request
    store/
      RequestRepository.java            # JdbcTemplate persistence for requests
      HistoryRepository.java            # JdbcTemplate persistence for state history
      AuditRepository.java              # JdbcTemplate persistence for audit logs
      TestFaultInjector.java            # Fault injection hook for rollback verification
    dependency/
      SimulatedVerificationService.java # External dependency simulator (OK, FAIL_ALWAYS, FAIL_FIRST_N)
      ExternalDependencyException.java  # Dependency failure exception
  src/main/resources/
    application.properties              # Config: H2 file mode, Swagger UI, config dir
    schema.sql                          # Database DDL: requests, history, audit
    workflows/
      loan_approval.yaml                # Example 1: Loan approval rules & stages
      claim_processing.yaml             # Example 2: Insurance claim processing rules
  src/test/java/com/example/decision/
    DecisionPlatformIntegrationTests.java # 12 comprehensive integration tests
  src/test/resources/
    application-test.properties         # In-memory H2 config for testing
  docs/
    ARCHITECTURE.md                     # Deep architectural breakdown & trade-offs
    DECISION_EXAMPLES.md                # Real captured execution data & explanations
  README.md                             # Getting started & API documentation
```

---

## Architectural Highlights
- **100% Configurable**: No hardcoded business rules in Java. Add workflows, stages, and rules purely via YAML.
- **Idempotency**: Deterministic SHA-256 payload canonical hashing + database-level unique constraints prevent duplicate processing and duplicate dependency side-effects.
- **Explainability**: Every request stores a snapshot of the active rules and config version at evaluation time, guaranteeing reproducible explanations even after rules change.
- **Transaction Safety**: State transitions, state history entries, and audit trail logs are persisted in a single `@Transactional` method to guarantee absolute consistency.
- **Lightweight & Clean**: Pure Spring Boot 3 + JdbcTemplate with H2 file persistence. Zero bloated ORM dependencies.

