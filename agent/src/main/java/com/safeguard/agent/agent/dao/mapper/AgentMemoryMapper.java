package com.safeguard.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safeguard.agent.agent.dao.entity.AgentMemoryDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 长期记忆条目 Mapper，失效两条走条件 UPDATE 换取影响行数
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "SqlResolve"})
public interface AgentMemoryMapper extends BaseMapper<AgentMemoryDO> {

    /**
     * 取代旧条目：一道闸同时挡幻觉ID、已失效ID、他人ID，调用方只认返回 1
     */
    @Update("""
            UPDATE t_agent_memory
            SET invalid_at = CURRENT_TIMESTAMP, superseded_by = #{newId}
            WHERE id = #{oldId} AND user_id = #{userId} AND invalid_at IS NULL
            """)
    int supersede(@Param("userId") String userId,
                  @Param("oldId") String oldId,
                  @Param("newId") String newId);

    /**
     * 置 invalid_at 不留后继；用户撤回与容量淘汰共用这一条，库上不区分，谁干的只看日志
     */
    @Update("""
            UPDATE t_agent_memory
            SET invalid_at = CURRENT_TIMESTAMP
            WHERE id = #{oldId} AND user_id = #{userId} AND invalid_at IS NULL
            """)
    int retract(@Param("userId") String userId, @Param("oldId") String oldId);
}
