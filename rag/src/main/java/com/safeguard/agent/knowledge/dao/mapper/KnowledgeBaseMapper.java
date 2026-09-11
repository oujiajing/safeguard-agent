package com.safeguard.agent.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeBaseDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {

    @Select("SELECT COUNT(1) FROM t_knowledge_base WHERE collection_name = #{collectionName}")
    Long countByCollectionNameIncludingDeleted(@Param("collectionName") String collectionName);
}
