package com.safeguard.agent.legal.diagnostics;

import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalQualityReport;

import java.util.List;

public record LegalDocumentDiagnostics(
        String document,
        CleanedTextImportResult result,
        List<LegalDuplicateGroup> duplicateGroups,
        List<LegalUnstructuredItem> unstructuredItems,
        List<String> metadataWarnings,
        double sourceTextCoverageRatio,
        int sourceTextLength,
        int accountedTextLength,
        int unaccountedTextLength,
        List<String> unaccountedSourceFragments,
        double structuredTextRatio,
        boolean deterministic
) {
    public LegalDocumentDiagnostics {
        duplicateGroups = duplicateGroups == null ? List.of() : List.copyOf(duplicateGroups);
        unstructuredItems = unstructuredItems == null ? List.of() : List.copyOf(unstructuredItems);
        metadataWarnings = metadataWarnings == null ? List.of() : List.copyOf(metadataWarnings);
        unaccountedSourceFragments = unaccountedSourceFragments == null ? List.of() : List.copyOf(unaccountedSourceFragments);
    }

    public LegalQualityReport quality() {
        return result.qualityReport();
    }
}
