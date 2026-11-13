package com.example.decision.api;

import com.example.decision.config.WorkflowConfigLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin Operations", description = "Administrative operations such as hot-reloading workflow configurations")
public class AdminController {

    private final WorkflowConfigLoader configLoader;

    public AdminController(WorkflowConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @PostMapping("/reload")
    @Operation(summary = "Reload all workflow YAML configurations without restarting the application")
    public ResponseEntity<Map<String, Object>> reloadWorkflows() {
        configLoader.reload();
        return ResponseEntity.ok(Map.of(
                "message", "Workflow configurations reloaded successfully",
                "loadedWorkflows", configLoader.getWorkflowNames()
        ));
    }
}

