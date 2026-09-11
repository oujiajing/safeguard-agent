package com.safeguard.agent.legal;

import com.safeguard.agent.core.parser.model.HeadingBlock;
import com.safeguard.agent.core.parser.model.ParagraphBlock;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import com.safeguard.agent.core.parser.model.Provenance;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.LegalDocumentImportAdapter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalDocumentImportAdapterTest {

    @Test
    void routesMineruBlocksThroughTheExistingLegalPipelineAndHashesPdf() {
        byte[] pdf = "%PDF-5.1 synthetic".getBytes(StandardCharsets.UTF_8);
        ParsedDocument parsed = ParsedDocument.of(List.of(
                new HeadingBlock(Provenance.ofFile("sample.pdf"), 1, "建设工程安全生产管理条例"),
                new ParagraphBlock(Provenance.ofFile("sample.pdf"), "第一条 为了加强建设工程安全生产管理，制定本条例。"),
                new ParagraphBlock(Provenance.ofFile("sample.pdf"), "（一）施工单位应当建立安全生产责任制；")
        ));

        CleanedTextImportResult result = new LegalDocumentImportAdapter(LegalTestFixtures.importer())
                .importPdf("pdf-doc-1", "sample.pdf", pdf, parsed, CleanedTextImportMode.DRY_RUN);

        assertEquals("MINERU_PDF", result.document().metadata().sourceFormat().name());
        assertEquals(LegalDocumentImportAdapter.PARSER_VERSION, result.document().metadata().parserVersion());
        assertTrue(result.document().metadata().fileHash().matches("[0-9a-f]{64}"));
        assertEquals("第一条", result.document().clauses().get(0).clauseNo());
        assertTrue(result.document().clauses().get(0).rawText().contains("（一）"));
    }

    @Test
    void rejectsMissingMineruOutput() {
        LegalDocumentImportAdapter adapter = new LegalDocumentImportAdapter(LegalTestFixtures.importer());
        assertThrows(IllegalArgumentException.class, () -> adapter.importPdf(
                "pdf-doc-1", "sample.pdf", new byte[]{1}, ParsedDocument.of(List.of()), CleanedTextImportMode.DRY_RUN));
    }
}
