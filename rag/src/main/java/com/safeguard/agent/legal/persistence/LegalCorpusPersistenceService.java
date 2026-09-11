package com.safeguard.agent.legal.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.CleanedTextImporter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalChunk;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalDocumentElement;
import com.safeguard.agent.legal.model.LegalDocumentMetadata;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Formal, deterministic persistence for the legal corpus. Indexing is intentionally a later phase. */
@Service
@RequiredArgsConstructor
public class LegalCorpusPersistenceService {

    public static final String KB_ID = "legal-corpus-2b";
    public static final String COLLECTION = "legal_corpus_2b";
    private static final String ACTOR = "phase2b";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CleanedTextImporter importer;

    @Transactional
    public LegalPersistenceResult importText(String sourceFile, byte[] rawBytes) {
        CleanedTextImportResult result = importer.importText(
                documentIdFor(rawBytes), sourceFile, rawBytes, CleanedTextImportMode.FORMAL);
        LegalDocumentMetadata metadata = result.document().metadata();
        String existing = findImported(metadata.fileHash(), metadata.parserVersion());
        if (existing != null) {
            return new LegalPersistenceResult(existing, "ALREADY_IMPORTED", 0, 0, 0);
        }
        ensureKnowledgeBase();
        persistDocument(metadata, result, "txt", "text/plain");
        return new LegalPersistenceResult(metadata.documentId(), "IMPORTED",
                result.document().elements().size(), result.document().clauses().size(), result.chunks().size());
    }

    @Transactional
    public LegalPersistenceResult importPdf(String sourceFile, byte[] rawBytes, CleanedTextImportResult result) {
        return importPdf(sourceFile, rawBytes, result, true);
    }

    @Transactional
    public LegalPersistenceResult importPdf(String sourceFile, byte[] rawBytes, CleanedTextImportResult result,
                                            boolean indexEligible) {
        if (result == null || result.document() == null) throw new IllegalArgumentException("PDF 法规解析结果不能为空");
        LegalDocumentMetadata metadata = result.document().metadata();
        String existing = findImported(metadata.fileHash(), metadata.parserVersion());
        if (existing != null) return new LegalPersistenceResult(existing, "ALREADY_IMPORTED", 0, 0, 0);
        ensureKnowledgeBase();
        persistDocument(metadata, result, "pdf", "application/pdf", indexEligible);
        return new LegalPersistenceResult(metadata.documentId(), "IMPORTED",
                result.document().elements().size(), result.document().clauses().size(), result.chunks().size());
    }

    /**
     * Persists Legal pipeline output on an existing upload Document. The batch corpus entry points above
     * intentionally keep their fixed Phase 2B identity; product uploads must provide their own KB and ID.
     */
    @Transactional
    public void replaceUploadedDocument(KnowledgeDocumentDO document, CleanedTextImportResult result,
                                        String actor) {
        if (document == null || document.getId() == null || document.getKbId() == null) {
            throw new IllegalArgumentException("上传法规文档身份不能为空");
        }
        if (result == null || result.document() == null) {
            throw new IllegalArgumentException("PDF 法规解析结果不能为空");
        }
        LegalDocumentMetadata metadata = result.document().metadata();
        if (!document.getId().equals(metadata.documentId())) {
            throw new IllegalArgumentException("法规解析结果与上传文档不匹配");
        }
        clearDocumentArtifacts(document.getId());
        boolean pass = result.qualityReport().qualityStatus()
                == com.safeguard.agent.legal.enums.LegalQualityStatus.PASS;
        updateUploadedDocument(document, metadata, result, actor);
        persistElements(document.getId(), result.document().elements());
        List<LegalClause> clauses = markDuplicates(result.document().clauses());
        persistClauses(clauses);
        persistQuality(result);
        persistChunks(document.getKbId(), document.getId(), clauses, result.chunks(), actor, pass);
        if (!pass) {
            jdbcTemplate.update("UPDATE t_legal_clause SET index_eligible = FALSE WHERE document_id = ?", document.getId());
        }
    }

    @Transactional
    public void clearUploadedDocumentArtifacts(String documentId) {
        clearDocumentArtifacts(documentId);
    }

    @Transactional
    public LegalPersistenceResult replaceText(String sourceFile, byte[] rawBytes) {
        String documentId = documentIdFor(rawBytes);
        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE collection_name = ? AND metadata->>'doc_id' = ?", COLLECTION, documentId);
        jdbcTemplate.update("DELETE FROM t_knowledge_chunk WHERE doc_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_legal_quality_report WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_legal_clause WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_legal_document_element WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_knowledge_document WHERE id = ?", documentId);
        return importText(sourceFile, rawBytes);
    }

    public String findImported(String fileHash, String parserVersion) {
        List<String> ids = jdbcTemplate.query(
                "SELECT id FROM t_knowledge_document WHERE kb_id = ? AND file_hash = ? AND parser_version = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString(1), KB_ID, fileHash, parserVersion);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void ensureKnowledgeBase() {
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base (id, name, embedding_model, collection_name, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, KB_ID, "施工安全法规 Phase 2B", "bge-m3", COLLECTION, ACTOR, ACTOR);
    }

    private void persistDocument(LegalDocumentMetadata metadata, CleanedTextImportResult result,
                                 String fileType, String mimeType) {
        persistDocument(metadata, result, fileType, mimeType, true);
    }

    private void persistDocument(LegalDocumentMetadata metadata, CleanedTextImportResult result,
                                 String fileType, String mimeType, boolean indexEligible) {
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document
                (id, kb_id, doc_name, enabled, chunk_count, file_url, file_type, mime_type, file_size,
                 process_mode, status, source_type, source_location, doc_title, doc_type, standard_no,
                 issuing_authority, publish_date, effective_date, source_format, file_hash, parser_version,
                 ingestion_stage, ingestion_run_id, quality_status, created_by, updated_by)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, 'chunk', 'success', 'legal', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PERSISTED', ?, ?, ?, ?)
                """, metadata.documentId(), KB_ID, metadata.sourceFile(), result.chunks().size(),
                metadata.sourceFile(), fileType, mimeType,
                result.canonicalSourceText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                metadata.sourceFile(), metadata.docTitle(), metadata.docType(), metadata.standardNo(),
                metadata.issuingAuthority(), metadata.publishDate(), metadata.effectiveDate(), metadata.sourceFormat().name(),
                metadata.fileHash(), metadata.parserVersion(), metadata.fileHash().substring(0, 32),
                result.qualityReport().qualityStatus().name(), ACTOR, ACTOR);

        persistElements(metadata.documentId(), result.document().elements());
        List<LegalClause> clauses = markDuplicates(result.document().clauses());
        persistClauses(clauses);
        persistQuality(result);
        persistChunks(KB_ID, metadata.documentId(), clauses, result.chunks(), ACTOR, indexEligible);
        if (!indexEligible) {
            jdbcTemplate.update("UPDATE t_legal_clause SET index_eligible = FALSE WHERE document_id = ?", metadata.documentId());
            jdbcTemplate.update("UPDATE t_knowledge_chunk SET index_eligible = FALSE WHERE doc_id = ?", metadata.documentId());
        }
    }

    @Transactional
    public void deletePdfDocumentsByHashes(List<String> fileHashes) {
        if (fileHashes == null || fileHashes.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(fileHashes.size(), "?"));
        List<String> ids = jdbcTemplate.query("SELECT id FROM t_knowledge_document WHERE kb_id = ? AND file_hash IN (" + placeholders + ") AND file_type = 'pdf' AND deleted = 0",
                (rs, n) -> rs.getString(1), concat(KB_ID, fileHashes));
        for (String id : ids) {
            jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE collection_name = ? AND metadata->>'doc_id' = ?", COLLECTION, id);
            jdbcTemplate.update("DELETE FROM t_knowledge_chunk WHERE doc_id = ?", id);
            jdbcTemplate.update("DELETE FROM t_legal_quality_report WHERE document_id = ?", id);
            jdbcTemplate.update("DELETE FROM t_legal_clause WHERE document_id = ?", id);
            jdbcTemplate.update("DELETE FROM t_legal_document_element WHERE document_id = ?", id);
            jdbcTemplate.update("DELETE FROM t_knowledge_document WHERE id = ?", id);
        }
    }

    private Object[] concat(String first, List<String> rest) {
        Object[] args = new Object[rest.size() + 1]; args[0] = first;
        for (int i = 0; i < rest.size(); i++) args[i + 1] = rest.get(i);
        return args;
    }

    private void persistElements(String documentId, List<LegalDocumentElement> elements) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_legal_document_element
                (id, document_id, element_index, raw_text, normalized_text, structure_type, content_role,
                 canonical_number, page_start, page_end)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, elements, elements.size(), (ps, e) -> {
            ps.setString(1, e.elementId()); ps.setString(2, documentId); ps.setInt(3, e.elementIndex());
            ps.setString(4, e.rawText()); ps.setString(5, e.normalizedText()); ps.setString(6, e.structureType().name());
            ps.setString(7, e.contentRole().name()); ps.setString(8, e.canonicalNumber());
            ps.setObject(9, e.pageStart()); ps.setObject(10, e.pageEnd());
        });
    }

    private List<LegalClause> markDuplicates(List<LegalClause> clauses) {
        Map<String, LegalClause> canonical = new LinkedHashMap<>();
        List<LegalClause> result = new ArrayList<>(clauses.size());
        for (LegalClause clause : clauses) {
            String key = clause.contentRole().name() + "\u0000" + clause.clauseNo() + "\u0000" + clause.normalizedText();
            LegalClause first = canonical.putIfAbsent(key, clause);
            result.add(first == null ? clause : clause);
        }
        return result;
    }

    private void persistClauses(List<LegalClause> clauses) {
        Map<String, LegalClause> canonical = new LinkedHashMap<>();
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_legal_clause
                (id, document_id, content_role, structure_type, chapter_no, chapter_title, section_no, section_title,
                 clause_no, hierarchy_path, raw_text, normalized_text, children_json, first_element_id, last_element_id,
                 page_start, page_end, provenance, index_eligible, duplicate_of_clause_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, clauses, clauses.size(), (ps, c) -> {
            String key = c.contentRole().name() + "\u0000" + c.clauseNo() + "\u0000" + c.normalizedText();
            LegalClause first = canonical.putIfAbsent(key, c);
            ps.setString(1, c.clauseId()); ps.setString(2, c.documentId()); ps.setString(3, c.contentRole().name());
            ps.setString(4, c.structureType().name()); ps.setString(5, c.chapterNo()); ps.setString(6, c.chapterTitle());
            ps.setString(7, c.sectionNo()); ps.setString(8, c.sectionTitle()); ps.setString(9, c.clauseNo());
            ps.setString(10, c.hierarchyPath()); ps.setString(11, c.rawText()); ps.setString(12, c.normalizedText());
            ps.setString(13, json(c.children())); ps.setString(14, c.firstElementId());
            ps.setString(15, c.lastElementId()); ps.setObject(16, c.pageStart()); ps.setObject(17, c.pageEnd());
            ps.setString(18, json(Map.of("start", c.sourceStartOffset(), "end", c.sourceEndOffset())));
            ps.setBoolean(19, first == null); ps.setString(20, first == null ? null : first.clauseId());
        });
    }

    private void persistQuality(CleanedTextImportResult result) {
        var q = result.qualityReport();
        jdbcTemplate.update("""
                INSERT INTO t_legal_quality_report
                (id, document_id, page_count, table_count, parsed_text_length, chapter_count, section_count,
                 clause_count, normative_clause_count, commentary_clause_count, supplementary_count, appendix_count,
                 unknown_role_count, unstructured_paragraph_count, duplicate_clause_count, chunk_count,
                 oversized_chunk_count, empty_chunk_count, quality_status, warnings)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, com.safeguard.agent.legal.util.LegalHashes.shortHash(q.documentId() + ":quality"), q.documentId(),
                q.pageCount(), q.tableCount(), q.parsedTextLength(), q.chapterCount(), q.sectionCount(), q.clauseCount(),
                q.normativeClauseCount(), q.commentaryClauseCount(), q.supplementaryCount(), q.appendixCount(), q.unknownRoleCount(),
                q.unstructuredParagraphCount(), q.duplicateClauseCount(), q.chunkCount(), q.oversizedChunkCount(), q.emptyChunkCount(),
                q.qualityStatus().name(), objectMapper.valueToTree(q.warnings()).toString());
    }

    private void persistChunks(String kbId, String documentId, List<LegalClause> clauses, List<LegalChunk> chunks,
                               String actor, boolean qualityPass) {
        Map<String, LegalClause> byId = clauses.stream().collect(java.util.stream.Collectors.toMap(LegalClause::clauseId, c -> c));
        Map<String, String> duplicateByClauseId = new LinkedHashMap<>();
        Map<String, String> canonicalByKey = new LinkedHashMap<>();
        for (LegalClause clause : clauses) {
            String key = clause.contentRole().name() + "\u0000" + clause.clauseNo() + "\u0000" + clause.normalizedText();
            String canonical = canonicalByKey.putIfAbsent(key, clause.clauseId());
            if (canonical != null) duplicateByClauseId.put(clause.clauseId(), canonical);
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_knowledge_chunk
                (id, kb_id, doc_id, chunk_index, content, content_hash, char_count, token_count, embedding_text,
                 parent_clause_id, chunk_type, chapter_no, chapter_title, section_no, section_title, clause_no,
                 hierarchy_path, child_range, content_role, page_start, page_end, metadata, index_eligible, duplicate_of_clause_id,
                 enabled, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 1, ?, ?)
                """, chunks, chunks.size(), (ps, c) -> {
            var m = c.metadata(); LegalClause clause = byId.get(m.parentClauseId());
            boolean eligible = qualityPass && (clause == null || !duplicateByClauseId.containsKey(clause.clauseId()));
            String duplicateOf = clause == null ? null : duplicateByClauseId.get(clause.clauseId());
            ps.setString(1, c.chunkId()); ps.setString(2, kbId); ps.setString(3, documentId); ps.setInt(4, c.chunkIndex());
            ps.setString(5, c.content()); ps.setString(6, com.safeguard.agent.legal.util.LegalHashes.sha256(c.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            ps.setInt(7, c.content().length()); ps.setInt(8, c.tokenCount()); ps.setString(9, c.content()); ps.setString(10, m.parentClauseId());
            ps.setString(11, m.chunkType().name()); ps.setString(12, m.chapterNo()); ps.setString(13, m.chapterTitle()); ps.setString(14, m.sectionNo());
            ps.setString(15, m.sectionTitle()); ps.setString(16, m.clauseNo()); ps.setString(17, m.hierarchyPath()); ps.setString(18, m.childRange());
            ps.setString(19, m.contentRole().name()); ps.setObject(20, m.pageStart()); ps.setObject(21, m.pageEnd());
            ps.setString(22, json(m.toMap())); ps.setBoolean(23, eligible); ps.setString(24, duplicateOf);
            ps.setString(25, actor); ps.setString(26, actor);
        });
    }

    private void updateUploadedDocument(KnowledgeDocumentDO document, LegalDocumentMetadata metadata,
                                        CleanedTextImportResult result, String actor) {
        jdbcTemplate.update("""
                UPDATE t_knowledge_document
                SET chunk_count = ?, doc_title = ?, doc_type = ?, standard_no = ?, issuing_authority = ?,
                    publish_date = ?, effective_date = ?, source_format = ?, file_hash = ?, parser_version = ?,
                    ingestion_stage = 'PERSISTED', ingestion_run_id = ?, quality_status = ?, updated_by = ?
                WHERE id = ? AND kb_id = ? AND deleted = 0
                """, result.chunks().size(), metadata.docTitle(), metadata.docType(), metadata.standardNo(),
                metadata.issuingAuthority(), metadata.publishDate(), metadata.effectiveDate(), metadata.sourceFormat().name(),
                metadata.fileHash(), metadata.parserVersion(), metadata.fileHash().substring(0, 32),
                result.qualityReport().qualityStatus().name(), actor, document.getId(), document.getKbId());
    }

    private void clearDocumentArtifacts(String documentId) {
        jdbcTemplate.update("DELETE FROM t_knowledge_chunk WHERE doc_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_legal_quality_report WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_legal_clause WHERE document_id = ?", documentId);
        jdbcTemplate.update("DELETE FROM t_legal_document_element WHERE document_id = ?", documentId);
    }

    private String documentIdFor(byte[] bytes) { return "leg" + com.safeguard.agent.legal.util.LegalHashes.sha256(bytes).substring(0, 17); }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Legal metadata JSON 序列化失败", e); }
    }
}
