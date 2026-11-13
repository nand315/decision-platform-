package com.example.decision.workflow;

import com.example.decision.config.FieldSchemaConfig;
import com.example.decision.config.RuleStepConfig;
import com.example.decision.config.WorkflowConfig;
import com.example.decision.config.WorkflowConfigLoader;
import com.example.decision.dependency.ExternalDependencyException;
import com.example.decision.dependency.SimulatedVerificationService;
import com.example.decision.model.*;
import com.example.decision.rules.RuleEngine;
import com.example.decision.store.AuditRepository;
import com.example.decision.store.HistoryRepository;
import com.example.decision.store.RequestRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowConfigLoader configLoader;
    private final RuleEngine ruleEngine;
    private final SimulatedVerificationService verificationService;
    private final RequestRepository requestRepository;
    private final HistoryRepository historyRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(
            WorkflowConfigLoader configLoader,
            RuleEngine ruleEngine,
            SimulatedVerificationService verificationService,
            RequestRepository requestRepository,
            HistoryRepository historyRepository,
            AuditRepository auditRepository,
            ObjectMapper objectMapper) {
        this.configLoader = configLoader;
        this.ruleEngine = ruleEngine;
        this.verificationService = verificationService;
        this.requestRepository = requestRepository;
        this.historyRepository = historyRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Intake and process a new request according to workflow configuration.
     */
    @Transactional(noRollbackFor = PayloadValidationException.class)
    public RequestRecord processNewRequest(CreateRequestDto dto) {
        String payloadHash = computeSha256(dto.payload());

        // Idempotency check
        Optional<RequestRecord> existingOpt = requestRepository.findByIdempotencyKey(dto.idempotencyKey());
        if (existingOpt.isPresent()) {
            RequestRecord existing = existingOpt.get();
            if (existing.getPayloadHash().equals(payloadHash)) {
                log.info("Duplicate request detected with key='{}'. Returning existing record.", dto.idempotencyKey());
                return existing;
            } else {
                log.warn("Idempotency conflict for key='{}'. Existing hash: {}, new hash: {}",
                        dto.idempotencyKey(), existing.getPayloadHash(), payloadHash);
                throw new IdempotencyConflictException("Conflict: Idempotency key '" + dto.idempotencyKey()
                        + "' has already been used with a different payload.");
            }
        }

        // Workflow lookup
        WorkflowConfig config = configLoader.getWorkflow(dto.workflow())
                .orElseThrow(() -> new PayloadValidationException("Workflow not found: " + dto.workflow(),
                        List.of("Workflow '" + dto.workflow() + "' is not registered in the system.")));

        // Schema validation
        List<String> validationErrors = validatePayloadAgainstSchema(config, dto.payload());
        if (!validationErrors.isEmpty()) {
            String tempId = UUID.randomUUID().toString();
            auditRepository.save(new AuditEntry(
                    tempId,
                    "INTAKE",
                    "VALIDATION_FAILED",
                    "Payload validation failed for workflow '" + dto.workflow() + "': " + String.join("; ", validationErrors),
                    Instant.now()
            ));
            throw new PayloadValidationException("Payload validation failed for workflow '" + dto.workflow() + "'", validationErrors);
        }

        // Initialize request record
        String requestId = UUID.randomUUID().toString();
        RequestRecord record = new RequestRecord();
        record.setId(requestId);
        record.setWorkflow(config.getName());
        record.setIdempotencyKey(dto.idempotencyKey());
        record.setPayloadHash(payloadHash);
        record.setPayload(dto.payload());
        record.setState(RequestState.RECEIVED);
        record.setRetryCount(0);
        record.setConfigVersion(config.getVersion());
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());

        requestRepository.save(record);
        historyRepository.save(new StateHistoryEntry(requestId, null, RequestState.RECEIVED, "Request intake accepted", Instant.now()));
        auditRepository.save(new AuditEntry(requestId, "INTAKE", "RECEIVED", "Request intake received and stored", Instant.now()));

        // Transition to VALIDATED
        transitionState(record, RequestState.VALIDATED, "Payload schema validation passed");
        auditRepository.save(new AuditEntry(requestId, "VALIDATION", "VALIDATED", "Payload schema conforms to workflow schema", Instant.now()));

        // Transition to EVALUATING
        transitionState(record, RequestState.EVALUATING, "Commencing workflow evaluation");
        auditRepository.save(new AuditEntry(requestId, "EVALUATION", "STARTED", "Workflow evaluation initiated", Instant.now()));

        // Execute external dependencies if configured
        if (config.getDependencies() != null && config.getDependencies().contains("verificationService")) {
            try {
                auditRepository.save(new AuditEntry(requestId, "DEPENDENCY", "CALL_ATTEMPT", "Calling external verification service", Instant.now()));
                verificationService.verify(config.getName(), requestId, dto.payload());
                auditRepository.save(new AuditEntry(requestId, "DEPENDENCY", "CALL_SUCCESS", "External verification succeeded", Instant.now()));
            } catch (ExternalDependencyException ex) {
                log.warn("External dependency failure for requestId={}: {}", requestId, ex.getMessage());
                auditRepository.save(new AuditEntry(requestId, "DEPENDENCY", "CALL_FAILED", ex.getMessage(), Instant.now()));
                record.setResultSummary("External dependency failed: " + ex.getMessage());
                transitionState(record, RequestState.RETRY_PENDING, "Dependency failure: " + ex.getMessage());
                requestRepository.update(record);
                return record;
            }
        }

        // Rule evaluation
        executeRulesAndFinalize(record, config);
        return record;
    }

    /**
     * Retries a request currently in RETRY_PENDING state.
     */
    @Transactional
    public RequestRecord retry(String requestId) {
        RequestRecord record = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

        if (record.getState() != RequestState.RETRY_PENDING) {
            throw new IllegalWorkflowStateException("Cannot retry request in state '" + record.getState()
                    + "'. Only RETRY_PENDING requests can be retried.");
        }

        WorkflowConfig config = configLoader.getWorkflow(record.getWorkflow())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow configuration not found: " + record.getWorkflow()));

        int nextRetryCount = record.getRetryCount() + 1;
        record.setRetryCount(nextRetryCount);

        auditRepository.save(new AuditEntry(requestId, "RETRY", "ATTEMPT",
                "Retry attempt #" + nextRetryCount + " of " + config.getMaxRetries(), Instant.now()));

        if (nextRetryCount > config.getMaxRetries()) {
            String reason = String.format("Maximum retries (%d) exhausted. Routed to manual review.", config.getMaxRetries());
            record.setResultSummary(reason);
            transitionState(record, RequestState.MANUAL_REVIEW, reason);
            auditRepository.save(new AuditEntry(requestId, "RETRY", "MAX_EXHAUSTED", reason, Instant.now()));
            requestRepository.update(record);
            return record;
        }

        transitionState(record, RequestState.EVALUATING, "Retrying execution attempt #" + nextRetryCount);

        // Re-call dependency
        if (config.getDependencies() != null && config.getDependencies().contains("verificationService")) {
            try {
                auditRepository.save(new AuditEntry(requestId, "DEPENDENCY", "RETRY_CALL_ATTEMPT", "Calling external verification service on retry", Instant.now()));
                verificationService.verify(config.getName(), requestId, record.getPayload());
                auditRepository.save(new AuditEntry(requestId, "DEPENDENCY", "RETRY_CALL_SUCCESS", "External verification succeeded on retry", Instant.now()));
            } catch (ExternalDependencyException ex) {
                log.warn("External dependency failed on retry #{} for requestId={}: {}", nextRetryCount, requestId, ex.getMessage());
                auditRepository.save(new AuditEntry(requestId, "DEPENDENCY", "RETRY_CALL_FAILED", ex.getMessage(), Instant.now()));
                
                if (nextRetryCount >= config.getMaxRetries()) {
                    String reason = String.format("Maximum retries (%d) reached after dependency failure: %s. Routed to manual review.",
                            config.getMaxRetries(), ex.getMessage());
                    record.setResultSummary(reason);
                    transitionState(record, RequestState.MANUAL_REVIEW, reason);
                    auditRepository.save(new AuditEntry(requestId, "RETRY", "MAX_EXHAUSTED", reason, Instant.now()));
                } else {
                    record.setResultSummary("External dependency failed on retry #" + nextRetryCount + ": " + ex.getMessage());
                    transitionState(record, RequestState.RETRY_PENDING, "Dependency failure on retry #" + nextRetryCount + ": " + ex.getMessage());
                }
                requestRepository.update(record);
                return record;
            }
        }

        executeRulesAndFinalize(record, config);
        return record;
    }

    /**
     * Resolves a MANUAL_REVIEW request to APPROVED or REJECTED.
     */
    @Transactional
    public RequestRecord review(String requestId, ReviewRequestDto dto) {
        RequestRecord record = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

        if (record.getState() != RequestState.MANUAL_REVIEW) {
            throw new IllegalWorkflowStateException("Cannot review request in state '" + record.getState()
                    + "'. Only MANUAL_REVIEW requests can be reviewed.");
        }

        RequestState targetState = RequestState.valueOf(dto.decision().trim().toUpperCase());
        if (targetState != RequestState.APPROVED && targetState != RequestState.REJECTED) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        }

        record.setReviewer(dto.reviewer());
        record.setReviewNote(dto.note());
        String reason = String.format("Manual review completed by %s: %s. Note: %s",
                dto.reviewer(), targetState, dto.note() != null ? dto.note() : "None");
        record.setResultSummary(reason);

        transitionState(record, targetState, reason);
        auditRepository.save(new AuditEntry(requestId, "MANUAL_REVIEW", targetState.name(),
                String.format("Reviewer: %s, Decision: %s, Note: %s", dto.reviewer(), targetState, dto.note()), Instant.now()));

        requestRepository.update(record);
        return record;
    }

    /**
     * Executes rules via RuleEngine and transitions request to final state.
     */
    private void executeRulesAndFinalize(RequestRecord record, WorkflowConfig config) {
        List<RuleResult> ruleResults = ruleEngine.evaluate(config.getRuleSteps(), record.getPayload());

        // Snapshot rules evaluated
        try {
            record.setRulesSnapshot(objectMapper.writeValueAsString(ruleResults));
        } catch (Exception e) {
            record.setRulesSnapshot("[]");
        }

        // Record audit entry for each evaluated rule
        for (RuleResult res : ruleResults) {
            auditRepository.save(new AuditEntry(
                    record.getId(),
                    "RULE_EVALUATION",
                    res.isPass() ? "PASS" : "FAIL",
                    String.format("[%s] field=%s, op=%s, expected=%s, actual=%s. %s",
                            res.getRuleId(), res.getField(), res.getOperator(),
                            res.getExpectedValue(), res.getActualValue(), res.getReason()),
                    Instant.now()
            ));
        }

        // Determine outcome based on rule results and workflow configuration
        RequestState finalState = RequestState.APPROVED;
        StringBuilder failureSummary = new StringBuilder();

        // Check for failures
        boolean hasReject = false;
        boolean hasManualReview = false;

        for (RuleResult res : ruleResults) {
            if (!res.isPass()) {
                // Find rule step config
                RuleStepConfig stepConfig = config.getRuleSteps().stream()
                        .filter(s -> s.getId().equals(res.getRuleId()))
                        .findFirst()
                        .orElse(null);

                String onFailure = (stepConfig != null && stepConfig.getOnFailure() != null)
                        ? stepConfig.getOnFailure().trim().toUpperCase()
                        : "REJECT";

                if ("MANUAL_REVIEW".equals(onFailure)) {
                    hasManualReview = true;
                    if (failureSummary.length() > 0) failureSummary.append("; ");
                    failureSummary.append(res.getReason());
                } else {
                    hasReject = true;
                    if (failureSummary.length() > 0) failureSummary.append("; ");
                    failureSummary.append(res.getReason());
                }
            }
        }

        if (hasReject) {
            finalState = RequestState.REJECTED;
            record.setResultSummary("Rejected: " + failureSummary);
        } else if (hasManualReview) {
            finalState = RequestState.MANUAL_REVIEW;
            record.setResultSummary("Manual review required: " + failureSummary);
        } else {
            finalState = RequestState.APPROVED;
            record.setResultSummary("All " + ruleResults.size() + " validation and business rules passed successfully.");
        }

        transitionState(record, finalState, record.getResultSummary());
        auditRepository.save(new AuditEntry(record.getId(), "FINAL_DECISION", finalState.name(), record.getResultSummary(), Instant.now()));

        requestRepository.update(record);
    }

    private void transitionState(RequestRecord record, RequestState toState, String reason) {
        RequestState fromState = record.getState();
        record.setState(toState);
        historyRepository.save(new StateHistoryEntry(record.getId(), fromState, toState, reason, Instant.now()));
        log.info("Request '{}' transitioned: {} -> {}. Reason: {}", record.getId(), fromState, toState, reason);
    }

    public RequestResponseDto getRequestDetails(String id) {
        RequestRecord record = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + id));
        List<StateHistoryEntry> history = historyRepository.findByRequestId(id);
        return RequestResponseDto.fromRecord(record, history);
    }

    public List<AuditEntry> getAuditTrail(String id) {
        // verify request exists
        if (requestRepository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Request not found: " + id);
        }
        return auditRepository.findByRequestId(id);
    }

    public ExplanationResponseDto getExplanation(String id) {
        RequestRecord record = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + id));

        List<RuleResult> ruleResults = Collections.emptyList();
        if (record.getRulesSnapshot() != null) {
            try {
                ruleResults = objectMapper.readValue(record.getRulesSnapshot(), new TypeReference<List<RuleResult>>() {});
            } catch (Exception e) {
                log.warn("Could not parse rules snapshot for request: {}", id);
            }
        }

        String reasoning;
        if (record.getState() == RequestState.APPROVED) {
            if (record.getReviewer() != null) {
                reasoning = String.format("Request approved after manual review by reviewer '%s'. Note: %s. Workflow: %s (v%s).",
                        record.getReviewer(), record.getReviewNote(), record.getWorkflow(), record.getConfigVersion());
            } else {
                reasoning = String.format("Request automatically approved. All %d rule(s) evaluated and passed successfully under workflow %s (v%s).",
                        ruleResults.size(), record.getWorkflow(), record.getConfigVersion());
            }
        } else if (record.getState() == RequestState.REJECTED) {
            if (record.getReviewer() != null) {
                reasoning = String.format("Request rejected after manual review by reviewer '%s'. Note: %s. Workflow: %s (v%s).",
                        record.getReviewer(), record.getReviewNote(), record.getWorkflow(), record.getConfigVersion());
            } else {
                long failCount = ruleResults.stream().filter(r -> !r.isPass()).count();
                reasoning = String.format("Request rejected due to %d rule violation(s): %s. Workflow: %s (v%s).",
                        failCount, record.getResultSummary(), record.getWorkflow(), record.getConfigVersion());
            }
        } else if (record.getState() == RequestState.MANUAL_REVIEW) {
            reasoning = String.format("Request routed to manual review under workflow %s (v%s). Reason: %s",
                    record.getWorkflow(), record.getConfigVersion(), record.getResultSummary());
        } else if (record.getState() == RequestState.RETRY_PENDING) {
            reasoning = String.format("Request pending retry (retry count: %d). Reason: %s",
                    record.getRetryCount(), record.getResultSummary());
        } else {
            reasoning = "Current request state: " + record.getState() + ". " + (record.getResultSummary() != null ? record.getResultSummary() : "");
        }

        return new ExplanationResponseDto(
                record.getId(),
                record.getWorkflow(),
                record.getConfigVersion(),
                record.getPayload(),
                ruleResults,
                record.getState().name(),
                reasoning
        );
    }

    private List<String> validatePayloadAgainstSchema(WorkflowConfig config, Map<String, Object> payload) {
        List<String> errors = new ArrayList<>();
        if (config.getInputSchema() == null) {
            return errors;
        }

        for (Map.Entry<String, FieldSchemaConfig> entry : config.getInputSchema().entrySet()) {
            String field = entry.getKey();
            FieldSchemaConfig schema = entry.getValue();
            Object value = payload.get(field);

            if (schema.isRequired()) {
                if (value == null || value.toString().trim().isEmpty()) {
                    errors.add(String.format("Field '%s' is required and cannot be empty", field));
                    continue;
                }
            }

            if (value != null && !value.toString().trim().isEmpty()) {
                String type = schema.getType() != null ? schema.getType().trim().toUpperCase() : "STRING";
                switch (type) {
                    case "INTEGER":
                        if (!isValidInteger(value)) {
                            errors.add(String.format("Field '%s' must be an integer, got: %s", field, value));
                        }
                        break;
                    case "DECIMAL":
                    case "NUMBER":
                        if (!isValidNumber(value)) {
                            errors.add(String.format("Field '%s' must be a numeric value, got: %s", field, value));
                        }
                        break;
                    case "BOOLEAN":
                        if (!(value instanceof Boolean) && !"true".equalsIgnoreCase(value.toString()) && !"false".equalsIgnoreCase(value.toString())) {
                            errors.add(String.format("Field '%s' must be a boolean, got: %s", field, value));
                        }
                        break;
                    case "STRING":
                    default:
                        // String accepts anything convertible
                        break;
                }
            }
        }

        return errors;
    }

    private boolean isValidInteger(Object value) {
        if (value instanceof Integer || value instanceof Long) return true;
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return d == Math.floor(d) && !Double.isInfinite(d);
        }
        try {
            Long.parseLong(value.toString().trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidNumber(Object value) {
        if (value instanceof Number) return true;
        try {
            new BigDecimal(value.toString().trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String computeSha256(Map<String, Object> payload) {
        try {
            // Sort keys to produce canonical JSON string
            Map<String, Object> sorted = new TreeMap<>(payload);
            String json = objectMapper.writeValueAsString(sorted);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}
