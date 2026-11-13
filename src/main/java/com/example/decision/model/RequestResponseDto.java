package com.example.decision.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestResponseDto {
    private String id;
    private String workflow;
    private String idempotencyKey;
    private RequestState state;
    private Map<String, Object> payload;
    private int retryCount;
    private String configVersion;
    private String resultSummary;
    private String reviewer;
    private String reviewNote;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StateHistoryEntry> history;

    public RequestResponseDto() {}

    public static RequestResponseDto fromRecord(RequestRecord record, List<StateHistoryEntry> history) {
        RequestResponseDto dto = new RequestResponseDto();
        dto.setId(record.getId());
        dto.setWorkflow(record.getWorkflow());
        dto.setIdempotencyKey(record.getIdempotencyKey());
        dto.setState(record.getState());
        dto.setPayload(record.getPayload());
        dto.setRetryCount(record.getRetryCount());
        dto.setConfigVersion(record.getConfigVersion());
        dto.setResultSummary(record.getResultSummary());
        dto.setReviewer(record.getReviewer());
        dto.setReviewNote(record.getReviewNote());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        dto.setHistory(history);
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflow() {
        return workflow;
    }

    public void setWorkflow(String workflow) {
        this.workflow = workflow;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public RequestState getState() {
        return state;
    }

    public void setState(RequestState state) {
        this.state = state;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<StateHistoryEntry> getHistory() {
        return history;
    }

    public void setHistory(List<StateHistoryEntry> history) {
        this.history = history;
    }
}

