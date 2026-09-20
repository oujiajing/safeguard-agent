package com.safeguard.agent.rag.dto;

import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.rag.core.retrieval.RetrievalStageTrace;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KB 检索结果
 *
 * @param groupedContext 分组后的上下文文本
 * @param intentChunks   意图 ID -> 分片列表
 * @param eligibleIntentIds 允许参与模板选择和规则注入的意图 ID
 */
public record KbResult(String groupedContext,
                       Map<String, List<RetrievedChunk>> intentChunks,
                       Set<String> eligibleIntentIds,
                       List<RetrievalStageTrace> stageTraces) {
    public KbResult(String groupedContext, Map<String, List<RetrievedChunk>> intentChunks,
                    Set<String> eligibleIntentIds) {
        this(groupedContext, intentChunks, eligibleIntentIds, List.of());
    }
}
