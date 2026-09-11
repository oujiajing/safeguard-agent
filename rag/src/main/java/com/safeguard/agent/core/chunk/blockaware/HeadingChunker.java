package com.safeguard.agent.core.chunk.blockaware;

import com.safeguard.agent.core.chunk.model.ChunkDraft;
import com.safeguard.agent.core.chunk.model.ChunkMetadata;
import com.safeguard.agent.core.parser.model.HeadingBlock;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 标题 chunker：标题按原文位置回到正文
 * <p>
 * 标题不产块的话，{@code content} 就不是文档原貌而是被剥掉全部结构的裸正文，命中的块回填模型时也无从
 * 判断出自哪一节；井号数取原始级别，不按路径深度重算
 */
@Component
public class HeadingChunker implements BlockChunker<HeadingBlock> {

    private static final int MAX_LEVEL = 6;

    @Override
    public Class<HeadingBlock> blockType() {
        return HeadingBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(HeadingBlock block, ChunkContext ctx) {
        if (block == null || !StringUtils.hasText(block.text())) {
            return List.of();
        }
        String text = block.text().strip();
        ChunkMetadata metadata = ChunkMetadata.builder()
                .outlinePath(ctx.outlinePath())
                .provenance(block.provenance())
                .build();

        int level = Math.min(MAX_LEVEL, Math.max(1, block.level()));
        // 向量文本不带井号，markdown 标记对嵌入模型是零信息 token
        return List.of(ChunkDraft.ofHeading("#".repeat(level) + " " + text, text, metadata));
    }
}
