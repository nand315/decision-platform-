package com.example.decision.model;

import java.time.Instant;

public class AuditEntry {
    private Long id;
    private String requestId;
    private String step;
    private String action;
    private String details;
    private Instant createdAt;

    public AuditEntry() {}

    public AuditEntry(Long id, String requestId, String step, String action, String details, Instant createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.step = step;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public AuditEntry(String requestId, String step, String action, String details, Instant createdAt) {
        this(null, requestId, step, action, details, createdAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

