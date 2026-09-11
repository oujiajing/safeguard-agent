package com.safeguard.agent.legal;

import com.safeguard.agent.TestRagentApplication;
import com.safeguard.agent.legal.batch.LegalPdfBatchImportJob;
import com.safeguard.agent.legal.batch.LegalPdfBatchImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit opt-in destructive integration test: persists and indexes the configured PDF corpus. */
@SpringBootTest(classes = TestRagentApplication.class, webEnvironment = WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "legal.pdf.persist", matches = "true")
class LegalPdfBatchImportPersistenceTest {

    @Autowired
    private LegalPdfBatchImportJob batchImportJob;

    @Test
    void importsAndIndexesTheConfiguredPdfCorpus() throws Exception {
        LegalPdfBatchImportResult result = batchImportJob.run(
                Path.of(System.getProperty("legal.pdf.dir")), true,
                Boolean.parseBoolean(System.getProperty("legal.pdf.index", "false")));
        result.tasks().forEach(task -> System.out.println("PDF_IMPORT_TASK " + task.fileName()
                + " status=" + task.status() + " error=" + task.errorMessage()));
        assertEquals(result.totalFiles(), result.successCount());
    }
}
