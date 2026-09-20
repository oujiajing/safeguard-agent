package com.safeguard.agent.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.safeguard.agent.knowledge.service.KnowledgeDocumentStats;
import org.apache.ibatis.annotations.Select;

public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {

    @Select("""
            SELECT
                COALESCE(SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END), 0) AS total_documents,
                COALESCE(SUM(CASE WHEN deleted = 0 AND enabled = 1 THEN 1 ELSE 0 END), 0) AS enabled_documents,
                COALESCE(SUM(CASE WHEN deleted = 0 AND processing_strategy = 'LEGAL' THEN 1 ELSE 0 END), 0) AS legal_documents,
                COALESCE(SUM(CASE WHEN deleted = 1 THEN 1 ELSE 0 END), 0) AS deleted_documents
            FROM t_knowledge_document
            """)
    KnowledgeDocumentStats selectStats();
}
