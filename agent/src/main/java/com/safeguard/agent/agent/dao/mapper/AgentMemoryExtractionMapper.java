package com.safeguard.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safeguard.agent.agent.dao.entity.AgentMemoryExtractionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 抽取台账 Mapper，水位是这张表上的聚合查询而不是独立游标行
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "SqlResolve"})
public interface AgentMemoryExtractionMapper extends BaseMapper<AgentMemoryExtractionDO> {

    /**
     * 水位：已结束抽取的最大末条ID，IN 列表定义了哪几个状态推进水位
     */
    @Select("""
            SELECT max(to_message_id)
            FROM t_agent_memory_extraction
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND status IN ('WRITTEN', 'NOOP', 'DROPPED')
            """)
    String selectWatermark(@Param("userId") String userId,
                           @Param("conversationId") String conversationId);

    /**
     * 结算：只允许从 PROCESSING 出发，重复结算返回 0
     * attemptCount 一并回写，快照失配靠把它退回上一档来实现「不计入尝试次数」
     */
    @Update("""
            UPDATE t_agent_memory_extraction
            SET status = #{status}, decision_count = #{decisionCount},
                attempt_count = #{attemptCount}, settle_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int settle(@Param("id") String id,
               @Param("status") String status,
               @Param("decisionCount") int decisionCount,
               @Param("attemptCount") int attemptCount);

    /**
     * 僵尸回收：JVM 死在 Judge 中途会留下永不结算的在飞行，超时后按失配结掉腾出 claim
     * 这一路留着尝试次数不退档：能把进程带走的抽取不该无限重来
     */
    @Update("""
            UPDATE t_agent_memory_extraction
            SET status = 'CONFLICT', settle_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND status = 'PROCESSING'
              AND create_time < CURRENT_TIMESTAMP - make_interval(mins => #{staleMinutes})
            """)
    int recycleStale(@Param("userId") String userId,
                     @Param("conversationId") String conversationId,
                     @Param("staleMinutes") int staleMinutes);

    /**
     * 同一区间已耗掉的尝试次数，行上的 attempt_count 已是结算后的有效值，不必再按状态过滤
     */
    @Select("""
            SELECT coalesce(max(attempt_count), 0)
            FROM t_agent_memory_extraction
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND to_message_id = #{toMessageId}
            """)
    int selectSpentAttempts(@Param("userId") String userId,
                            @Param("conversationId") String conversationId,
                            @Param("toMessageId") String toMessageId);
}
