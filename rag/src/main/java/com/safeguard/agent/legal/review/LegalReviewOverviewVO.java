package com.safeguard.agent.legal.review;

public record LegalReviewOverviewVO(
        String detectionStatus,
        long signalCount,
        long reviewedSignalCount,
        long pendingSignalCount,
        long documentSignalCount,
        long affectedChunkCount
) {
}
