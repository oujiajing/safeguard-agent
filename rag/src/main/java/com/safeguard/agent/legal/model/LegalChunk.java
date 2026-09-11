package com.safeguard.agent.legal.model;

public record LegalChunk(
        String chunkId,
        int chunkIndex,
        String content,
        String sourceText,
        int tokenCount,
        LegalChunkMetadata metadata
) {
    public LegalChunk {
        if (chunkId == null || chunkId.isBlank()) throw new IllegalArgumentException("chunkId 不能为空");
        if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex 必须 >= 0");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        sourceText = sourceText == null ? "" : sourceText;
        if (metadata == null || metadata.parentClauseId() == null || metadata.parentClauseId().isBlank()) {
            throw new IllegalArgumentException("每个 LegalChunk 必须有 parentClauseId");
        }
    }
}
