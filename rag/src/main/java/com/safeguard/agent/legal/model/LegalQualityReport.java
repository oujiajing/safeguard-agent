package com.safeguard.agent.legal.model;

import com.safeguard.agent.legal.enums.LegalQualityStatus;

import java.util.List;

public record LegalQualityReport(
        String documentId,
        Integer pageCount,
        int tableCount,
        int parsedTextLength,
        int chapterCount,
        int sectionCount,
        int clauseCount,
        int normativeClauseCount,
        int commentaryClauseCount,
        int supplementaryCount,
        int appendixCount,
        int unknownRoleCount,
        int unstructuredParagraphCount,
        int duplicateClauseCount,
        int chunkCount,
        int oversizedChunkCount,
        int emptyChunkCount,
        LegalQualityStatus qualityStatus,
        List<String> warnings
) {
    public LegalQualityReport {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
