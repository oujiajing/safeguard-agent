package com.safeguard.agent.core.ingest;

import com.safeguard.agent.core.chunk.model.ChunkBudget;
import com.safeguard.agent.core.parser.registry.ParseProfile;

/**
 * 文档级摄取配置（L3）：这一篇怎么解析、怎么切，对应 {@code t_knowledge_document.ingestion_spec} 一个 JSONB 列
 * <p>
 * 不含 embeddingModel：嵌入模型是知识库级（L2）约束性配置，文档级无权覆盖，只能由 {@link VectorTarget} 提供
 *
 * @param version      结构版本，用于未来演进时识别旧值
 * @param parseProfile 解析档位
 * @param budget       分块预算
 */
public record IngestionSpec(int version, ParseProfile parseProfile, ChunkBudget budget) {

    public static final int CURRENT_VERSION = 2;

    public IngestionSpec {
        if (version <= 0) {
            throw new IllegalArgumentException("version 必须 > 0，实际 " + version);
        }
        parseProfile = parseProfile == null ? ParseProfile.defaultProfile() : parseProfile;
        budget = budget == null ? ChunkBudget.defaults() : budget;
    }

    public static IngestionSpec defaults() {
        return new IngestionSpec(CURRENT_VERSION, ParseProfile.defaultProfile(), ChunkBudget.defaults());
    }

    public static IngestionSpec of(ParseProfile parseProfile, ChunkBudget budget) {
        return new IngestionSpec(CURRENT_VERSION, parseProfile, budget);
    }
}
