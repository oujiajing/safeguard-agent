package com.safeguard.agent.rag.core.retrieval;

import cn.hutool.core.util.StrUtil;
import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.framework.convention.RetrievedChunkKey;
import com.safeguard.agent.rag.core.intent.IntentNode;
import com.safeguard.agent.rag.core.intent.NodeScore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record KnowledgeRetrievalResult(List<RetrievedChunk> chunks,
                                       Map<String, Set<String>> intentIdsByChunkKey,
                                       Set<String> directedIntentIds,
                                       List<RetrievalStageTrace> stageTraces) {

    public KnowledgeRetrievalResult(List<RetrievedChunk> chunks,
                                    Map<String, Set<String>> intentIdsByChunkKey,
                                    Set<String> directedIntentIds) {
        this(chunks, intentIdsByChunkKey, directedIntentIds, List.of());
    }

    public KnowledgeRetrievalResult {
        chunks = chunks == null ? List.of() : chunks;
        intentIdsByChunkKey = intentIdsByChunkKey == null ? Map.of() : intentIdsByChunkKey;
        directedIntentIds = directedIntentIds == null
                ? Set.of()
                : Set.copyOf(directedIntentIds);
        stageTraces = stageTraces == null ? List.of() : List.copyOf(stageTraces);
    }

    public static KnowledgeRetrievalResult empty() {
        return new KnowledgeRetrievalResult(List.of(), Map.of(), Set.of(), List.of());
    }

    public Set<String> retrievedIntentIds() {
        Set<String> intentIds = new LinkedHashSet<>();
        intentIdsByChunkKey.values().stream()
                .filter(Objects::nonNull)
                .forEach(intentIds::addAll);
        return Collections.unmodifiableSet(intentIds);
    }

    public Set<String> eligibleIntentIds(List<NodeScore> candidateIntents) {
        Set<String> retrievedIntentIds = retrievedIntentIds();
        return candidateIntents.stream()
                .filter(Objects::nonNull)
                .map(NodeScore::getNode)
                .filter(Objects::nonNull)
                .map(IntentNode::getId)
                .filter(StrUtil::isNotBlank)
                .filter(intentId -> !directedIntentIds.contains(intentId)
                        || retrievedIntentIds.contains(intentId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Map<String, List<RetrievedChunk>> groupByIntent(String globalKey) {
        Map<String, List<RetrievedChunk>> grouped = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            Set<String> intentIds = intentIdsByChunkKey.get(RetrievedChunkKey.of(chunk));
            if (intentIds == null || intentIds.isEmpty()) {
                grouped.computeIfAbsent(globalKey, ignored -> new ArrayList<>()).add(chunk);
                continue;
            }
            for (String intentId : intentIds) {
                grouped.computeIfAbsent(intentId, ignored -> new ArrayList<>()).add(chunk);
            }
        }
        return grouped;
    }
}
