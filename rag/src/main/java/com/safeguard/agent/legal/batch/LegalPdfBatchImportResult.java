package com.safeguard.agent.legal.batch;

import java.util.List;

public record LegalPdfBatchImportResult(
        List<LegalPdfImportTask> tasks,
        int totalFiles,
        int successCount,
        int failedCount,
        int retryCount,
        int clauseCount,
        int chunkCount
) {
    public LegalPdfBatchImportResult {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
