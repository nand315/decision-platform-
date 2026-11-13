package com.example.decision.config;

public class RoutingConfig {
    private String defaultOutcome = "APPROVED";
    private String failureOutcome = "REJECTED";

    public RoutingConfig() {}

    public RoutingConfig(String defaultOutcome, String failureOutcome) {
        this.defaultOutcome = defaultOutcome;
        this.failureOutcome = failureOutcome;
    }

    public String getDefaultOutcome() {
        return defaultOutcome;
    }

    public void setDefaultOutcome(String defaultOutcome) {
        this.defaultOutcome = defaultOutcome;
    }

    public String getFailureOutcome() {
        return failureOutcome;
    }

    public void setFailureOutcome(String failureOutcome) {
        this.failureOutcome = failureOutcome;
    }
}

