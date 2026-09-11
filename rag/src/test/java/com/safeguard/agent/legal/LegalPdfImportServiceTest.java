package com.safeguard.agent.legal;

import com.safeguard.agent.core.parser.mineru.MinerUDocumentParser;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import com.safeguard.agent.legal.ingest.LegalDocumentImportAdapter;
import com.safeguard.agent.legal.ingest.LegalPdfImportService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalPdfImportServiceTest {

    @Test
    void invokesExistingMineruParserThenLegalAdapter() {
        MinerUDocumentParser parser = mock(MinerUDocumentParser.class);
        byte[] pdf = "%PDF".getBytes(StandardCharsets.UTF_8);
        when(parser.parseStructured(org.mockito.ArgumentMatchers.same(pdf),
                org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(ParsedDocument.of(List.of()));
        LegalDocumentImportAdapter adapter = mock(LegalDocumentImportAdapter.class);
        assertNull(new LegalPdfImportService(parser, adapter).dryRun("doc-1", "a.pdf", pdf));
        verify(adapter).importPdf("doc-1", "a.pdf", pdf, ParsedDocument.of(List.of()),
                com.safeguard.agent.legal.ingest.CleanedTextImportMode.DRY_RUN);
    }

    @Test
    void propagatesMineruFailureWithoutAttemptingFallback() {
        MinerUDocumentParser parser = mock(MinerUDocumentParser.class);
        when(parser.parseStructured(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new IllegalStateException("MinerU unavailable"));

        assertThrows(IllegalStateException.class, () -> new LegalPdfImportService(
                parser, mock(LegalDocumentImportAdapter.class)).dryRun("doc-1", "a.pdf", new byte[]{1}));
    }
}
