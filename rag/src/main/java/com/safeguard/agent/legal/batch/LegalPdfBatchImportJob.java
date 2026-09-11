package com.safeguard.agent.legal.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.legal.ingest.LegalPdfImportService;
import com.safeguard.agent.legal.ingest.LegalDocumentImportAdapter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.persistence.LegalCorpusIndexingService;
import com.safeguard.agent.legal.persistence.LegalCorpusPersistenceService;
import com.safeguard.agent.legal.persistence.LegalPersistenceResult;
import com.safeguard.agent.legal.util.LegalHashes;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Batch coordinator with bounded retries; one failed PDF never aborts the remaining files. */
@Service
public class LegalPdfBatchImportJob {
    private static final int MAX_ATTEMPTS = 3;

    private final LegalPdfImportService pdfImportService;
    private final LegalCorpusPersistenceService persistenceService;
    private final LegalCorpusIndexingService indexingService;
    private final ObjectMapper objectMapper;

    public LegalPdfBatchImportJob(LegalPdfImportService pdfImportService,
                                  LegalCorpusPersistenceService persistenceService,
                                  LegalCorpusIndexingService indexingService) {
        this(pdfImportService, persistenceService, indexingService, null);
    }

    @Autowired
    public LegalPdfBatchImportJob(LegalPdfImportService pdfImportService,
                                  LegalCorpusPersistenceService persistenceService,
                                  LegalCorpusIndexingService indexingService,
                                  ObjectMapper objectMapper) {
        this.pdfImportService = pdfImportService;
        this.persistenceService = persistenceService;
        this.indexingService = indexingService;
        this.objectMapper = objectMapper;
    }

    public LegalPdfBatchImportResult run(Path directory, boolean persist, boolean index) throws Exception {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("PDF directory 不存在: " + directory);
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        List<LegalPdfImportTask> tasks = new ArrayList<>();
        for (Path file : files) tasks.add(runOne(file, persist));
        if (persist && index) {
            indexingService.indexAll();
            tasks.stream().filter(t -> t.status() == LegalPdfImportTaskStatus.STRUCTURED).forEach(LegalPdfImportTask::indexed);
        }
        int success = (int) tasks.stream().filter(t -> t.status() == LegalPdfImportTaskStatus.STRUCTURED
                || t.status() == LegalPdfImportTaskStatus.INDEXED).count();
        return new LegalPdfBatchImportResult(tasks, tasks.size(), success, tasks.size() - success,
                tasks.stream().mapToInt(LegalPdfImportTask::retryCount).sum(),
                tasks.stream().mapToInt(LegalPdfImportTask::clauseCount).sum(),
                tasks.stream().mapToInt(LegalPdfImportTask::chunkCount).sum());
    }

    /** Production replay entry: cache directories contain origin.pdf and validated result.zip. */
    public LegalPdfBatchImportResult runCached(Path cacheDirectory, boolean persist, boolean index) throws Exception {
        return runCached(cacheDirectory, persist, index, Set.of());
    }

    public LegalPdfBatchImportResult runCached(Path cacheDirectory, boolean persist, boolean index,
                                               Set<String> reviewHashes) throws Exception {
        return runCached(cacheDirectory, null, persist, index, reviewHashes);
    }

    /** Replays cached MinerU output while hashing and persisting the operator's source PDF directory. */
    public LegalPdfBatchImportResult runCached(Path cacheDirectory, Path sourcePdfDirectory,
                                               boolean persist, boolean index,
                                               Set<String> reviewHashes) throws Exception {
        if (cacheDirectory == null || !Files.isDirectory(cacheDirectory)) {
            throw new IllegalArgumentException("MinerU 缓存目录不存在: " + cacheDirectory);
        }
        if (sourcePdfDirectory != null && !Files.isDirectory(sourcePdfDirectory)) {
            throw new IllegalArgumentException("PDF 原件目录不存在: " + sourcePdfDirectory);
        }
        List<Path> files;
        try (var stream = Files.list(cacheDirectory)) {
            files = stream.filter(Files::isDirectory)
                    .filter(p -> Files.isRegularFile(p.resolve("origin.pdf")) && Files.isRegularFile(p.resolve("result.zip")))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        List<LegalPdfImportTask> tasks = new ArrayList<>();
        for (Path cache : files) tasks.add(runCachedOne(cache, sourcePdfDirectory, persist, reviewHashes));
        if (persist && index) {
            indexingService.indexAll();
            tasks.stream().filter(t -> t.status() == LegalPdfImportTaskStatus.STRUCTURED).forEach(LegalPdfImportTask::indexed);
        }
        int success = (int) tasks.stream().filter(t -> t.status() == LegalPdfImportTaskStatus.STRUCTURED
                || t.status() == LegalPdfImportTaskStatus.INDEXED).count();
        return new LegalPdfBatchImportResult(tasks, tasks.size(), success, tasks.size() - success,
                tasks.stream().mapToInt(LegalPdfImportTask::retryCount).sum(),
                tasks.stream().mapToInt(LegalPdfImportTask::clauseCount).sum(),
                tasks.stream().mapToInt(LegalPdfImportTask::chunkCount).sum());
    }

    private LegalPdfImportTask runCachedOne(Path cache, Path sourcePdfDirectory,
                                            boolean persist, Set<String> reviewHashes) throws Exception {
        String fileName = sourceFileName(cache);
        Path pdf = sourcePdfDirectory == null ? cache.resolve("origin.pdf") : sourcePdfDirectory.resolve(fileName);
        if (!Files.isRegularFile(pdf)) throw new IllegalArgumentException("PDF 原件不存在: " + pdf);
        byte[] bytes = Files.readAllBytes(pdf);
        String hash = LegalHashes.sha256(bytes);
        LegalPdfImportTask task = new LegalPdfImportTask("leg" + hash.substring(0, 17), fileName, hash);
        if (persist && persistenceService.findImported(hash, LegalDocumentImportAdapter.PARSER_VERSION) != null) {
            task.structured(0, 0); return task;
        }
        try {
            task.parsing();
            CleanedTextImportResult result = pdfImportService.dryRunCached(task.id(), fileName, bytes,
                    Files.readAllBytes(cache.resolve("result.zip")));
            boolean eligible = result.qualityReport().qualityStatus() == com.safeguard.agent.legal.enums.LegalQualityStatus.PASS
                    && !reviewHashes.contains(hash);
            task.structured(result.document().clauses().size(), result.chunks().size());
            if (persist) persistenceService.importPdf(fileName, bytes, result, eligible);
            return task;
        } catch (Exception error) {
            task.failed(error);
            return task;
        }
    }

    private String sourceFileName(Path cache) {
        if (objectMapper != null) {
            try {
                var node = objectMapper.readTree(Files.readString(cache.resolve("manifest.json")));
                if (node.hasNonNull("sourceFile")) return node.get("sourceFile").asText();
            } catch (Exception ignored) { }
        }
        return cache.getFileName().toString() + ".pdf";
    }

    private LegalPdfImportTask runOne(Path file, boolean persist) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String fileName = file.getFileName().toString();
        String hash = LegalHashes.sha256(bytes);
        // t_knowledge_document.id is varchar(20); keep the same 20-char identity convention as TXT imports.
        LegalPdfImportTask task = new LegalPdfImportTask("leg" + hash.substring(0, 17), fileName, hash);
        if (persist && persistenceService.findImported(hash, LegalDocumentImportAdapter.PARSER_VERSION) != null) {
            task.structured(0, 0);
            return task;
        }
        Exception failure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                task.parsing();
                CleanedTextImportResult result = pdfImportService.dryRun(task.id(), fileName, bytes);
                task.structured(result.document().clauses().size(), result.chunks().size());
                if (persist) {
                    LegalPersistenceResult persisted = persistenceService.importPdf(fileName, bytes, result);
                    if ("ALREADY_IMPORTED".equals(persisted.status())) {
                        task.structured(persisted.clauseCount(), persisted.chunkCount());
                    }
                }
                return task;
            } catch (Exception error) {
                failure = error;
                if (!isRetryable(error) || attempt == MAX_ATTEMPTS) break;
                task.retrying();
            }
        }
        task.failed(failure == null ? new IllegalStateException("PDF import failed") : failure);
        return task;
    }

    private boolean isRetryable(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return message.contains("timeout") || message.contains("network") || message.contains("connection");
    }
}
