package com.safeguard.agent.rag.eval;

import com.safeguard.agent.legal.model.LegalEvidence;
import java.util.List;

public record HostedAssessmentResponse(
        String assessmentId,
        String visualRunId,
        String status,
        boolean knowledgeUsed,
        List<TraceStep> workflowTrace,
        List<Item> assessments) {
    public record TraceStep(String stage, String message, boolean executed) {}
    public record RiskLevel(String value, Double confidence, String basis, boolean requiresManualReview) {}
    public record Item(
            String candidateId,
            List<String> sourceAttachmentIds,
            String hazardType,
            String hazardDescription,
            String visualJudgement,
            double visualConfidence,
            List<String> visibleEvidence,
            String userProvidedContext,
            String legalRetrievalQuery,
            List<String> legalQueryFacts,
            RiskLevel riskLevel,
            List<String> rectificationMeasures,
            List<String> acceptanceCriteria,
            String aiReviewSuggestion,
            List<LegalEvidence> legalEvidence) {}
}
