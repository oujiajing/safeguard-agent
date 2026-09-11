package com.safeguard.agent.legal;

import com.safeguard.agent.legal.diagnostics.LegalCorpusDiagnosticsService;
import com.safeguard.agent.legal.diagnostics.LegalDuplicateType;
import com.safeguard.agent.legal.diagnostics.UnstructuredDiagnosticType;
import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalQualityStatus;
import com.safeguard.agent.legal.enums.LegalSourceFormat;
import com.safeguard.agent.legal.enums.LegalStructureType;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.CleanedTextImporter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalDocumentElement;
import com.safeguard.agent.legal.model.LegalDocumentMetadata;
import com.safeguard.agent.legal.model.LegalQualityReport;
import com.safeguard.agent.legal.model.NormalizedLegalDocument;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class LegalDiagnosticsServiceTest {

    @Test
    void shouldReportUnaccountedSourceFragmentWhenParserOmitsSpan() throws Exception {
        String sourceText = "已解析文本\n未被 parser 纳入的文本";
        LegalDocumentElement element = element("known", "已解析文本", 0, 5);
        CleanedTextImporter importer = Mockito.mock(CleanedTextImporter.class);
        Mockito.when(importer.importText(anyString(), anyString(), any(byte[].class), eq(CleanedTextImportMode.DRY_RUN)))
                .thenReturn(result(List.of(element), List.of(), sourceText));
        Path source = Files.createTempFile("legal-coverage-", ".txt");
        try {
            Files.writeString(source, sourceText, StandardCharsets.UTF_8);
            var diagnostics = new LegalCorpusDiagnosticsService(importer).analyze(source);
            assertTrue(diagnostics.sourceTextCoverageRatio() < 1.0);
            assertTrue(diagnostics.unaccountedTextLength() > 0);
            assertTrue(diagnostics.unaccountedSourceFragments().stream().anyMatch(fragment -> !fragment.isBlank()));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void shouldIncludeClauseAndChunkTextInDeterministicSignature() {
        String signatureA = LegalCorpusDiagnosticsService.signature(result(
                List.of(element("a", "1.0.1 相同结构", 0, 12)),
                List.of(clause("a", "相同结构", 0, 12)), "1.0.1 相同结构"));
        String signatureB = LegalCorpusDiagnosticsService.signature(result(
                List.of(element("a", "1.0.1 不同文本", 0, 12)),
                List.of(clause("a", "不同文本", 0, 12)), "1.0.1 不同文本"));
        assertTrue(!signatureA.equals(signatureB));
    }

    @Test
    void shouldClassifySameSpanAsParserDuplicateAndDifferentSpansAsSourceDuplicate() throws Exception {
        String text = "1.0.1 相同正文。\n1.0.1 相同正文。\n1.0.1 相同正文。\n1.0.1 相同正文。";
        List<LegalClause> clauses = List.of(
                clause("a", "1.0.1", "相同正文。", 0, 12), clause("b", "1.0.1", "相同正文。", 0, 12),
                clause("c", "1.0.2", "相同正文。", 13, 25), clause("d", "1.0.2", "相同正文。", 26, 38));
        CleanedTextImportResult imported = result(List.of(), clauses, text);
        CleanedTextImporter importer = Mockito.mock(CleanedTextImporter.class);
        Mockito.when(importer.importText(anyString(), anyString(), any(byte[].class), eq(CleanedTextImportMode.DRY_RUN)))
                .thenReturn(imported);
        Path source = Files.createTempFile("legal-duplicate-", ".txt");
        try {
            Files.writeString(source, text, StandardCharsets.UTF_8);
            var groups = new LegalCorpusDiagnosticsService(importer).analyze(source).duplicateGroups();
            assertTrue(groups.stream().anyMatch(g -> g.duplicateOrigin() == LegalDuplicateType.PARSER_DUPLICATE));
            assertTrue(groups.stream().anyMatch(g -> g.duplicateOrigin() == LegalDuplicateType.SOURCE_EXACT_DUPLICATE));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void shouldClassifyExactNearAndCrossRoleDuplicatesAndRemainDeterministic() throws Exception {
        String text = """
                1 总则
                表A 表格残留
                1.0.1 完全相同正文。
                1.0.1 完全相同正文。
                1.0.2 第一份正文。
                1.0.2 第二份正文含残留 标题。
                条文说明
                1.0.1 解释文本。
                """;
        Path source = Files.createTempFile("legal-diagnostics-", ".txt");
        try {
            Files.writeString(source, text, StandardCharsets.UTF_8);
            var diagnostics = new LegalCorpusDiagnosticsService(LegalTestFixtures.importer()).analyze(source);
            assertTrue(diagnostics.deterministic());
            assertTrue(diagnostics.duplicateGroups().stream().anyMatch(g -> g.duplicateType() == LegalDuplicateType.EXACT_DUPLICATE
                    && g.duplicateOrigin() == LegalDuplicateType.SOURCE_EXACT_DUPLICATE));
            assertTrue(diagnostics.duplicateGroups().stream().anyMatch(g -> g.duplicateType() == LegalDuplicateType.NEAR_DUPLICATE
                    && g.duplicateOrigin() == LegalDuplicateType.SOURCE_NEAR_DUPLICATE));
            assertTrue(diagnostics.duplicateGroups().stream().anyMatch(g -> g.duplicateType() == LegalDuplicateType.SAME_NUMBER_DIFFERENT_ROLE
                    && g.duplicateOrigin() == LegalDuplicateType.STRUCTURAL_VALID));
            assertEquals(1.0, diagnostics.sourceTextCoverageRatio());
            assertTrue(diagnostics.unstructuredItems().stream().anyMatch(item -> item.diagnosticType() == UnstructuredDiagnosticType.TABLE_RESIDUE));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static CleanedTextImportResult result(List<LegalDocumentElement> elements,
                                                  List<LegalClause> clauses,
                                                  String canonicalText) {
        NormalizedLegalDocument document = new NormalizedLegalDocument(metadata(), elements, clauses, List.of(), List.of());
        return new CleanedTextImportResult(document, List.of(), new LegalQualityReport(
                "doc", null, 0, canonicalText.length(), 0, 0, clauses.size(), clauses.size(), 0,
                0, 0, 0, 0, 0, 0, 0, 0, LegalQualityStatus.PASS, List.of()), canonicalText);
    }

    private static LegalDocumentMetadata metadata() {
        return new LegalDocumentMetadata("doc", "title", "standard", "GB 1", "authority", null, null,
                "source.txt", LegalSourceFormat.CLEANED_TXT, "hash", "parser");
    }

    private static LegalDocumentElement element(String id, String text, int start, int end) {
        return new LegalDocumentElement(id, "doc", 0, text, text, LegalStructureType.CLAUSE,
                LegalContentRole.NORMATIVE, "1.0.1", null, null, 0, start, end);
    }

    private static LegalClause clause(String id, String text, int start, int end) {
        return clause(id, "1.0.1", text, start, end);
    }

    private static LegalClause clause(String id, String clauseNo, String text, int start, int end) {
        return new LegalClause(id, "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                null, null, null, null, clauseNo, clauseNo, text, text, List.of(), id, id,
                null, null, start, end);
    }
}
