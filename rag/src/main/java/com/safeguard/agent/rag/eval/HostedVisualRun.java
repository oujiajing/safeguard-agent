package com.safeguard.agent.rag.eval;

import java.time.Instant;
import java.util.List;

public record HostedVisualRun(
        String runId,
        String analysisId,
        String sourceRecordId,
        int sourceRecordVersion,
        String userProvidedContext,
        String model,
        String status,
        Instant createdAt,
        List<HostedCandidate> candidates) {
    public record HostedCandidate(
            String candidateId,
            List<String> sourceAttachmentIds,
            String hazardType,
            String operationObject,
            String description,
            List<String> visibleEvidence,
            String potentialRisk,
            String judgement,
            double confidence,
            boolean needsManualVerification) {}
}
