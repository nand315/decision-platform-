package com.example.decision.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExplanationResponseDto {
    private String requestId;
    private String workflow;
    private String configVersion;
    private Map<String, Object> inputData;
    private List<RuleResult> rulesTriggered;
    private String finalDecision;
    private String finalReasoning;

    public ExplanationResponseDto() {}

    public ExplanationResponseDto(String requestId, String workflow, String configVersion,
                                  Map<String, Object> inputData, List<RuleResult> rulesTriggered,
                                  String finalDecision, String finalReasoning) {
        this.requestId = requestId;
        this.workflow = workflow;
        this.configVersion = configVersion;
        this.inputData = inputData;
        this.rulesTriggered = rulesTriggered;
        this.finalDecision = finalDecision;
        this.finalReasoning = finalReasoning;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getWorkflow() {
        return workflow;
    }

    public void setWorkflow(String workflow) {
        this.workflow = workflow;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public Map<String, Object> getInputData() {
        return inputData;
    }

    public void setInputData(Map<String, Object> inputData) {
        this.inputData = inputData;
    }

    public List<RuleResult> getRulesTriggered() {
        return rulesTriggered;
    }

    public void setRulesTriggered(List<RuleResult> rulesTriggered) {
        this.rulesTriggered = rulesTriggered;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
    }

    public String getFinalReasoning() {
        return finalReasoning;
    }

    public void setFinalReasoning(String finalReasoning) {
        this.finalReasoning = finalReasoning;
    }
}

