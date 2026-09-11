package com.safeguard.agent.rag.eval;

import com.safeguard.agent.legal.model.LegalEvidence;

import java.util.List;

public record LegalAnswerResponse(
        String answer,
        List<LegalEvidence> evidence,
        List<Citation> citations
) {
    public record Citation(String evidenceId, String referenceText) {
    }
}
