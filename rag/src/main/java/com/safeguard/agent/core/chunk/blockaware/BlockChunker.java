package com.safeguard.agent.core.chunk.blockaware;

import com.safeguard.agent.core.chunk.model.ChunkDraft;
import com.safeguard.agent.core.parser.model.Block;

import java.util.List;

/**
 * Block 类型专属的切分器：每个实现自报处理哪个 Block 类型、怎么切
 * <p>
 * 前者让调度器靠查表工作，新增 Block 类型只补一个实现即可；能否与邻居并块不由 Block 类型决定，
 * 由 {@link ChunkPacker} 按预算算出来
 *
 * @param <B> 该 chunker 处理的 Block 子类型
 */
public interface BlockChunker<B extends Block> {

    /**
     * 注册键：本 chunker 处理的 Block 类型
     */
    Class<B> blockType();

    /**
     * 把单个 Block 切分为若干草稿，可能为空；序号与块 ID 由装配阶段统一分配
     * <p>
     * 切与不切的判据全类型统一：整块撑得住 {@link com.safeguard.agent.core.chunk.model.ChunkBudget#toleranceChars()}
     * 就不切，超出才按块大小降级切分，且切点一律落在结构边界（行、表格行、列表项、句末）上
     */
    List<ChunkDraft> chunk(B block, ChunkContext ctx);
}
