package com.safeguard.agent.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.TestSafeGuardApplication;
import com.safeguard.agent.legal.batch.LegalPdfBatchImportJob;
import com.safeguard.agent.legal.batch.LegalPdfBatchImportResult;
import com.safeguard.agent.legal.persistence.LegalCorpusPersistenceService;
import com.safeguard.agent.legal.persistence.LegalCorpusIndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit opt-in production replay for validated MinerU result.zip caches. */
@SpringBootTest(classes = TestSafeGuardApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "rag.image-parse.embedded-describe-enabled=false")
@EnabledIfSystemProperty(named = "legal.pdf.cached.persist", matches = "true")
class LegalPdfCachedProductionReimportTest {
    @Autowired LegalPdfBatchImportJob batchImportJob;
    @Autowired LegalCorpusPersistenceService persistenceService;
    @Autowired LegalCorpusIndexingService indexingService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void replacesTheApprovedCachedPdfCorpus() throws Exception {
        if (Boolean.parseBoolean(System.getProperty("legal.pdf.cached.index-only", "false"))) {
            int indexed = indexingService.indexEligiblePassPdfDocuments();
            System.out.println("PDF_INDEX_ONLY chunks=" + indexed);
            return;
        }
        Path manifest = Path.of(System.getProperty("legal.pdf.approved-manifest"));
        JsonNode root = objectMapper.readTree(Files.readAllBytes(manifest));
        Set<String> reviewHashes = new HashSet<>();
        for (JsonNode document : root.path("documents")) {
            if ("REVIEW_REQUIRED".equals(document.path("finalDisposition").asText())) {
                reviewHashes.add(document.path("fileHash").asText());
            }
        }
        Set<String> hashes = new HashSet<>();
        for (JsonNode document : root.path("documents")) hashes.add(document.path("fileHash").asText());
        persistenceService.deletePdfDocumentsByHashes(hashes.stream().toList());
        String sourceDirectory = System.getProperty("legal.pdf.source-dir");
        LegalPdfBatchImportResult result = batchImportJob.runCached(
                Path.of(System.getProperty("legal.pdf.cache-dir")),
                sourceDirectory == null || sourceDirectory.isBlank() ? null : Path.of(sourceDirectory), true,
                Boolean.parseBoolean(System.getProperty("legal.pdf.cached.index", "true")), reviewHashes);
        result.tasks().forEach(task -> System.out.println("PDF_CACHED_IMPORT " + task.fileName()
                + " status=" + task.status() + " clauses=" + task.clauseCount()
                + " chunks=" + task.chunkCount() + " error=" + task.errorMessage()));
        assertEquals(30, result.totalFiles());
    }
}
