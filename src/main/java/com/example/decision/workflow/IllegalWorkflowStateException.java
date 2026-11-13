package com.example.decision.workflow;

public class IllegalWorkflowStateException extends RuntimeException {
    public IllegalWorkflowStateException(String message) {
        super(message);
    }
}

