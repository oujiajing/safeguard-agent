package com.safeguard.agent.legal.review;

import java.util.List;
import java.util.Map;

/** Immutable detector output. It contains evidence only; persistence assigns review state. */
public record ReviewSignalCandidate(
        String stableKey,
        String documentId,
        ReviewSignalScope scope,
        ReviewSignalType signalType,
        String targetId,
        List<String> relatedClauseIds,
        List<String> relatedChunkIds,
        String message,
        Map<String, Object> evidence
) {
    public ReviewSignalCandidate {
        relatedClauseIds = relatedClauseIds == null ? List.of() : List.copyOf(relatedClauseIds);
        relatedChunkIds = relatedChunkIds == null ? List.of() : List.copyOf(relatedChunkIds);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
