package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record HostedHazardAssessmentRequest(
        @NotBlank String visualRunId,
        @NotEmpty List<@Valid SelectedCandidate> candidates,
        @Valid SafeGuardExecutionContext executionContext) {
    public record SelectedCandidate(
            @NotBlank String candidateId,
            String editedHazardType,
            String editedDescription) {}
}
