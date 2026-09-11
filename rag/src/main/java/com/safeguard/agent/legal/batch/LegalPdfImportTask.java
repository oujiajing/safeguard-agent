package com.safeguard.agent.legal.batch;

import java.time.Instant;

/** In-memory task snapshot for one batch item; database persistence remains the corpus source of truth. */
public final class LegalPdfImportTask {
    private final String id;
    private final String fileName;
    private final String fileHash;
    private final String sourceType = "PDF";
    private final String parserType = "MINERU";
    private LegalPdfImportTaskStatus status = LegalPdfImportTaskStatus.PENDING;
    private int clauseCount;
    private int chunkCount;
    private String errorMessage;
    private int retryCount;
    private final Instant createdTime = Instant.now();
    private Instant finishedTime;

    public LegalPdfImportTask(String id, String fileName, String fileHash) {
        this.id = id;
        this.fileName = fileName;
        this.fileHash = fileHash;
    }

    public void parsing() { status = LegalPdfImportTaskStatus.PARSING; }

    public void structured(int clauses, int chunks) {
        status = LegalPdfImportTaskStatus.STRUCTURED;
        clauseCount = clauses;
        chunkCount = chunks;
    }

    public void indexed() {
        status = LegalPdfImportTaskStatus.INDEXED;
        finishedTime = Instant.now();
    }

    public void failed(Exception error) {
        status = LegalPdfImportTaskStatus.FAILED;
        errorMessage = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        finishedTime = Instant.now();
    }

    public void retrying() { retryCount++; }

    public String id() { return id; }
    public String fileName() { return fileName; }
    public String fileHash() { return fileHash; }
    public String sourceType() { return sourceType; }
    public String parserType() { return parserType; }
    public LegalPdfImportTaskStatus status() { return status; }
    public int clauseCount() { return clauseCount; }
    public int chunkCount() { return chunkCount; }
    public String errorMessage() { return errorMessage; }
    public int retryCount() { return retryCount; }
    public Instant createdTime() { return createdTime; }
    public Instant finishedTime() { return finishedTime; }
}
