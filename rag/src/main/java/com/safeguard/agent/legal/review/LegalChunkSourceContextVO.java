package com.safeguard.agent.legal.review;

public record LegalChunkSourceContextVO(
        boolean available,
        String chapterTitle,
        String sectionTitle,
        String clauseNo,
        String originalText,
        Integer pageStart,
        Integer pageEnd,
        String message
) {
}
