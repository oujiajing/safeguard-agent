package com.safeguard.agent.legal;

import com.safeguard.agent.TestRagentApplication;
import com.safeguard.agent.legal.ingest.LegalPdfImportService;
import com.safeguard.agent.core.parser.mineru.MinerUDocumentParser;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import com.safeguard.agent.legal.filter.LegalSectionFilter;
import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.LegalDocumentImportAdapter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestRagentApplication.class, webEnvironment = WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "legal.pdf.dir", matches = ".+")
class LegalPdfCorpusDryRunTest {

    @Autowired
    private LegalPdfImportService importService;

    @Autowired
    private MinerUDocumentParser minerUParser;

    @Autowired
    private LegalDocumentImportAdapter adapter;

    @Autowired
    private LegalSectionFilter sectionFilter;

    @Test
    void dryRunsTenPdfDocumentsThroughMineruAndLegalParser() throws Exception {
        Path corpus = Path.of(System.getProperty("legal.pdf.dir")).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(corpus), "PDF corpus 不存在: " + corpus);
        List<Path> pdfs;
        try (var files = Files.list(corpus)) {
            pdfs = files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .filter(path -> selectedFiles().isEmpty()
                            || selectedFiles().contains(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        assertTrue(pdfs.size() > 0 && pdfs.size() <= 30, "PDF 运行样本数应为 1-30");

        StringBuilder report = new StringBuilder("# Phase 5.3 Non-Body Filter Report\n\n")
                .append("Run status: completed; final acceptance is asserted after all selected PDFs finish.\n\n")
                .append("## Per-document before/after\n\n")
                .append("| file | before clauses | after clauses | before chunks | after chunks | clause_no | hierarchy | quality |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---|\n");
        java.util.Map<String, Integer> filteredSections = new java.util.TreeMap<>();
        int beforeClauses = 0, afterClauses = 0, beforeChunks = 0, afterChunks = 0, noiseChunks = 0;
        int succeeded = 0;
        for (int i = 0; i < pdfs.size(); i++) {
            Path pdf = pdfs.get(i);
            byte[] bytes = Files.readAllBytes(pdf);
            String documentId = "pdfdry" + i;
            try {
                ParsedDocument parsed = minerUParser.parseStructured(bytes, "application/pdf", java.util.Map.of(
                        MinerUDocumentParser.OPT_SOURCE_FILE, pdf.getFileName().toString(),
                        MinerUDocumentParser.OPT_DOCUMENT_ID, documentId));
                CleanedTextImportResult before = adapter.importPdf(documentId, pdf.getFileName().toString(), bytes,
                        parsed, CleanedTextImportMode.DRY_RUN);
                LegalSectionFilter.FilterResult filtered = sectionFilter.filter(documentId, parsed);
                CleanedTextImportResult result = adapter.importPdf(documentId, pdf.getFileName().toString(), bytes,
                        filtered.document(), CleanedTextImportMode.DRY_RUN);
                var metadata = result.document().metadata();
                var quality = result.qualityReport();
                beforeClauses += before.qualityReport().clauseCount();
                afterClauses += quality.clauseCount();
                beforeChunks += before.qualityReport().chunkCount();
                afterChunks += quality.chunkCount();
                filtered.logs().forEach(log -> filteredSections.merge(log.sectionType(), 1, Integer::sum));
                noiseChunks += (int) result.chunks().stream().filter(this::containsNoise).count();
                long clauseNo = result.document().clauses().stream().filter(c -> c.clauseNo() != null).count();
                long hierarchy = result.document().clauses().stream().filter(c -> c.hierarchyPath() != null
                        && !c.hierarchyPath().isBlank()).count();
                report.append('|').append(pdf.getFileName().toString().replace("|", "\\|"))
                        .append(" | ").append(before.qualityReport().clauseCount())
                        .append(" | ").append(quality.clauseCount())
                        .append(" | ").append(before.qualityReport().chunkCount())
                        .append(" | ").append(quality.chunkCount())
                        .append(" | ").append(rate(clauseNo, quality.clauseCount()))
                        .append(" | ").append(rate(hierarchy, quality.clauseCount()))
                        .append(" | ").append(quality.qualityStatus()).append(" |\n");
                assertEquals("MINERU_PDF", metadata.sourceFormat().name());
                assertTrue(quality.clauseCount() > 0, "未生成 Clause: " + pdf);
                assertTrue(quality.chunkCount() > 0, "未生成 Chunk: " + pdf);
                succeeded++;
            } catch (Exception e) {
                report.append('|').append(pdf.getFileName().toString().replace("|", "\\|"))
                        .append(" | - | - | - | - | FAILED: ")
                        .append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replace('|', '/'))
                        .append(" |\n");
            }
        }
        report.append("\n## Corpus totals\n\n")
                .append("| metric | before | after |\n|---|---:|---:|\n")
                .append("| Clause | ").append(beforeClauses).append(" | ").append(afterClauses).append(" |\n")
                .append("| Chunk | ").append(beforeChunks).append(" | ").append(afterChunks).append(" |\n")
                .append("\n## Filtered sections\n\n| section_type | removed blocks |\n|---|---:|\n");
        filteredSections.forEach((type, count) -> report.append('|').append(type).append(" | ").append(count).append(" |\n"));
        report.append("\n## Noise chunk check\n\n")
                .append("Forbidden section content in resulting chunks: ").append(noiseChunks).append("\n\n")
                .append("Acceptance: directory/preface/referenced standards/terminology/appendix chunks must be zero; "
                        + "Clause_no and hierarchy completeness are reported per document.\n");
        Path output = Path.of("..", "PHASE5_3_NON_BODY_FILTER_REPORT.md").normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
        assertEquals(pdfs.size(), succeeded, "部分 PDF 未完成 MinerU Dry Run；报告已写入 " + output.toAbsolutePath());
    }

    private boolean containsNoise(com.safeguard.agent.legal.model.LegalChunk chunk) {
        LegalContentRole role = chunk.metadata().contentRole();
        return role == LegalContentRole.FRONT_MATTER || role == LegalContentRole.APPENDIX
                || role == LegalContentRole.SUPPLEMENTARY;
    }

    private String rate(long numerator, long denominator) {
        return denominator == 0 ? "0.00%" : String.format("%.2f%%", numerator * 100.0 / denominator);
    }

    private Set<String> selectedFiles() {
        String configured = System.getProperty("legal.pdf.files", "").strip();
        return configured.isBlank() ? Set.of() : Set.of(configured.split("\\s*;\\s*"));
    }
}
