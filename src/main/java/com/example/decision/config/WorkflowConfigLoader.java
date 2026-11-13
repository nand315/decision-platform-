package com.example.decision.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component
public class WorkflowConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfigLoader.class);

    private final ObjectMapper yamlMapper;
    private final Map<String, WorkflowConfig> workflowMap = new ConcurrentHashMap<>();

    @Value("${workflow.config-dir:classpath:workflows}")
    private String configDir;

    public WorkflowConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        log.info("Loading workflow configurations from: {}", configDir);
        Map<String, WorkflowConfig> newMap = new HashMap<>();

        try {
            if (configDir.startsWith("classpath:")) {
                loadFromClasspath(configDir, newMap);
            } else {
                loadFromFileSystem(configDir, newMap);
            }

            workflowMap.clear();
            workflowMap.putAll(newMap);
            log.info("Loaded {} workflow definitions: {}", workflowMap.size(), workflowMap.keySet());
        } catch (Exception e) {
            log.error("Failed to load workflow configurations from {}", configDir, e);
            throw new RuntimeException("Workflow configuration loading failed: " + e.getMessage(), e);
        }
    }

    private void loadFromClasspath(String locationPattern, Map<String, WorkflowConfig> map) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = locationPattern;
        if (!pattern.endsWith("*.yaml") && !pattern.endsWith("*.yml")) {
            pattern = pattern.endsWith("/") ? pattern + "*.yaml" : pattern + "/*.yaml";
        }
        Resource[] resources = resolver.getResources(pattern);
        for (Resource res : resources) {
            try (InputStream is = res.getInputStream()) {
                WorkflowConfig config = yamlMapper.readValue(is, WorkflowConfig.class);
                validateConfig(config, res.getFilename());
                map.put(config.getName(), config);
            }
        }

        // Also check .yml
        String ymlPattern = pattern.replace(".yaml", ".yml");
        Resource[] ymlResources = resolver.getResources(ymlPattern);
        for (Resource res : ymlResources) {
            try (InputStream is = res.getInputStream()) {
                WorkflowConfig config = yamlMapper.readValue(is, WorkflowConfig.class);
                validateConfig(config, res.getFilename());
                map.put(config.getName(), config);
            }
        }
    }

    private void loadFromFileSystem(String dirPath, Map<String, WorkflowConfig> map) throws Exception {
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Config directory does not exist or is not a directory: {}", dirPath);
            return;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .toList();

            for (Path filePath : files) {
                File file = filePath.toFile();
                try (InputStream is = new FileInputStream(file)) {
                    WorkflowConfig config = yamlMapper.readValue(is, WorkflowConfig.class);
                    validateConfig(config, file.getName());
                    map.put(config.getName(), config);
                }
            }
        }
    }

    private void validateConfig(WorkflowConfig config, String source) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow config in " + source + " is missing mandatory 'name'");
        }
        if (config.getVersion() == null || config.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow config in " + source + " is missing mandatory 'version'");
        }
        if (config.getInputSchema() == null || config.getInputSchema().isEmpty()) {
            throw new IllegalArgumentException("Workflow config in " + source + " has no 'inputSchema'");
        }
        if (config.getRuleSteps() == null || config.getRuleSteps().isEmpty()) {
            throw new IllegalArgumentException("Workflow config in " + source + " has no 'ruleSteps'");
        }
        if (config.getMaxRetries() < 0) {
            throw new IllegalArgumentException("Workflow config in " + source + " has negative maxRetries");
        }
    }

    public Optional<WorkflowConfig> getWorkflow(String name) {
        return Optional.ofNullable(workflowMap.get(name));
    }

    public Collection<WorkflowConfig> getAllWorkflows() {
        return Collections.unmodifiableCollection(workflowMap.values());
    }

    public Set<String> getWorkflowNames() {
        return Collections.unmodifiableSet(workflowMap.keySet());
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getConfigDir() {
        return configDir;
    }
}

