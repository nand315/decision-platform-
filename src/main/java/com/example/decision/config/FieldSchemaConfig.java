package com.example.decision.config;

public class FieldSchemaConfig {
    private String type; // STRING, INTEGER, DECIMAL, NUMBER, BOOLEAN
    private boolean required = true;

    public FieldSchemaConfig() {}

    public FieldSchemaConfig(String type, boolean required) {
        this.type = type;
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}

