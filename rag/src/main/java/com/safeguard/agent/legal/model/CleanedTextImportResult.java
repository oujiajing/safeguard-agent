package com.safeguard.agent.legal.model;

import java.util.List;

public record CleanedTextImportResult(
        NormalizedLegalDocument document,
        List<LegalChunk> chunks,
        LegalQualityReport qualityReport,
        String canonicalSourceText
) {
    public CleanedTextImportResult {
        if (document == null || qualityReport == null) throw new IllegalArgumentException("dry-run 结果不能为空");
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        canonicalSourceText = canonicalSourceText == null ? "" : canonicalSourceText;
    }
}
