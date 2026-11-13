package com.example.decision.model;

import java.time.Instant;

public class StateHistoryEntry {
    private Long id;
    private String requestId;
    private RequestState fromState;
    private RequestState toState;
    private String reason;
    private Instant createdAt;

    public StateHistoryEntry() {}

    public StateHistoryEntry(Long id, String requestId, RequestState fromState,
                             RequestState toState, String reason, Instant createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public StateHistoryEntry(String requestId, RequestState fromState,
                             RequestState toState, String reason, Instant createdAt) {
        this(null, requestId, fromState, toState, reason, createdAt);
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

    public RequestState getFromState() {
        return fromState;
    }

    public void setFromState(RequestState fromState) {
        this.fromState = fromState;
    }

    public RequestState getToState() {
        return toState;
    }

    public void setToState(RequestState toState) {
        this.toState = toState;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

