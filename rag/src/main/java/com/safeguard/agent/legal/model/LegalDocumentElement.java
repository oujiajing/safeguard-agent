package com.safeguard.agent.legal.model;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalStructureType;

public record LegalDocumentElement(
        String elementId,
        String documentId,
        int elementIndex,
        String rawText,
        String normalizedText,
        LegalStructureType structureType,
        LegalContentRole contentRole,
        String canonicalNumber,
        Integer pageStart,
        Integer pageEnd,
        int sourceLineIndex,
        int sourceStartOffset,
        int sourceEndOffset
) {
    public LegalDocumentElement {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("elementId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (elementIndex < 0) {
            throw new IllegalArgumentException("elementIndex 必须 >= 0");
        }
        if (sourceLineIndex < 0 || sourceStartOffset < 0 || sourceEndOffset < sourceStartOffset) {
            throw new IllegalArgumentException("source provenance 非法");
        }
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        structureType = structureType == null ? LegalStructureType.UNKNOWN : structureType;
        contentRole = contentRole == null ? LegalContentRole.UNKNOWN : contentRole;
    }

    public LegalDocumentElement classified(LegalStructureType type, LegalContentRole role, String number) {
        return new LegalDocumentElement(elementId, documentId, elementIndex, rawText, normalizedText,
                type, role, number, pageStart, pageEnd, sourceLineIndex, sourceStartOffset, sourceEndOffset);
    }
}
