package com.safeguard.agent.knowledge.sink;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.safeguard.agent.core.chunk.model.EmbeddedChunk;
import com.safeguard.agent.core.ingest.DocumentRef;
import com.safeguard.agent.core.ingest.VectorTarget;
import com.safeguard.agent.core.ingest.sink.ChunkSink;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.infra.token.TokenCounterService;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeChunkDO;
import com.safeguard.agent.knowledge.dao.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 关系库落点：写 {@code t_knowledge_chunk}，展示文本与向量文本一并落库
 * <p>
 * {@code embedding_text} 落库不是为了展示：它让换嵌入模型时可以直接重嵌入而不必重新解析（省掉版面
 * 解析与视觉模型的重复成本），也让人工编辑单块后能正确重算向量文本
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RelationalChunkSink implements ChunkSink {

    private final KnowledgeChunkMapper chunkMapper;
    private final TokenCounterService tokenCounterService;

    @Override
    public void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks) {
        deleteDocument(target, doc);
        if (chunks.isEmpty()) {
            return;
        }
        String username = UserContext.getUsername();
        List<KnowledgeChunkDO> rows = new ArrayList<>(chunks.size());
        for (EmbeddedChunk chunk : chunks) {
            String content = chunk.content();
            rows.add(KnowledgeChunkDO.builder()
                    .id(chunk.chunkId())
                    .kbId(doc.kbId())
                    .docId(doc.docId())
                    .chunkIndex(chunk.index())
                    .content(content)
                    .contentHash(SecureUtil.sha256(content))
                    .charCount(content.length())
                    .tokenCount(StringUtils.hasText(content) ? tokenCounterService.countTokens(content) : 0)
                    .embeddingText(chunk.embeddingText())
                    .enabled(1)
                    .createdBy(username)
                    .updatedBy(username)
                    .build());
        }
        chunkMapper.insert(rows);
        log.debug("关系库块写入完成 docId={} 块数={}", doc.docId(), rows.size());
    }

    @Override
    public void deleteDocument(VectorTarget target, DocumentRef doc) {
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocId, doc.docId()));
    }
}
