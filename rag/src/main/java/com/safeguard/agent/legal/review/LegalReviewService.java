package com.safeguard.agent.legal.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.framework.exception.ClientException;
import com.safeguard.agent.legal.dao.entity.LegalClauseDO;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.safeguard.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.safeguard.agent.knowledge.controller.vo.KnowledgeChunkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LegalReviewService {

    private static final String DETECTOR_VERSION = "1.0.1";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ClauseSequenceGapDetector clauseDetector = new ClauseSequenceGapDetector();
    private final EnumerationSequenceGapDetector enumerationDetector = new EnumerationSequenceGapDetector();

    public int audit(String documentId) {
        requireDocument(documentId);
        markRun(documentId, "RUNNING", 0, null);
        jdbcTemplate.update("UPDATE t_legal_review_signal SET lifecycle_status='STALE', update_time=CURRENT_TIMESTAMP WHERE document_id=? AND lifecycle_status='ACTIVE' AND review_status='PENDING_REVIEW'", documentId);
        try {
            List<LegalClauseDO> rows = jdbcTemplate.query("SELECT id, document_id, content_role, structure_type, chapter_no, chapter_title, section_no, section_title, clause_no, hierarchy_path, raw_text, normalized_text, children_json, first_element_id, last_element_id, page_start, page_end, create_time FROM t_legal_clause WHERE document_id=? ORDER BY create_time, id",
                    (rs, rowNum) -> LegalClauseDO.builder()
                            .id(rs.getString("id"))
                            .documentId(rs.getString("document_id"))
                            .contentRole(rs.getString("content_role"))
                            .structureType(rs.getString("structure_type"))
                            .chapterNo(rs.getString("chapter_no"))
                            .chapterTitle(rs.getString("chapter_title"))
                            .sectionNo(rs.getString("section_no"))
                            .sectionTitle(rs.getString("section_title"))
                            .clauseNo(rs.getString("clause_no"))
                            .hierarchyPath(rs.getString("hierarchy_path"))
                            .rawText(rs.getString("raw_text"))
                            .normalizedText(rs.getString("normalized_text"))
                            .childrenJson(rs.getString("children_json"))
                            .firstElementId(rs.getString("first_element_id"))
                            .lastElementId(rs.getString("last_element_id"))
                            .pageStart(rs.getObject("page_start", Integer.class))
                            .pageEnd(rs.getObject("page_end", Integer.class))
                            .createTime(rs.getTimestamp("create_time"))
                            .build(), documentId);
            List<LegalClause> clauses = rows.stream().map(this::toModel).toList();
            List<ReviewSignalCandidate> candidates = new ArrayList<>(clauseDetector.detect(clauses));
            candidates.addAll(enumerationDetector.detect(clauses));
            for (ReviewSignalCandidate candidate : candidates) upsert(candidate);
            markRun(documentId, "SUCCESS", candidates.size(), null);
            return candidates.size();
        } catch (RuntimeException error) {
            markRun(documentId, "FAILED", 0, error.getMessage());
            throw error;
        }
    }

    public LegalReviewOverviewVO overview(String documentId) {
        requireDocument(documentId);
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM t_legal_review_signal WHERE document_id=? AND lifecycle_status='ACTIVE'", Long.class, documentId);
        Long reviewed = jdbcTemplate.queryForObject("SELECT count(*) FROM t_legal_review_signal WHERE document_id=? AND lifecycle_status='ACTIVE' AND review_status<>'PENDING_REVIEW'", Long.class, documentId);
        Long pending = jdbcTemplate.queryForObject("SELECT count(*) FROM t_legal_review_signal WHERE document_id=? AND lifecycle_status='ACTIVE' AND review_status='PENDING_REVIEW'", Long.class, documentId);
        Long docs = jdbcTemplate.queryForObject("SELECT count(*) FROM t_legal_review_signal WHERE document_id=? AND lifecycle_status='ACTIVE' AND scope='DOCUMENT'", Long.class, documentId);
        Long chunks = jdbcTemplate.queryForObject("SELECT count(DISTINCT x.value) FROM t_legal_review_signal s CROSS JOIN LATERAL jsonb_array_elements_text(s.related_chunk_ids) x(value) WHERE s.document_id=? AND s.lifecycle_status='ACTIVE'", Long.class, documentId);
        String detectionStatus = jdbcTemplate.query("SELECT status FROM t_legal_review_run WHERE document_id=?", rs -> rs.next() ? rs.getString(1) : "NOT_RUN", documentId);
        return new LegalReviewOverviewVO(detectionStatus, value(total), value(reviewed), value(pending), value(docs), value(chunks));
    }

    public void enrichChunks(String documentId, List<KnowledgeChunkVO> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        for (KnowledgeChunkVO chunk : chunks) {
            Map<String, Object> summary = jdbcTemplate.query("SELECT COALESCE(bool_or(review_status='PENDING_REVIEW'), false), COALESCE(bool_or(review_status='ISSUE_CONFIRMED'), false), count(*) FROM t_legal_review_signal WHERE document_id=? AND lifecycle_status='ACTIVE' AND jsonb_exists(related_chunk_ids, ?)", rs -> {
                if (!rs.next()) return Map.<String, Object>of();
                return Map.of("pending", rs.getBoolean(1), "issue", rs.getBoolean(2), "count", rs.getInt(3));
            }, documentId, chunk.getId());
            boolean pending = Boolean.TRUE.equals(summary.get("pending"));
            boolean issue = Boolean.TRUE.equals(summary.get("issue"));
            int count = ((Number) summary.getOrDefault("count", 0)).intValue();
            chunk.setReviewIssueCount(count);
            if (pending) chunk.setReviewStatus("NEEDS_REVIEW");
            else if (issue) chunk.setReviewStatus("ISSUE_CONFIRMED");
            else if (count > 0) chunk.setReviewStatus("VERIFIED_OK");
            else {
                String status = jdbcTemplate.query("SELECT status FROM t_legal_review_run WHERE document_id=?", rs -> rs.next() ? rs.getString(1) : "NOT_RUN", documentId);
                chunk.setReviewStatus("SUCCESS".equals(status) ? "NOT_FOUND" : "FAILED".equals(status) ? "DETECTION_FAILED" : "RUNNING".equals(status) || "PENDING_RECHECK".equals(status) ? "DETECTION_PENDING" : "NOT_DETECTED");
            }
        }
    }

    public void markChunkChanged(String documentId, String chunkId) {
        jdbcTemplate.update("UPDATE t_legal_review_signal SET lifecycle_status='STALE', update_time=CURRENT_TIMESTAMP WHERE document_id=? AND lifecycle_status='ACTIVE' AND jsonb_exists(related_chunk_ids, ?)", documentId, chunkId);
        jdbcTemplate.update("UPDATE t_legal_review_run SET status='PENDING_RECHECK', updated_at=CURRENT_TIMESTAMP WHERE document_id=?", documentId);
    }

    private void markRun(String documentId, String status, int count, String error) {
        jdbcTemplate.update("INSERT INTO t_legal_review_run(document_id, status, detector_version, signal_count, error_message, updated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (document_id) DO UPDATE SET status=EXCLUDED.status, detector_version=EXCLUDED.detector_version, signal_count=EXCLUDED.signal_count, error_message=EXCLUDED.error_message, updated_at=CURRENT_TIMESTAMP", documentId, status, DETECTOR_VERSION, count, error);
    }

    public List<LegalReviewSignalVO> list(String documentId, String signalType, String reviewStatus) {
        requireDocument(documentId);
        StringBuilder sql = new StringBuilder("SELECT id, document_id, scope, target_id, signal_type, message, related_clause_ids, related_chunk_ids, evidence, review_status, review_reason, version, reviewed_at FROM t_legal_review_signal WHERE document_id=? AND lifecycle_status='ACTIVE'");
        List<Object> args = new ArrayList<>();
        args.add(documentId);
        if (signalType != null && !signalType.isBlank()) {
            sql.append(" AND signal_type=?");
            args.add(signalType);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            sql.append(" AND review_status=?");
            args.add(reviewStatus);
        }
        sql.append(" ORDER BY create_time, id");
        return jdbcTemplate.query(sql.toString(), (rs, row) -> mapRow(rs), args.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(String signalId, LegalReviewStatus status, String reason, int expectedVersion) {
        if (status == null || status == LegalReviewStatus.PENDING_REVIEW) throw new ClientException("复核结论必须是 VERIFIED_OK 或 ISSUE_CONFIRMED");
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new ClientException("复核原因不能为空且不能超过1000字");
        int updated = jdbcTemplate.update("UPDATE t_legal_review_signal SET review_status=?, review_reason=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP, version=version+1, update_time=CURRENT_TIMESTAMP WHERE id=? AND lifecycle_status='ACTIVE' AND version=?",
                status.name(), reason.trim(), UserContext.getUsername(), signalId, expectedVersion);
        if (updated != 1) throw new ClientException("问题不存在、已失效或版本已变化，请刷新后重试");
    }

    private void upsert(ReviewSignalCandidate candidate) {
        String evidence = write(candidate.evidence());
        String clauses = write(candidate.relatedClauseIds());
        List<String> relatedChunkIds = candidate.relatedChunkIds().isEmpty()
                ? jdbcTemplate.query("SELECT id FROM t_knowledge_chunk WHERE doc_id=? AND parent_clause_id IN (" + placeholders(candidate.relatedClauseIds().size()) + ") AND deleted=0 ORDER BY chunk_index", (rs, row) -> rs.getString(1), concat(documentId(candidate), candidate.relatedClauseIds()).toArray())
                : candidate.relatedChunkIds();
        String chunks = write(relatedChunkIds);
        String chunkFingerprint = relatedChunkIds.isEmpty() ? "" : jdbcTemplate.queryForObject("SELECT COALESCE(string_agg(id || ':' || COALESCE(content_hash, ''), ',' ORDER BY chunk_index), '') FROM t_knowledge_chunk WHERE doc_id=? AND id IN (" + placeholders(relatedChunkIds.size()) + ") AND deleted=0", String.class, concat(candidate.documentId(), relatedChunkIds).toArray());
        String fingerprint = sha256(candidate.stableKey() + evidence + chunkFingerprint);
        String effectiveStableKey = candidate.stableKey() + "|" + fingerprint;
        jdbcTemplate.update("INSERT INTO t_legal_review_signal (id, document_id, scope, target_id, signal_type, stable_key, related_clause_ids, related_chunk_ids, message, evidence, detector_version, input_fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?) ON CONFLICT (stable_key) DO UPDATE SET lifecycle_status='ACTIVE', target_id=EXCLUDED.target_id, related_clause_ids=EXCLUDED.related_clause_ids, related_chunk_ids=EXCLUDED.related_chunk_ids, message=EXCLUDED.message, evidence=EXCLUDED.evidence, detector_version=EXCLUDED.detector_version, input_fingerprint=EXCLUDED.input_fingerprint, update_time=CURRENT_TIMESTAMP WHERE t_legal_review_signal.review_status='PENDING_REVIEW'",
                id(effectiveStableKey), candidate.documentId(), candidate.scope().name(), candidate.targetId(), candidate.signalType().name(), effectiveStableKey, clauses, chunks, candidate.message(), evidence, DETECTOR_VERSION, fingerprint);
    }

    private LegalReviewSignalVO mapRow(ResultSet rs) throws java.sql.SQLException {
        List<String> clauseIds = readList(rs.getString(7));
        List<String> clauseNos = clauseIds.stream().map(this::clauseNo).filter(value -> !value.isBlank()).toList();
        return new LegalReviewSignalVO(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), clauseIds, clauseNos, readList(rs.getString(8)), readMap(rs.getString(9)), rs.getString(10), rs.getString(11), rs.getInt(12), rs.getObject(13, LocalDateTime.class));
    }

    public LegalChunkSourceContextVO sourceContext(String documentId, String chunkId) {
        KnowledgeDocumentDO document = documentMapper.selectById(documentId);
        if (document == null) throw new ClientException("文档不存在");
        Map<String, Object> chunk = jdbcTemplate.query("SELECT parent_clause_id FROM t_knowledge_chunk WHERE id=? AND doc_id=? AND deleted=0", rs -> {
            if (!rs.next()) return Map.<String, Object>of();
            return Map.of("parentClauseId", rs.getString(1));
        }, chunkId, documentId);
        String parentClauseId = (String) chunk.get("parentClauseId");
        if (parentClauseId == null || parentClauseId.isBlank()) {
            return new LegalChunkSourceContextVO(false, null, null, null, null, null, null, "该分块没有可靠的原文条款关联");
        }
        return jdbcTemplate.query("SELECT chapter_title, section_title, clause_no, raw_text, page_start, page_end FROM t_legal_clause WHERE id=? AND document_id=?", rs -> {
            if (!rs.next()) return new LegalChunkSourceContextVO(false, null, null, null, null, null, null, "未找到关联的原文条款");
            return new LegalChunkSourceContextVO(true, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5, Integer.class), rs.getObject(6, Integer.class), null);
        }, parentClauseId, documentId);
    }

    private String clauseNo(String clauseId) {
        return jdbcTemplate.query("SELECT clause_no FROM t_legal_clause WHERE id=?", rs -> rs.next() ? rs.getString(1) : "", clauseId);
    }

    private LegalClause toModel(LegalClauseDO row) {
        return new LegalClause(row.getId(), row.getDocumentId(), enumValue(row.getContentRole(), com.safeguard.agent.legal.enums.LegalContentRole.UNKNOWN), enumValue(row.getStructureType(), com.safeguard.agent.legal.enums.LegalStructureType.UNKNOWN), row.getChapterNo(), row.getChapterTitle(), row.getSectionNo(), row.getSectionTitle(), row.getClauseNo(), row.getHierarchyPath(), row.getRawText(), row.getNormalizedText(), readChildren(row.getChildrenJson()), row.getFirstElementId(), row.getLastElementId(), row.getPageStart(), row.getPageEnd(), 0, Math.max(0, row.getNormalizedText() == null ? 0 : row.getNormalizedText().length()));
    }

    private List<com.safeguard.agent.legal.model.LegalSubUnit> readChildren(String value) { try { return value == null ? List.of() : objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ignored) { return List.of(); } }
    private List<String> readList(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ignored) { return List.of(); } }
    private Map<String, Object> readMap(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ignored) { return Map.of(); } }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private void requireDocument(String id) { KnowledgeDocumentDO document = documentMapper.selectById(id); if (document == null) throw new ClientException("文档不存在"); }
    private static long value(Long value) { return value == null ? 0 : value; }
    private static String id(String stableKey) { return sha256(stableKey).substring(0, 32); }
    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(Math.max(1, count), "?")); }
    private static List<Object> concat(String documentId, List<String> clauses) { List<Object> values = new ArrayList<>(); values.add(documentId); values.addAll(clauses); return values; }
    private static String documentId(ReviewSignalCandidate candidate) { return candidate.documentId(); }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static <T extends Enum<T>> T enumValue(String value, T fallback) { try { return value == null ? fallback : Enum.valueOf(fallback.getDeclaringClass(), value); } catch (Exception ignored) { return fallback; } }
}
