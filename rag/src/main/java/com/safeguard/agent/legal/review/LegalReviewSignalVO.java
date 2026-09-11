package com.safeguard.agent.legal.review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LegalReviewSignalVO(
        String id,
        String documentId,
        String scope,
        String targetId,
        String signalType,
        String message,
        List<String> relatedClauseIds,
        List<String> relatedClauseNos,
        List<String> relatedChunkIds,
        Map<String, Object> evidence,
        String reviewStatus,
        String reviewReason,
        Integer version,
        LocalDateTime reviewedAt
) {
}
