package com.example.decision;

import com.example.decision.config.WorkflowConfigLoader;
import com.example.decision.dependency.SimulatedVerificationService;
import com.example.decision.model.RequestState;
import com.example.decision.store.AuditRepository;
import com.example.decision.store.HistoryRepository;
import com.example.decision.store.RequestRepository;
import com.example.decision.store.TestFaultInjector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionPlatformIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimulatedVerificationService verificationService;

    @Autowired
    private TestFaultInjector faultInjector;

    @Autowired
    private WorkflowConfigLoader configLoader;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private HistoryRepository historyRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Reset services and hooks
        verificationService.setMode(SimulatedVerificationService.FailureMode.OK, 0);
        verificationService.resetCallCount();
        faultInjector.reset();

        // Reset config directory to classpath
        configLoader.setConfigDir("classpath:workflows");
        configLoader.reload();
    }

    @Test
    @DisplayName("1. Happy path: Request is validated, rules pass, approved")
    void testHappyPathApproved() throws Exception {
        Map<String, Object> payload = Map.of(
                "applicantId", "app-001",
                "creditScore", 750,
                "annualIncome", 95000,
                "loanAmount", 250000,
                "employmentStatus", "EMPLOYED",
                "requestedTermMonths", 36
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "happy-" + UUID.randomUUID(),
                "payload", payload
        );

        MvcResult result = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.workflow").value("loan_approval"))
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.configVersion").value("1.0.0"))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.history").isArray())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Check GET /requests/{id}
        mockMvc.perform(get("/requests/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.history.length()").value(4)); // RECEIVED, VALIDATED, EVALUATING, APPROVED

        // Check GET /requests/{id}/audit
        mockMvc.perform(get("/requests/{id}/audit", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));

        // Check GET /requests/{id}/explanation
        mockMvc.perform(get("/requests/{id}/explanation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(id))
                .andExpect(jsonPath("$.finalDecision").value("APPROVED"))
                .andExpect(jsonPath("$.finalReasoning").isNotEmpty())
                .andExpect(jsonPath("$.rulesTriggered").isArray());
    }

    @Test
    @DisplayName("2. Reject path: Credit score below cutoff causes rejection")
    void testRejectPath() throws Exception {
        Map<String, Object> payload = Map.of(
                "applicantId", "app-low-credit",
                "creditScore", 520, // Below 600 threshold
                "annualIncome", 50000,
                "loanAmount", 100000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "reject-" + UUID.randomUUID(),
                "payload", payload
        );

        MvcResult result = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("Credit score is below the mandatory cutoff of 600")))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/requests/{id}/explanation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalDecision").value("REJECTED"))
                .andExpect(jsonPath("$.finalReasoning").value(org.hamcrest.Matchers.containsString("Credit score is below the mandatory cutoff of 600")));
    }

    @Test
    @DisplayName("3. Manual review path and reviewer resolution")
    void testManualReviewAndResolution() throws Exception {
        Map<String, Object> payload = Map.of(
                "applicantId", "app-high-loan",
                "creditScore", 740,
                "annualIncome", 200000,
                "loanAmount", 750000, // Exceeds 500000 auto threshold
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "manual-" + UUID.randomUUID(),
                "payload", payload
        );

        MvcResult result = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("Loan amount exceeds auto-approval ceiling")))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Perform manual review approval
        Map<String, Object> reviewBody = Map.of(
                "decision", "APPROVED",
                "reviewer", "underwriter_sarah",
                "note", "High net worth verified through tax statements"
        );

        mockMvc.perform(post("/requests/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.reviewer").value("underwriter_sarah"))
                .andExpect(jsonPath("$.reviewNote").value("High net worth verified through tax statements"));

        // Verify history contains transition from MANUAL_REVIEW to APPROVED
        mockMvc.perform(get("/requests/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.history[-1].fromState").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.history[-1].toState").value("APPROVED"));
    }

    @Test
    @DisplayName("4. Invalid input -> 422 with clear errors recorded in audit log")
    void testInvalidInputReturns422() throws Exception {
        int initialAuditCount = auditRepository.countAll();

        // Missing applicantId and negative loanAmount
        Map<String, Object> invalidPayload = Map.of(
                "creditScore", "invalid-not-int",
                "annualIncome", 50000,
                "loanAmount", 100000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "invalid-" + UUID.randomUUID(),
                "payload", invalidPayload
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Field 'applicantId' is required"))));

        // Audit entry should be saved for the validation failure
        assertThat(auditRepository.countAll()).isGreaterThan(initialAuditCount);
    }

    @Test
    @DisplayName("5. Duplicate request: same key + same payload -> same result, dependency not re-called")
    void testDuplicateRequestReturnsSameResultWithoutReExecution() throws Exception {
        String key = "idem-dup-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "applicantId", "app-dup",
                "creditScore", 710,
                "annualIncome", 70000,
                "loanAmount", 50000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", key,
                "payload", payload
        );

        // First call
        MvcResult res1 = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andReturn();

        String id1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asText();
        int callCountAfterFirst = verificationService.getCallCount();
        assertThat(callCountAfterFirst).isEqualTo(1);

        // Second call with same key and same payload
        MvcResult res2 = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String id2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asText();
        assertThat(id2).isEqualTo(id1);

        // Dependency was NOT called again
        assertThat(verificationService.getCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("6. Idempotency conflict: same key, different payload -> 409")
    void testIdempotencyConflictReturns409() throws Exception {
        String key = "conflict-" + UUID.randomUUID();
        Map<String, Object> payload1 = Map.of(
                "applicantId", "app-conf-1",
                "creditScore", 710,
                "annualIncome", 70000,
                "loanAmount", 50000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request1 = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", key,
                "payload", payload1
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // Same key, different loanAmount
        Map<String, Object> payload2 = Map.of(
                "applicantId", "app-conf-1",
                "creditScore", 710,
                "annualIncome", 70000,
                "loanAmount", 99000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request2 = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", key,
                "payload", payload2
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Idempotency Conflict"));
    }

    @Test
    @DisplayName("7. Dependency failure -> RETRY_PENDING")
    void testDependencyFailureMovesToRetryPending() throws Exception {
        verificationService.setMode(SimulatedVerificationService.FailureMode.FAIL_ALWAYS);

        Map<String, Object> payload = Map.of(
                "applicantId", "app-dep-fail",
                "creditScore", 750,
                "annualIncome", 80000,
                "loanAmount", 100000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "dep-fail-" + UUID.randomUUID(),
                "payload", payload
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("External dependency failed")));
    }

    @Test
    @DisplayName("8. Retry succeeds after dependency recovers")
    void testRetrySucceedsAfterDependencyRecovers() throws Exception {
        verificationService.setMode(SimulatedVerificationService.FailureMode.FAIL_ALWAYS);

        Map<String, Object> payload = Map.of(
                "applicantId", "app-retry-success",
                "creditScore", 720,
                "annualIncome", 80000,
                "loanAmount", 120000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "retry-recov-" + UUID.randomUUID(),
                "payload", payload
        );

        MvcResult result = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETRY_PENDING"))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Dependency recovers
        verificationService.setMode(SimulatedVerificationService.FailureMode.OK);

        mockMvc.perform(post("/requests/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.retryCount").value(1));

        // Verify side-effects: calling retry on a completed request is not allowed
        mockMvc.perform(post("/requests/{id}/retry", id))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("9. Retries exhausted -> MANUAL_REVIEW")
    void testRetriesExhaustedMovesToManualReview() throws Exception {
        verificationService.setMode(SimulatedVerificationService.FailureMode.FAIL_ALWAYS);

        Map<String, Object> payload = Map.of(
                "applicantId", "app-exhaust-retries",
                "creditScore", 720,
                "annualIncome", 80000,
                "loanAmount", 120000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "exhaust-" + UUID.randomUUID(),
                "payload", payload
        );

        MvcResult result = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETRY_PENDING"))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // loan_approval maxRetries = 3
        // Retry 1: still fails -> RETRY_PENDING (retryCount = 1)
        mockMvc.perform(post("/requests/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.retryCount").value(1));

        // Retry 2: still fails -> RETRY_PENDING (retryCount = 2)
        mockMvc.perform(post("/requests/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.retryCount").value(2));

        // Retry 3: reaches maxRetries (3) -> MANUAL_REVIEW
        mockMvc.perform(post("/requests/{id}/retry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.retryCount").value(3))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("Maximum retries")));
    }

    @Test
    @DisplayName("10. Partial save failure -> rollback, no inconsistent state")
    void testPartialSaveFailureRollback() throws Exception {
        int initialRequestsCount = requestRepository.countAll();
        int initialHistoryCount = historyRepository.countAll();
        int initialAuditCount = auditRepository.countAll();

        // Inject fault on the 3rd write in the transactional flow
        faultInjector.enableOnWrite(3);

        String idempotencyKey = "fault-" + UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "applicantId", "app-fault",
                "creditScore", 750,
                "annualIncome", 90000,
                "loanAmount", 100000,
                "employmentStatus", "EMPLOYED"
        );

        Map<String, Object> request = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", idempotencyKey,
                "payload", payload
        );

        // Expect internal server error due to injected fault
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        // Verify transaction was rolled back: no partial request record or history was saved
        assertThat(requestRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();
        assertThat(requestRepository.countAll()).isEqualTo(initialRequestsCount);
        assertThat(historyRepository.countAll()).isEqualTo(initialHistoryCount);
        assertThat(auditRepository.countAll()).isEqualTo(initialAuditCount);

        // Reset fault injector and confirm retry works cleanly
        faultInjector.reset();
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));
    }

    @Test
    @DisplayName("11. Rule change scenario: Hot-reloading YAML config from directory")
    void testRuleChangeScenarioWithReload(@TempDir Path tempDir) throws Exception {
        // 1. Initial YAML version 1.0.0 with cutoff 600
        String initialYaml = """
            name: loan_approval
            version: "1.0.0"
            maxRetries: 3
            dependencies:
              - verificationService
            inputSchema:
              applicantId:
                type: STRING
                required: true
              creditScore:
                type: INTEGER
                required: true
            ruleSteps:
              - id: rule-min-credit-score
                description: "Credit score cutoff"
                field: creditScore
                operator: ">="
                value: 600
                onFailure: REJECT
                failureReason: "Credit score below cutoff of 600"
            """;

        Path yamlFile = tempDir.resolve("loan_approval.yaml");
        Files.writeString(yamlFile, initialYaml);

        // Configure loader to temp dir and reload
        configLoader.setConfigDir(tempDir.toString());
        configLoader.reload();

        // Request with creditScore 650 -> APPROVED under v1.0.0
        Map<String, Object> request1 = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "rule-change-1",
                "payload", Map.of("applicantId", "user-1", "creditScore", 650)
        );

        MvcResult res1 = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.configVersion").value("1.0.0"))
                .andReturn();

        String id1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asText();

        // 2. Change rule in YAML to v2.0.0 with stricter cutoff 700
        String updatedYaml = """
            name: loan_approval
            version: "2.0.0"
            maxRetries: 3
            dependencies:
              - verificationService
            inputSchema:
              applicantId:
                type: STRING
                required: true
              creditScore:
                type: INTEGER
                required: true
            ruleSteps:
              - id: rule-min-credit-score
                description: "Credit score cutoff"
                field: creditScore
                operator: ">="
                value: 700
                onFailure: REJECT
                failureReason: "Credit score below revised cutoff of 700"
            """;

        Files.writeString(yamlFile, updatedYaml);

        // Trigger POST /admin/reload
        mockMvc.perform(post("/admin/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Workflow configurations reloaded successfully"));

        // New request with exact same credit score (650) -> REJECTED under v2.0.0
        Map<String, Object> request2 = Map.of(
                "workflow", "loan_approval",
                "idempotencyKey", "rule-change-2",
                "payload", Map.of("applicantId", "user-2", "creditScore", 650)
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.configVersion").value("2.0.0"))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("Credit score below revised cutoff of 700")));

        // 3. Verify old request still maintains its old config version (1.0.0) and original explanation
        mockMvc.perform(get("/requests/{id}/explanation", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configVersion").value("1.0.0"))
                .andExpect(jsonPath("$.finalDecision").value("APPROVED"));
    }

    @Test
    @DisplayName("12. Second workflow (claim_processing) runs on the exact same engine")
    void testSecondWorkflowClaimProcessing() throws Exception {
        // Test claim approval
        Map<String, Object> claimPayload = Map.of(
                "claimId", "CLM-8801",
                "policyNumber", "POL-9921",
                "claimAmount", 12500.0,
                "incidentType", "ACCIDENT",
                "policeReportFiled", false
        );

        Map<String, Object> claimRequest = Map.of(
                "workflow", "claim_processing",
                "idempotencyKey", "claim-ok-" + UUID.randomUUID(),
                "payload", claimPayload
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claimRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.workflow").value("claim_processing"));

        // Test claim conditional rejection: THEFT requires policeReportFiled == true
        Map<String, Object> theftWithoutPoliceReport = Map.of(
                "claimId", "CLM-8802",
                "policyNumber", "POL-9921",
                "claimAmount", 8000.0,
                "incidentType", "THEFT",
                "policeReportFiled", false // Missing report for theft!
        );

        Map<String, Object> theftRequest = Map.of(
                "workflow", "claim_processing",
                "idempotencyKey", "theft-fail-" + UUID.randomUUID(),
                "payload", theftWithoutPoliceReport
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(theftRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("Police report is mandatory for all theft claims")));

        // Test claim manual review: Claim amount > 100000
        Map<String, Object> largeClaimPayload = Map.of(
                "claimId", "CLM-8803",
                "policyNumber", "POL-9921",
                "claimAmount", 150000.0, // > 100000 threshold
                "incidentType", "WEATHER",
                "policeReportFiled", false
        );

        Map<String, Object> largeClaimRequest = Map.of(
                "workflow", "claim_processing",
                "idempotencyKey", "claim-large-" + UUID.randomUUID(),
                "payload", largeClaimPayload
        );

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(largeClaimRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("Claim amount exceeds 100000 threshold")));
    }
}

