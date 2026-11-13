package com.example.decision.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {
    private String error;
    private List<String> details;
    private Instant timestamp;

    public ErrorResponseDto() {}

    public ErrorResponseDto(String error, List<String> details) {
        this.error = error;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public ErrorResponseDto(String error, String detail) {
        this(error, detail != null ? List.of(detail) : List.of());
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

