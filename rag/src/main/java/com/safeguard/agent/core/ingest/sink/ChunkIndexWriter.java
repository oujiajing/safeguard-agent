package com.safeguard.agent.core.ingest.sink;

import com.safeguard.agent.core.chunk.model.EmbeddedChunk;
import com.safeguard.agent.core.ingest.DocumentRef;
import com.safeguard.agent.core.ingest.VectorTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;

/**
 * 索引扇出：把块整体写进全部落点，事务边界在此
 * <p>
 * 加一个索引后端 = 加一个 {@link ChunkSink} bean，本类与内核都一行不改
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkIndexWriter {

    private final List<ChunkSink> sinks;
    private final TransactionOperations transactionOperations;

    /**
     * 整体替换该文档的块：全部落点在同一个事务里
     */
    public void replaceDocument(VectorTarget target, DocumentRef doc, List<EmbeddedChunk> chunks) {
        transactionOperations.executeWithoutResult(status ->
                sinks.forEach(sink -> sink.replaceDocument(target, doc, chunks)));
        log.info("块索引写入完成 docId={} 分区={} 块数={} 落点数={}",
                doc.docId(), target.partition(), chunks.size(), sinks.size());
    }

    public void deleteDocument(VectorTarget target, DocumentRef doc) {
        transactionOperations.executeWithoutResult(status ->
                sinks.forEach(sink -> sink.deleteDocument(target, doc)));
    }
}
