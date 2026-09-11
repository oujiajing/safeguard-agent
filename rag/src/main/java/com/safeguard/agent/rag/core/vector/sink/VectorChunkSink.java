package com.safeguard.agent.rag.core.vector.sink;

import com.safeguard.agent.core.chunk.model.EmbeddedChunk;
import com.safeguard.agent.core.ingest.DocumentRef;
import com.safeguard.agent.core.ingest.VectorTarget;
import com.safeguard.agent.core.ingest.sink.ChunkSink;
import com.safeguard.agent.rag.core.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量落点：委托既有的向量写入服务
 * <p>
 * 注入的 {@link VectorStoreService} 是一条装饰器链（图谱同步 → 关键词同步 → PG / Milvus），未启用的
 * 后端不注册装饰器；要把关键词或图谱从向量写入的副作用提升为一等落点，各加一个 {@link ChunkSink}
 * bean、删掉对应装饰器即可，内核与写入器都不用改
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class VectorChunkSink implements ChunkSink {

    private final VectorStoreService vectorStoreService;

    @Override
    public void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks) {
        // 先删后建：装饰器链的图谱同步正是依赖这个顺序构成 upsert 语义，
        // 顺序留在实现内部，不暴露给调用方
        vectorStoreService.deleteDocumentVectors(target.partition(), doc.docId());
        if (!chunks.isEmpty()) {
            vectorStoreService.indexDocumentChunks(target.partition(), doc.docId(), chunks);
        }
    }

    @Override
    public void deleteDocument(VectorTarget target, DocumentRef doc) {
        vectorStoreService.deleteDocumentVectors(target.partition(), doc.docId());
    }
}
