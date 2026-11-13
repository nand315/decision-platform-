# Decision Platform - Execution Examples

This document contains real examples captured directly from live executions of the **Configurable Workflow Decision Platform**, demonstrating automated approvals, automated rejections, manual review routing with resolution, and multi-workflow generic execution.

---

## Example 1: Automated Approval (`loan_approval`)

### 1. Intake Request (`POST /requests`)
```json
{
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-APPROVED-001",
  "payload": {
    "applicantId": "APP-LIVE-1001",
    "creditScore": 760,
    "annualIncome": 95000,
    "loanAmount": 250000,
    "employmentStatus": "EMPLOYED",
    "requestedTermMonths": 36
  }
}
```

### 2. Output Response (`HTTP 200 OK`)
```json
{
  "id": "c2cb517f-2636-4078-8f44-d1959e4e07da",
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-APPROVED-001",
  "state": "APPROVED",
  "payload": {
    "applicantId": "APP-LIVE-1001",
    "creditScore": 760,
    "annualIncome": 95000,
    "loanAmount": 250000,
    "employmentStatus": "EMPLOYED",
    "requestedTermMonths": 36
  },
  "retryCount": 0,
  "configVersion": "1.0.0",
  "resultSummary": "All 5 validation and business rules passed successfully.",
  "createdAt": "2026-11-13T09:44:23.236605Z",
  "updatedAt": "2026-11-13T09:44:23.290371Z",
  "history": [
    {
      "id": 1,
      "requestId": "c2cb517f-2636-4078-8f44-d1959e4e07da",
      "fromState": null,
      "toState": "RECEIVED",
      "reason": "Request intake accepted",
      "createdAt": "2026-11-13T09:44:23.268623Z"
    },
    {
      "id": 2,
      "requestId": "c2cb517f-2636-4078-8f44-d1959e4e07da",
      "fromState": "RECEIVED",
      "toState": "VALIDATED",
      "reason": "Payload schema validation passed",
      "createdAt": "2026-11-13T09:44:23.272626Z"
    },
    {
      "id": 3,
      "requestId": "c2cb517f-2636-4078-8f44-d1959e4e07da",
      "fromState": "VALIDATED",
      "toState": "EVALUATING",
      "reason": "Commencing workflow evaluation",
      "createdAt": "2026-11-13T09:44:23.275178Z"
    },
    {
      "id": 4,
      "requestId": "c2cb517f-2636-4078-8f44-d1959e4e07da",
      "fromState": "EVALUATING",
      "toState": "APPROVED",
      "reason": "All 5 validation and business rules passed successfully.",
      "createdAt": "2026-11-13T09:44:23.287932Z"
    }
  ]
}
```

### 3. Rules Triggered & Explanation (`GET /requests/{id}/explanation`)
```json
{
  "requestId": "c2cb517f-2636-4078-8f44-d1959e4e07da",
  "workflow": "loan_approval",
  "configVersion": "1.0.0",
  "inputData": {
    "applicantId": "APP-LIVE-1001",
    "creditScore": 760,
    "annualIncome": 95000,
    "loanAmount": 250000,
    "employmentStatus": "EMPLOYED",
    "requestedTermMonths": 36
  },
  "rulesTriggered": [
    {
      "ruleId": "rule-applicant-id-present",
      "stage": "ELIGIBILITY",
      "field": "applicantId",
      "actualValue": "APP-LIVE-1001",
      "operator": "not_empty",
      "pass": true,
      "reason": "Rule 'rule-applicant-id-present' passed: applicantId not_empty (actual: APP-LIVE-1001)"
    },
    {
      "ruleId": "rule-positive-loan-amount",
      "stage": "ELIGIBILITY",
      "field": "loanAmount",
      "actualValue": 250000,
      "operator": ">",
      "expectedValue": 0,
      "pass": true,
      "reason": "Rule 'rule-positive-loan-amount' passed: loanAmount > 0 (actual: 250000)"
    },
    {
      "ruleId": "rule-min-credit-score",
      "stage": "RISK_ASSESSMENT",
      "field": "creditScore",
      "actualValue": 760,
      "operator": ">=",
      "expectedValue": 600,
      "pass": true,
      "reason": "Rule 'rule-min-credit-score' passed: creditScore >= 600 (actual: 760)"
    },
    {
      "ruleId": "rule-min-income",
      "stage": "RISK_ASSESSMENT",
      "field": "annualIncome",
      "actualValue": 95000,
      "operator": ">=",
      "expectedValue": 25000,
      "pass": true,
      "reason": "Rule 'rule-min-income' passed: annualIncome >= 25000 (actual: 95000)"
    },
    {
      "ruleId": "rule-high-loan-manual-review",
      "stage": "RISK_ASSESSMENT",
      "field": "loanAmount",
      "actualValue": 250000,
      "operator": "<=",
      "expectedValue": 500000,
      "pass": true,
      "reason": "Rule 'rule-high-loan-manual-review' passed: loanAmount <= 500000 (actual: 250000)"
    }
  ],
  "finalDecision": "APPROVED",
  "finalReasoning": "Request automatically approved. All 5 rule(s) evaluated and passed successfully under workflow loan_approval (v1.0.0)."
}
```

### 4. Audit Reasoning Trail (`GET /requests/{id}/audit`)
| Step | Action | Details | Timestamp |
| :--- | :--- | :--- | :--- |
| `INTAKE` | `RECEIVED` | Request intake received and stored | 2026-11-13T09:44:23.270Z |
| `VALIDATION` | `VALIDATED` | Payload schema conforms to workflow schema | 2026-11-13T09:44:23.274Z |
| `EVALUATION` | `STARTED` | Workflow evaluation initiated | 2026-11-13T09:44:23.276Z |
| `DEPENDENCY` | `CALL_ATTEMPT` | Calling external verification service | 2026-11-13T09:44:23.276Z |
| `DEPENDENCY` | `CALL_SUCCESS` | External verification succeeded | 2026-11-13T09:44:23.279Z |
| `RULE_EVALUATION` | `PASS` | `[rule-applicant-id-present]` actual=APP-LIVE-1001 | 2026-11-13T09:44:23.283Z |
| `RULE_EVALUATION` | `PASS` | `[rule-positive-loan-amount]` expected=0, actual=250000 | 2026-11-13T09:44:23.285Z |
| `RULE_EVALUATION` | `PASS` | `[rule-min-credit-score]` expected=600, actual=760 | 2026-11-13T09:44:23.285Z |
| `RULE_EVALUATION` | `PASS` | `[rule-min-income]` expected=25000, actual=95000 | 2026-11-13T09:44:23.286Z |
| `RULE_EVALUATION` | `PASS` | `[rule-high-loan-manual-review]` expected=500000, actual=250000 | 2026-11-13T09:44:23.286Z |
| `FINAL_DECISION` | `APPROVED` | All 5 validation and business rules passed successfully. | 2026-11-13T09:44:23.289Z |

---

## Example 2: Automated Rejection (`loan_approval`)

### 1. Intake Request (`POST /requests`)
```json
{
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-REJECTED-002",
  "payload": {
    "applicantId": "APP-LIVE-1002",
    "creditScore": 540,
    "annualIncome": 45000,
    "loanAmount": 150000,
    "employmentStatus": "EMPLOYED"
  }
}
```

### 2. Output Response (`HTTP 200 OK`)
```json
{
  "id": "81f22662-a386-4dbb-a7c4-295f1f25e2e4",
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-REJECTED-002",
  "state": "REJECTED",
  "payload": {
    "applicantId": "APP-LIVE-1002",
    "creditScore": 540,
    "annualIncome": 45000,
    "loanAmount": 150000,
    "employmentStatus": "EMPLOYED"
  },
  "retryCount": 0,
  "configVersion": "1.0.0",
  "resultSummary": "Rejected: Credit score is below the mandatory cutoff of 600",
  "createdAt": "2026-11-13T09:44:23.449265Z",
  "updatedAt": "2026-11-13T09:44:23.469340Z",
  "history": [
    { "fromState": null, "toState": "RECEIVED", "reason": "Request intake accepted" },
    { "fromState": "RECEIVED", "toState": "VALIDATED", "reason": "Payload schema validation passed" },
    { "fromState": "VALIDATED", "toState": "EVALUATING", "reason": "Commencing workflow evaluation" },
    { "fromState": "EVALUATING", "toState": "REJECTED", "reason": "Rejected: Credit score is below the mandatory cutoff of 600" }
  ]
}
```

### 3. Rules Triggered & Explanation (`GET /requests/{id}/explanation`)
```json
{
  "requestId": "81f22662-a386-4dbb-a7c4-295f1f25e2e4",
  "workflow": "loan_approval",
  "configVersion": "1.0.0",
  "inputData": {
    "applicantId": "APP-LIVE-1002",
    "creditScore": 540,
    "annualIncome": 45000,
    "loanAmount": 150000,
    "employmentStatus": "EMPLOYED"
  },
  "rulesTriggered": [
    {
      "ruleId": "rule-applicant-id-present",
      "stage": "ELIGIBILITY",
      "field": "applicantId",
      "actualValue": "APP-LIVE-1002",
      "operator": "not_empty",
      "pass": true,
      "reason": "Rule 'rule-applicant-id-present' passed: applicantId not_empty (actual: APP-LIVE-1002)"
    },
    {
      "ruleId": "rule-positive-loan-amount",
      "stage": "ELIGIBILITY",
      "field": "loanAmount",
      "actualValue": 150000,
      "operator": ">",
      "expectedValue": 0,
      "pass": true,
      "reason": "Rule 'rule-positive-loan-amount' passed: loanAmount > 0 (actual: 150000)"
    },
    {
      "ruleId": "rule-min-credit-score",
      "stage": "RISK_ASSESSMENT",
      "field": "creditScore",
      "actualValue": 540,
      "operator": ">=",
      "expectedValue": 600,
      "pass": false,
      "reason": "Credit score is below the mandatory cutoff of 600"
    },
    {
      "ruleId": "rule-min-income",
      "stage": "RISK_ASSESSMENT",
      "field": "annualIncome",
      "actualValue": 45000,
      "operator": ">=",
      "expectedValue": 25000,
      "pass": true,
      "reason": "Rule 'rule-min-income' passed: annualIncome >= 25000 (actual: 45000)"
    },
    {
      "ruleId": "rule-high-loan-manual-review",
      "stage": "RISK_ASSESSMENT",
      "field": "loanAmount",
      "actualValue": 150000,
      "operator": "<=",
      "expectedValue": 500000,
      "pass": true,
      "reason": "Rule 'rule-high-loan-manual-review' passed: loanAmount <= 500000 (actual: 150000)"
    }
  ],
  "finalDecision": "REJECTED",
  "finalReasoning": "Request rejected due to 1 rule violation(s): Rejected: Credit score is below the mandatory cutoff of 600. Workflow: loan_approval (v1.0.0)."
}
```

### 4. Audit Reasoning Trail (`GET /requests/{id}/audit`)
| Step | Action | Details | Timestamp |
| :--- | :--- | :--- | :--- |
| `INTAKE` | `RECEIVED` | Request intake received and stored | 2026-11-13T09:44:23.451Z |
| `VALIDATION` | `VALIDATED` | Payload schema conforms to workflow schema | 2026-11-13T09:44:23.454Z |
| `EVALUATION` | `STARTED` | Workflow evaluation initiated | 2026-11-13T09:44:23.456Z |
| `DEPENDENCY` | `CALL_ATTEMPT` | Calling external verification service | 2026-11-13T09:44:23.456Z |
| `DEPENDENCY` | `CALL_SUCCESS` | External verification succeeded | 2026-11-13T09:44:23.458Z |
| `RULE_EVALUATION` | `PASS` | `[rule-applicant-id-present]` actual=APP-LIVE-1002 | 2026-11-13T09:44:23.459Z |
| `RULE_EVALUATION` | `PASS` | `[rule-positive-loan-amount]` expected=0, actual=150000 | 2026-11-13T09:44:23.460Z |
| `RULE_EVALUATION` | `FAIL` | `[rule-min-credit-score]` expected=600, actual=540. Credit score is below the mandatory cutoff of 600 | 2026-11-13T09:44:23.460Z |
| `RULE_EVALUATION` | `PASS` | `[rule-min-income]` expected=25000, actual=45000 | 2026-11-13T09:44:23.461Z |
| `RULE_EVALUATION` | `PASS` | `[rule-high-loan-manual-review]` expected=500000, actual=150000 | 2026-11-13T09:44:23.465Z |
| `FINAL_DECISION` | `REJECTED` | Rejected: Credit score is below the mandatory cutoff of 600 | 2026-11-13T09:44:23.468Z |

---

## Example 3: Manual Review Routing & Human Resolution (`loan_approval`)

### 1. Intake Request with Large Loan Amount (`POST /requests`)
```json
{
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-MANUAL-003",
  "payload": {
    "applicantId": "APP-LIVE-1003",
    "creditScore": 780,
    "annualIncome": 250000,
    "loanAmount": 850000,
    "employmentStatus": "EMPLOYED"
  }
}
```

### 2. Initial Routing to `MANUAL_REVIEW` (`HTTP 200 OK`)
```json
{
  "id": "387973b6-7292-4855-9c2b-db68611506a2",
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-MANUAL-003",
  "state": "MANUAL_REVIEW",
  "payload": {
    "applicantId": "APP-LIVE-1003",
    "creditScore": 780,
    "annualIncome": 250000,
    "loanAmount": 850000,
    "employmentStatus": "EMPLOYED"
  },
  "resultSummary": "Manual review required: Loan amount exceeds auto-approval ceiling of 500000; routed to underwriter"
}
```

### 3. Reviewer Adjudication (`POST /requests/{id}/review`)
```json
{
  "decision": "APPROVED",
  "reviewer": "senior_underwriter_rachel",
  "note": "Verified $500k liquid collateral and past clean credit history."
}
```

### 4. Final Output Response after Human Review
```json
{
  "id": "387973b6-7292-4855-9c2b-db68611506a2",
  "workflow": "loan_approval",
  "idempotencyKey": "LIVE-MANUAL-003",
  "state": "APPROVED",
  "reviewer": "senior_underwriter_rachel",
  "reviewNote": "Verified $500k liquid collateral and past clean credit history.",
  "resultSummary": "Manual review completed by senior_underwriter_rachel: APPROVED. Note: Verified $500k liquid collateral and past clean credit history.",
  "history": [
    { "fromState": null, "toState": "RECEIVED", "reason": "Request intake accepted" },
    { "fromState": "RECEIVED", "toState": "VALIDATED", "reason": "Payload schema validation passed" },
    { "fromState": "VALIDATED", "toState": "EVALUATING", "reason": "Commencing workflow evaluation" },
    { "fromState": "EVALUATING", "toState": "MANUAL_REVIEW", "reason": "Manual review required: Loan amount exceeds auto-approval ceiling of 500000; routed to underwriter" },
    { "fromState": "MANUAL_REVIEW", "toState": "APPROVED", "reason": "Manual review completed by senior_underwriter_rachel: APPROVED. Note: Verified $500k liquid collateral and past clean credit history." }
  ]
}
```

### 5. Final Explanation (`GET /requests/{id}/explanation`)
```json
{
  "requestId": "387973b6-7292-4855-9c2b-db68611506a2",
  "workflow": "loan_approval",
  "configVersion": "1.0.0",
  "finalDecision": "APPROVED",
  "finalReasoning": "Request approved after manual review by reviewer 'senior_underwriter_rachel'. Note: Verified $500k liquid collateral and past clean credit history.. Workflow: loan_approval (v1.0.0)."
}
```

---

## Example 4: Second Workflow Execution (`claim_processing`)

Demonstrating that the identical engine executes an entirely different domain without Java modifications:

### 1. Intake Request (`POST /requests`)
```json
{
  "workflow": "claim_processing",
  "idempotencyKey": "LIVE-CLAIM-004",
  "payload": {
    "claimId": "CLM-2026-99",
    "policyNumber": "POL-AUTO-8821",
    "claimAmount": 14500.0,
    "incidentType": "THEFT",
    "policeReportFiled": true
  }
}
```

### 2. Explanation Response (`GET /requests/{id}/explanation`)
```json
{
  "workflow": "claim_processing",
  "configVersion": "1.0.0",
  "finalDecision": "APPROVED",
  "finalReasoning": "Request automatically approved. All 5 rule(s) evaluated and passed successfully under workflow claim_processing (v1.0.0)."
}
```
All rules (policy verification, covered incident in list `[ACCIDENT, THEFT, WEATHER, VANDALISM]`, conditional theft police report check, and auto-settlement ceiling) passed successfully.

