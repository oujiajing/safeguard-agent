package com.safeguard.agent.rag.eval;

import jakarta.validation.constraints.NotBlank;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;

public record HazardAssessmentRequest(@NotBlank String hazardDescription,
                                      SafeGuardExecutionContext executionContext) {
    public HazardAssessmentRequest(String hazardDescription) {
        this(hazardDescription, null);
    }
}
