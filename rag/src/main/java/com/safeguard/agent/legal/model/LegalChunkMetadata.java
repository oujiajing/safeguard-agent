package com.safeguard.agent.legal.model;

import com.safeguard.agent.legal.enums.LegalChunkType;
import com.safeguard.agent.legal.enums.LegalContentRole;

import java.util.LinkedHashMap;
import java.util.Map;

public record LegalChunkMetadata(
        String documentId,
        String docTitle,
        String standardNo,
        String chapterNo,
        String chapterTitle,
        String sectionNo,
        String sectionTitle,
        String clauseNo,
        String hierarchyPath,
        String parentClauseId,
        String childRange,
        LegalContentRole contentRole,
        LegalChunkType chunkType,
        Integer pageStart,
        Integer pageEnd
) {
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "document_id", documentId);
        put(result, "doc_title", docTitle);
        put(result, "standard_no", standardNo);
        put(result, "chapter_no", chapterNo);
        put(result, "chapter_title", chapterTitle);
        put(result, "section_no", sectionNo);
        put(result, "section_title", sectionTitle);
        put(result, "clause_no", clauseNo);
        put(result, "hierarchy_path", hierarchyPath);
        put(result, "parent_clause_id", parentClauseId);
        put(result, "child_range", childRange);
        if (contentRole != null) result.put("content_role", contentRole.name());
        if (chunkType != null) result.put("chunk_type", chunkType.name());
        if (pageStart != null) result.put("page_start", pageStart);
        if (pageEnd != null) result.put("page_end", pageEnd);
        return Map.copyOf(result);
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
