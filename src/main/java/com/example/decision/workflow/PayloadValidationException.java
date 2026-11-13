package com.example.decision.workflow;

import java.util.List;

public class PayloadValidationException extends RuntimeException {
    private final List<String> validationErrors;

    public PayloadValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}

