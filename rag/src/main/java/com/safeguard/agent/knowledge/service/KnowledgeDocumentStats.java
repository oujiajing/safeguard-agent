package com.safeguard.agent.knowledge.service;

/**
 * 知识库文档聚合统计，已删除记录单独统计，不混入有效文档总数。
 */
public record KnowledgeDocumentStats(
        long totalDocuments,
        long enabledDocuments,
        long legalDocuments,
        long deletedDocuments) {
}
