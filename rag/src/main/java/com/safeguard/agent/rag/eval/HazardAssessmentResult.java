package com.safeguard.agent.rag.eval;

import com.safeguard.agent.legal.model.LegalEvidence;
import java.util.List;

public record HazardAssessmentResult(
        String hazard,
        String category,
        String riskLevel,
        String riskExplanation,
        List<LegalEvidence> evidence,
        List<String> suggestion,
        Action action,
        String assessmentId) {

    public record Action(
            boolean needCreateTask,
            boolean requiresConfirmation,
            String toolName,
            String status) {
    }
}
