package com.example.decision.api;

import com.example.decision.model.*;
import com.example.decision.workflow.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/requests")
@Tag(name = "Workflow Requests", description = "Endpoints for request intake, evaluation, retry, review, audit, and explainability")
public class WorkflowController {

    private final WorkflowEngine workflowEngine;

    public WorkflowController(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @PostMapping
    @Operation(summary = "Submit a new workflow request with idempotency key and payload")
    public ResponseEntity<RequestResponseDto> createRequest(@Valid @RequestBody CreateRequestDto dto) {
        RequestRecord record = workflowEngine.processNewRequest(dto);
        RequestResponseDto response = workflowEngine.getRequestDetails(record.getId());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the current state and full state history of a request")
    public ResponseEntity<RequestResponseDto> getRequest(@PathVariable String id) {
        RequestResponseDto response = workflowEngine.getRequestDetails(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get the complete audit trail for a request")
    public ResponseEntity<List<AuditEntry>> getAuditTrail(@PathVariable String id) {
        List<AuditEntry> audit = workflowEngine.getAuditTrail(id);
        return ResponseEntity.ok(audit);
    }

    @GetMapping("/{id}/explanation")
    @Operation(summary = "Get a human-readable explanation of the decision, rules evaluated, and config version")
    public ResponseEntity<ExplanationResponseDto> getExplanation(@PathVariable String id) {
        ExplanationResponseDto explanation = workflowEngine.getExplanation(id);
        return ResponseEntity.ok(explanation);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a request currently in RETRY_PENDING state")
    public ResponseEntity<RequestResponseDto> retryRequest(@PathVariable String id) {
        RequestRecord record = workflowEngine.retry(id);
        RequestResponseDto response = workflowEngine.getRequestDetails(record.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Submit a manual review decision (APPROVED or REJECTED) for a request in MANUAL_REVIEW")
    public ResponseEntity<RequestResponseDto> reviewRequest(
            @PathVariable String id,
            @Valid @RequestBody ReviewRequestDto dto) {
        RequestRecord record = workflowEngine.review(id, dto);
        RequestResponseDto response = workflowEngine.getRequestDetails(record.getId());
        return ResponseEntity.ok(response);
    }
}

