package com.example.decision.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewRequestDto(
    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be either APPROVED or REJECTED")
    String decision,

    @NotBlank(message = "Reviewer is required")
    String reviewer,

    String note
) {}

