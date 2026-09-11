package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import jakarta.validation.constraints.NotBlank;

public record VisualHazardAnalysisRequest(
        String description,
        @NotBlank String imageBase64,
        SafeGuardExecutionContext executionContext) {}
