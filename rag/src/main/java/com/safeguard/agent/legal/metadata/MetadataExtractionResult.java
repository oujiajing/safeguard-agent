package com.safeguard.agent.legal.metadata;

import com.safeguard.agent.legal.model.LegalDocumentMetadata;

import java.util.List;

public record MetadataExtractionResult(LegalDocumentMetadata metadata, List<String> warnings) {
    public MetadataExtractionResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
