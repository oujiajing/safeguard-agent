package com.safeguard.agent.rag.core.retrieval;

import java.util.List;

/** Identifies the chunks surviving each retrieval stage for evaluation and diagnosis. */
public record RetrievalStageTrace(String stage, List<String> chunkIds) {
    public RetrievalStageTrace {
        stage = stage == null ? "UNKNOWN" : stage;
        chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
    }
}
