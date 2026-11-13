package com.example.decision.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateRequestDto(
    @NotBlank(message = "Workflow name must not be blank")
    String workflow,

    @NotBlank(message = "Idempotency key must not be blank")
    String idempotencyKey,

    @NotNull(message = "Payload must not be null")
    Map<String, Object> payload
) {}

