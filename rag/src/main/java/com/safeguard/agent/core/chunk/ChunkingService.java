package com.safeguard.agent.core.chunk;

import com.safeguard.agent.core.chunk.blockaware.BlockAwareChunkerDispatcher;
import com.safeguard.agent.core.chunk.model.Chunk;
import com.safeguard.agent.core.chunk.model.ChunkAssembler;
import com.safeguard.agent.core.chunk.model.ChunkBudget;
import com.safeguard.agent.core.chunk.model.ChunkDraft;
import com.safeguard.agent.core.chunk.model.ChunkMetadata;
import com.safeguard.agent.core.parser.BlockTextRenderer;
import com.safeguard.agent.core.parser.model.Block;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 分块入口：解析产出的 Block 列表 → 成品块
 * <p>
 * 只有两个分支，分支依据是预算而不是用户选的策略：整文档单块，或按 Block 类型分发
 */
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final BlockAwareChunkerDispatcher blockAwareChunkerDispatcher;

    /**
     * 切分为块列表，序号从 0 单调递增，无可切内容时返回空列表
     *
     * @param budget 分块预算，整文档模式由 {@link ChunkBudget#isWholeDocument()} 表达
     */
    public List<Chunk> chunk(List<Block> blocks, ChunkBudget budget) {
        if (budget.isWholeDocument()) {
            return wholeDocument(blocks);
        }
        return blockAwareChunkerDispatcher.dispatch(blocks, budget);
    }

    private List<Chunk> wholeDocument(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        String whole = BlockTextRenderer.render(blocks);
        if (!StringUtils.hasText(whole)) {
            return List.of();
        }
        ChunkMetadata metadata = ChunkMetadata.builder()
                .provenance(blocks.get(0).provenance())
                .build();
        return List.of(ChunkAssembler.assemble(0, ChunkDraft.of(whole, metadata)));
    }
}
