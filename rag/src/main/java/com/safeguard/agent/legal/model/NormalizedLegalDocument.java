package com.safeguard.agent.legal.model;

import java.util.List;

public record NormalizedLegalDocument(
        LegalDocumentMetadata metadata,
        List<LegalDocumentElement> elements,
        List<LegalClause> clauses,
        List<LegalDocumentElement> unstructuredParagraphs,
        List<String> warnings
) {
    public NormalizedLegalDocument {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata 不能为空");
        }
        elements = elements == null ? List.of() : List.copyOf(elements);
        clauses = clauses == null ? List.of() : List.copyOf(clauses);
        unstructuredParagraphs = unstructuredParagraphs == null ? List.of() : List.copyOf(unstructuredParagraphs);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
