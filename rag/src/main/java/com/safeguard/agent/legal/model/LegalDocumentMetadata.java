package com.safeguard.agent.legal.model;

import com.safeguard.agent.legal.enums.LegalSourceFormat;

import java.time.LocalDate;

public record LegalDocumentMetadata(
        String documentId,
        String docTitle,
        String docType,
        String standardNo,
        String issuingAuthority,
        LocalDate publishDate,
        LocalDate effectiveDate,
        String sourceFile,
        LegalSourceFormat sourceFormat,
        String fileHash,
        String parserVersion
) {
    public LegalDocumentMetadata {
        requireText(documentId, "documentId");
        requireText(sourceFile, "sourceFile");
        if (sourceFormat == null) {
            throw new IllegalArgumentException("sourceFormat 不能为空");
        }
        requireText(fileHash, "fileHash");
        requireText(parserVersion, "parserVersion");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
