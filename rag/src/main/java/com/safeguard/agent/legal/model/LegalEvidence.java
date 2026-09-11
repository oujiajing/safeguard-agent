package com.safeguard.agent.legal.model;

/**
 * 面向法规问答和 Citation 的稳定证据 DTO。
 * 不向上层暴露 KnowledgeChunkDO 或数据库对象。
 */
public record LegalEvidence(
        String evidenceId,
        String documentTitle,
        String standardNo,
        String clauseNo,
        String hierarchyPath,
        String contentRole,
        String content,
        String chunkId,
        Integer pageNo,
        Float retrievalScore,
        Float rerankScore
) {
}
