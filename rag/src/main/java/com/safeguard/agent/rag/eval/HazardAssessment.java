package com.safeguard.agent.rag.eval;

import com.safeguard.agent.legal.model.LegalEvidence;
import java.time.Instant;
import java.util.List;

public record HazardAssessment(String assessmentId, String hazardDescription, String category,
        String riskLevel, String riskSummary, List<String> rectificationSuggestions,
        List<String> acceptanceCriteria, List<LegalEvidence> evidence,
        String status, HazardAssessmentResult.Action toolProposal, String taskId,
        String taskStatus, String errorReason, Instant createdTime, Instant confirmedTime,
        List<TraceStep> trace) {
    public enum Status { CREATED, CONFIRMATION_REQUIRED, CONFIRMED, TASK_CREATED, FAILED }
    public record TraceStep(String type, String message) {}
}
