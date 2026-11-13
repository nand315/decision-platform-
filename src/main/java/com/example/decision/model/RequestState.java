package com.example.decision.model;

public enum RequestState {
    RECEIVED,
    VALIDATED,
    EVALUATING,
    APPROVED,
    REJECTED,
    MANUAL_REVIEW,
    RETRY_PENDING,
    FAILED
}

