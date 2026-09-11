package com.safeguard.agent.core.chunk.blockaware;

import com.safeguard.agent.core.chunk.model.ChunkBudget;

import java.util.List;

/**
 * 切分上下文：调度器遍历 Block 列表时构造并传给每个 chunker，章节路径由 {@link HeadingHandler} 累积
 */
public record ChunkContext(List<String> outlinePath, ChunkBudget budget) {

    public ChunkContext {
        outlinePath = outlinePath == null ? List.of() : List.copyOf(outlinePath);
    }

    public static ChunkContext of(List<String> outlinePath, ChunkBudget budget) {
        return new ChunkContext(outlinePath, budget);
    }
}
