package com.safeguard.agent.rag.eval;

import java.time.Instant;
import java.util.List;

public record VisualHazardContext(
        String analysisId,
        String scene,
        String userProvidedContext,
        String model,
        Instant analyzedAt,
        List<Candidate> hazardCandidates) {
    public record Candidate(
            String candidateId,
            String hazardType,
            String operationObject,
            String description,
            List<String> visibleEvidence,
            String potentialRisk,
            String judgement,
            double confidence,
            boolean needsManualVerification) {}
}
