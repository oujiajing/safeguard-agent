package com.safeguard.agent.legal.model;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalStructureType;

import java.util.List;

public record LegalClause(
        String clauseId,
        String documentId,
        LegalContentRole contentRole,
        LegalStructureType structureType,
        String chapterNo,
        String chapterTitle,
        String sectionNo,
        String sectionTitle,
        String clauseNo,
        String hierarchyPath,
        String rawText,
        String normalizedText,
        List<LegalSubUnit> children,
        String firstElementId,
        String lastElementId,
        Integer pageStart,
        Integer pageEnd,
        int sourceStartOffset,
        int sourceEndOffset
) {
    public LegalClause {
        if (clauseId == null || clauseId.isBlank()) {
            throw new IllegalArgumentException("clauseId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (contentRole == null || structureType == null) {
            throw new IllegalArgumentException("contentRole/structureType 不能为空");
        }
        if (clauseNo == null || clauseNo.isBlank()) {
            throw new IllegalArgumentException("clauseNo 不能为空");
        }
        if (sourceStartOffset < 0 || sourceEndOffset < sourceStartOffset) {
            throw new IllegalArgumentException("clause source provenance 非法");
        }
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        children = children == null ? List.of() : List.copyOf(children);
    }
}
