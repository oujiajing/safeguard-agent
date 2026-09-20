package com.safeguard.agent.rag.dto;

import com.safeguard.agent.rag.core.retrieval.RetrievalStageTrace;
import java.util.List;

/** Retrieval-stage IDs for one rewritten sub-question. */
public record SubQuestionRetrievalTrace(String question, List<RetrievalStageTrace> stages) {
    public SubQuestionRetrievalTrace {
        question = question == null ? "" : question;
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
