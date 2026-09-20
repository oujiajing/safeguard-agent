package com.safeguard.agent.rag.eval;

import java.util.List;
import com.safeguard.agent.rag.dto.SubQuestionRetrievalTrace;

public record LegalAnswerTrace(String originalQuestion, String rewrittenQuestion,
        List<String> subQuestions, List<String> matchedIntentIds, List<String> searchedKnowledgeBases,
        int retrievedCandidateCount, int evidenceCount, boolean evidenceGatePassed,
        List<SubQuestionRetrievalTrace> retrievalTraces) {
    public LegalAnswerTrace(String originalQuestion, String rewrittenQuestion,
            List<String> subQuestions, List<String> matchedIntentIds, List<String> searchedKnowledgeBases,
            int retrievedCandidateCount, int evidenceCount, boolean evidenceGatePassed) {
        this(originalQuestion, rewrittenQuestion, subQuestions, matchedIntentIds, searchedKnowledgeBases,
                retrievedCandidateCount, evidenceCount, evidenceGatePassed, List.of());
    }
}
