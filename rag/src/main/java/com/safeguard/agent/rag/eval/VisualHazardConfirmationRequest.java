package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.util.List;

public record VisualHazardConfirmationRequest(
        List<ConfirmedCandidate> candidates,
        List<String> excludedCandidateIds,
        SafeGuardExecutionContext executionContext) {
    public record ConfirmedCandidate(String candidateId, String description, String operationObject) {}
}
