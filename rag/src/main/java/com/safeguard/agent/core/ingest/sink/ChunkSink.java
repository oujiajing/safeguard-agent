package com.safeguard.agent.core.ingest.sink;

import com.safeguard.agent.core.chunk.model.EmbeddedChunk;
import com.safeguard.agent.core.ingest.DocumentRef;
import com.safeguard.agent.core.ingest.VectorTarget;

import java.util.List;

/**
 * 索引落点端口：内核只认这个接口，实现住在各自模块里
 * <p>
 * 内核注入 {@code List<ChunkSink>} 扇出，实现间先后由 Spring 的 {@code @Order} 决定，全部包在内核落库步骤的同一个事务里；
 * 只暴露"整体替换"而非删 + 写两个方法，先删后建的顺序由实现自己保证（向量装饰器链靠它构成 upsert 语义）
 */
public interface ChunkSink {

    /**
     * 用给定的块整体替换该文档已有的块，空列表表示该文档不产生任何块
     */
    void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks);

    /**
     * 清除该文档的全部块
     */
    void deleteDocument(VectorTarget target, DocumentRef doc);
}
