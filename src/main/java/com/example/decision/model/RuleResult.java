package com.example.decision.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleResult {
    private String ruleId;
    private String stage;
    private String field;
    private Object actualValue;
    private String operator;
    private Object expectedValue;
    private boolean pass;
    private String reason;

    public RuleResult() {}

    public RuleResult(String ruleId, String stage, String field, Object actualValue,
                      String operator, Object expectedValue, boolean pass, String reason) {
        this.ruleId = ruleId;
        this.stage = stage;
        this.field = field;
        this.actualValue = actualValue;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.pass = pass;
        this.reason = reason;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getActualValue() {
        return actualValue;
    }

    public void setActualValue(Object actualValue) {
        this.actualValue = actualValue;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(Object expectedValue) {
        this.expectedValue = expectedValue;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

