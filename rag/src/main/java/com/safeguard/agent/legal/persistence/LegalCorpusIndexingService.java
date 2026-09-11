package com.safeguard.agent.legal.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.core.chunk.model.Chunk;
import com.safeguard.agent.core.chunk.model.ChunkMetadata;
import com.safeguard.agent.core.chunk.model.EmbeddedChunk;
import com.safeguard.agent.core.ingest.VectorTarget;
import com.safeguard.agent.core.ingest.embed.ChunkEmbeddingService;
import com.safeguard.agent.rag.core.keyword.KeywordIndexService;
import com.safeguard.agent.rag.core.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Indexes only eligible persisted legal chunks through the existing embedding/vector/keyword SPIs. */
@Service
@RequiredArgsConstructor
public class LegalCorpusIndexingService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChunkEmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final org.springframework.beans.factory.ObjectProvider<KeywordIndexService> keywordIndexProvider;

    public int eligibleChunkCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM t_knowledge_chunk WHERE kb_id = ? AND index_eligible = TRUE AND deleted = 0",
                Integer.class, LegalCorpusPersistenceService.KB_ID);
    }

    public int indexAll() {
        return indexDocuments(Set.of());
    }

    public int indexEligiblePassPdfDocuments() {
        Set<String> ids = new java.util.HashSet<>(jdbcTemplate.queryForList("""
                SELECT id FROM t_knowledge_document
                WHERE kb_id = ? AND file_type = 'pdf' AND quality_status = 'PASS' AND deleted = 0
                """, String.class, LegalCorpusPersistenceService.KB_ID));
        return indexDocuments(ids);
    }

    /** Indexes only eligible chunks belonging to the supplied documents; empty means the whole legal KB. */
    public int indexDocuments(Set<String> documentIds) {
        String scope = documentIds == null || documentIds.isEmpty() ? "" : " AND doc_id IN (" +
                String.join(",", java.util.Collections.nCopies(documentIds.size(), "?")) + ")";
        List<Object> args = new java.util.ArrayList<>(); args.add(LegalCorpusPersistenceService.KB_ID);
        if (documentIds != null) args.addAll(documentIds);
        List<Row> rows = jdbcTemplate.query("""
                SELECT id, doc_id, chunk_index, content, embedding_text, metadata
                FROM t_knowledge_chunk
                WHERE kb_id = ? AND index_eligible = TRUE AND deleted = 0
                """ + scope + " ORDER BY doc_id, chunk_index", (rs, n) -> new Row(rs.getString("id"), rs.getString("doc_id"), rs.getInt("chunk_index"),
                rs.getString("content"), rs.getString("embedding_text"), rs.getString("metadata")), args.toArray());
        Map<String, List<Row>> byDoc = rows.stream().collect(Collectors.groupingBy(Row::docId, LinkedHashMap::new, Collectors.toList()));
        VectorTarget target = new VectorTarget(LegalCorpusPersistenceService.COLLECTION, "bge-m3", 1536);
        for (var entry : byDoc.entrySet()) {
            List<Chunk> chunks = entry.getValue().stream().map(this::toChunk).toList();
            List<EmbeddedChunk> embedded = embeddingService.embed(chunks, target);
            vectorStoreService.deleteDocumentVectors(target.partition(), entry.getKey());
            vectorStoreService.indexDocumentChunks(target.partition(), entry.getKey(), embedded);
            KeywordIndexService keyword = keywordIndexProvider.getIfAvailable();
            if (keyword != null) {
                keyword.deleteDocumentIndex(target.partition(), entry.getKey());
                keyword.indexDocumentChunks(target.partition(), entry.getKey(), embedded);
            }
        }
        return rows.size();
    }

    /** Indexes one uploaded Legal document into its owning knowledge base's vector target. */
    public int indexUploadedDocument(String kbId, String documentId, VectorTarget target) {
        vectorStoreService.deleteDocumentVectors(target.partition(), documentId);
        List<Row> rows = jdbcTemplate.query("""
                SELECT id, doc_id, chunk_index, content, embedding_text, metadata
                FROM t_knowledge_chunk
                WHERE kb_id = ? AND doc_id = ? AND index_eligible = TRUE AND enabled = 1 AND deleted = 0
                ORDER BY chunk_index
                """, (rs, n) -> new Row(rs.getString("id"), rs.getString("doc_id"), rs.getInt("chunk_index"),
                rs.getString("content"), rs.getString("embedding_text"), rs.getString("metadata")), kbId, documentId);
        if (rows.isEmpty()) {
            return 0;
        }
        List<EmbeddedChunk> embedded = embeddingService.embed(rows.stream().map(this::toChunk).toList(), target);
        vectorStoreService.indexDocumentChunks(target.partition(), documentId, embedded);
        KeywordIndexService keyword = keywordIndexProvider.getIfAvailable();
        if (keyword != null) {
            keyword.deleteDocumentIndex(target.partition(), documentId);
            keyword.indexDocumentChunks(target.partition(), documentId, embedded);
        }
        return rows.size();
    }

    private Chunk toChunk(Row row) {
        Map<String, Object> extras = Map.of();
        try {
            if (row.metadata() != null && !row.metadata().isBlank()) {
                extras = objectMapper.readValue(row.metadata(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            throw new IllegalStateException("Legal chunk metadata JSON 解析失败: " + row.id(), e);
        }
        return new Chunk(row.id(), row.chunkIndex(), row.content(),
                row.embeddingText() == null || row.embeddingText().isBlank() ? row.content() : row.embeddingText(),
                ChunkMetadata.empty().withExtras(extras));
    }

    private record Row(String id, String docId, int chunkIndex, String content, String embeddingText, String metadata) {}
}
