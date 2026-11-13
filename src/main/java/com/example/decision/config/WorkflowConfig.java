package com.example.decision.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkflowConfig {
    private String name;
    private String version;
    private int maxRetries = 3;
    private List<String> dependencies = new ArrayList<>();
    private Map<String, FieldSchemaConfig> inputSchema = new LinkedHashMap<>();
    private List<StageConfig> stages = new ArrayList<>();
    private RoutingConfig routing = new RoutingConfig();
    private List<RuleStepConfig> ruleSteps = new ArrayList<>();

    public WorkflowConfig() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, FieldSchemaConfig> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, FieldSchemaConfig> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public List<StageConfig> getStages() {
        return stages;
    }

    public void setStages(List<StageConfig> stages) {
        this.stages = stages;
    }

    public RoutingConfig getRouting() {
        return routing;
    }

    public void setRouting(RoutingConfig routing) {
        this.routing = routing;
    }

    public List<RuleStepConfig> getRuleSteps() {
        return ruleSteps;
    }

    public void setRuleSteps(List<RuleStepConfig> ruleSteps) {
        this.ruleSteps = ruleSteps;
    }
}

