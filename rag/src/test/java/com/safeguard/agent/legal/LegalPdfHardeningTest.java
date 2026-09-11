package com.safeguard.agent.legal;

import com.safeguard.agent.core.parser.model.Block;
import com.safeguard.agent.core.parser.model.HeadingBlock;
import com.safeguard.agent.core.parser.model.HtmlTableBlock;
import com.safeguard.agent.core.parser.model.ParagraphBlock;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import com.safeguard.agent.core.parser.model.Provenance;
import com.safeguard.agent.legal.enums.LegalQualityStatus;
import com.safeguard.agent.legal.enums.LegalStructureType;
import com.safeguard.agent.legal.filter.LegalSectionFilter;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.LegalDocumentImportAdapter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegalPdfHardeningTest {
    private static final Provenance SOURCE = Provenance.ofFile("hardening.pdf");

    @Test
    void recognizesTocVariantsWithoutDeletingBody() {
        for (String title : List.of("目次", "目 次", "TABLE OF CONTENTS", "Contents")) {
            var input = List.<Block>of(h(title), p("1 总则……(1)\n2 术语……(2)"), h("1 总则"), p("1.0.1 正文必须保留。"));
            assertEquals(input.subList(2, 4), filtered(input).blocks(), title);
        }
    }

    @Test
    void preservesOcrDamagedScopeAndDocumentTitle() {
        var input = List.<Block>of(h("前 言"), p("修订说明。"), h("头部防护"), h("范团"),
                p("本标准规定了产品的适用范围。"), h("2规范性引用文件"), p("引用文件正文。"));
        assertEquals(input.subList(2, input.size()), filtered(input).blocks());
    }

    @Test
    void tocPageNumberIsNotABodyStartEvenWhenRecognizedAsHeading() {
        var input = List.<Block>of(h("前言"), p("编制说明。"), h("目次"), h("1 总则  1"),
                h("1 总则"), p("1.0.1 应保留的正文。"));
        assertEquals(input.subList(4, 6), filtered(input).blocks());
    }

    @Test
    void doesNotDeletePrefaceWhenBodyBoundaryIsMissing() {
        var input = List.<Block>of(h("前言"), p("无法确认正文边界的内容。"), h("OCR异常标题"));
        var result = filtered(input);
        assertEquals(input, result.blocks());
        assertFalse(((List<?>) result.metadata().get("legalSectionFilterWarnings")).isEmpty());
    }

    @Test
    void duplicateCoverTitleDoesNotMoveBodyBoundaryToCover() {
        var input = List.<Block>of(h("头部防护"), h("前言"), p("编制说明。"), h("头部防护"),
                h("范团"), p("本标准规定了适用范围。"));
        assertEquals(1, filtered(input).metadata().get("legalBodyStartBlock"));
    }

    @Test
    void doesNotTreatUntrustedParagraphAsPrefaceHeading() {
        var input = List.<Block>of(p("前言"), p("应保留待复核。"), h("1总则"), p("1.0.1 正文。"));
        assertEquals(input, filtered(input).blocks());
    }

    @Test
    void doesNotStartPrefaceDeletionInsideBody() {
        var input = List.<Block>of(h("1总则"), p("1.0.1 正文。"), h("前言"), p("正文中的说明应保留。"));
        assertEquals(input, filtered(input).blocks());
    }

    @Test
    void preservesEllipsisInBodyRatherThanTreatingItAsTocLeader() {
        var input = List.<Block>of(h("1总则"), p("1.0.1 数值范围……10"));
        assertEquals(input, filtered(input).blocks());
    }

    @Test
    void uncertainPrefaceBoundaryIsVisibleInQualityWarnings() {
        var result = run(List.of(p("前言"), p("需要复核的前部内容。"), h("1总则"), p("1.0.1 正文。")));
        assertTrue(result.qualityReport().warnings().stream().anyMatch(w -> w.startsWith("PDF_PREFACE_RETAINED")));
        assertEquals(LegalQualityStatus.REVIEW, result.qualityReport().qualityStatus());
    }

    @Test
    void keepsBodyAppendixReferencesButDropsAppendixSection() {
        var input = List.<Block>of(h("1总则"), p("附录A规定的方法可用于本条检验。"),
                h("Appendix A"), p("NON_BODY"));
        assertEquals(input.subList(0, 2), filtered(input).blocks());
    }

    @Test
    void softWrappedReferenceStaysInsideItsClause() {
        var result = run(List.of(h("6 配电设施"), h("6.2 配电室"),
                p("6.2.1 配电室的要求应符合本规范第\n5.0.1条、第5.0.2条的有关规定。")));
        assertEquals(List.of("6.2.1"), result.document().clauses().stream().map(c -> c.clauseNo()).toList());
        assertTrue(result.chunks().get(0).content().contains("5.0.1条、第5.0.2条的有关规定。"));
        assertTrue(result.document().clauses().get(0).rawText().contains("\n5.0.1"));
    }

    @Test
    void separateReferenceBlockDoesNotBecomeClause() {
        var result = run(List.of(h("6 配电设施"), h("6.2 配电室"), p("6.2.1 应符合本规范第"),
                p("5.0.1条、第5.0.2条的有关规定。"), p("第5.0.3条也适用。")));
        assertEquals(1, result.document().clauses().size());
        assertTrue(result.document().clauses().get(0).normalizedText().contains("第5.0.3条"));
    }

    @Test
    void rejectsIllegalHierarchyAndNumbersWithoutContent() {
        var result = run(List.of(h("6 配电设施"), h("6.2 配电室"), p("6.2.1 已确认正文。"),
                p("5.0.9 不应生成第五章条款。"), p("6.2.2")));
        assertEquals(1, result.document().clauses().size());
        assertEquals("6.2.1", result.document().clauses().get(0).clauseNo());
        assertTrue(result.canonicalSourceText().contains("5.0.9"));
        assertEquals(LegalQualityStatus.REVIEW, result.qualityReport().qualityStatus());
    }

    @Test
    void recognizesLongAndCompactChapterHeadings() {
        var result = run(List.of(h("2 术语"), h("3供用电设施的设计、施工、验收及运行维护的基本要求"),
                h("3.1 设计要求"), p("3.1.1 应按要求设计。"),
                h("10办公、生活用电及现场照明"), h("10.1 办公用电"), p("10.1.1 应符合规定。")));
        assertEquals(List.of("3", "10"), result.document().clauses().stream().map(c -> c.chapterNo()).toList());
    }

    @Test
    void allowsMissingChapterHeadingWhenNextChapterHasSupportingNumbers() {
        var result = run(List.of(h("2规范性引用文件"), p("需保留的说明。"), h("4分类与标记"),
                h("4.2 分类标记"), p("4.2.1 产品标记应符合要求。")));
        assertTrue(result.document().clauses().stream().anyMatch(c -> c.clauseNo().equals("4.2.1") && c.chapterNo().equals("4")));
    }

    @Test
    void tableWithoutNumberedClauseGetsExplicitTechnicalIdentity() {
        String table = "<table><tr><td>5.0.1</td><td>必须保留的表格内容</td></tr></table>";
        var result = run(List.of(h("1 总则"), h("1.2 数据表"), new HtmlTableBlock(SOURCE, table)));
        var clause = result.document().clauses().get(0);
        assertEquals(LegalStructureType.TABLE, clause.structureType());
        assertEquals(1, result.qualityReport().tableCount());
        assertTrue(clause.clauseNo().startsWith("TABLE@"));
        assertEquals(table, result.chunks().get(0).sourceText());
        assertEquals(LegalQualityStatus.REVIEW, result.qualityReport().qualityStatus());
    }

    @Test
    void structuredTableRowsArePreservedWithoutInterpretingCellNumbers() {
        var table = new com.safeguard.agent.core.parser.model.TableBlock(SOURCE,
                List.of("编号", "内容"), List.of(List.of("5.0.1", "应保留的单元格内容")));
        var result = run(List.of(h("1总则"), table));
        assertEquals(1, result.document().clauses().size());
        assertTrue(result.document().clauses().get(0).clauseNo().startsWith("TABLE@"));
        assertTrue(result.chunks().get(0).sourceText().contains("5.0.1 | 应保留的单元格内容"));
    }

    @Test
    void tableHtmlEntitiesAndPunctuationAreNotSentenceBoundaries() {
        String table = "<table>" + "<tr><td>甲；乙 &lt;500</td></tr>".repeat(200) + "</table>";
        var result = run(List.of(h("1 总则"), p("1.0.1 应符合下表。"), new HtmlTableBlock(SOURCE, table)));
        String normalized = java.text.Normalizer.normalize(table, java.text.Normalizer.Form.NFKC);
        assertTrue(result.chunks().stream().anyMatch(c -> c.sourceText().contains(normalized)));
        assertTrue(result.qualityReport().oversizedChunkCount() > 0);
    }

    @Test
    void doesNotPromoteInternalLineToIndependentClause() {
        var result = run(List.of(h("1 总则"), p("1.0.1 保留原文中的\n1.0.2 内部数字字符串。")));
        assertEquals(1, result.document().clauses().size());
        assertTrue(result.chunks().get(0).sourceText().contains("1.0.2"));
    }

    private static ParsedDocument filtered(List<Block> blocks) {
        return new LegalSectionFilter().filter("hardening", ParsedDocument.of(blocks)).document();
    }

    private static CleanedTextImportResult run(List<Block> blocks) {
        return new LegalDocumentImportAdapter(LegalTestFixtures.importer()).importPdf("hardening", "hardening.pdf",
                new byte[]{1}, filtered(blocks), CleanedTextImportMode.DRY_RUN);
    }

    private static HeadingBlock h(String text) { return new HeadingBlock(SOURCE, 1, text); }
    private static ParagraphBlock p(String text) { return new ParagraphBlock(SOURCE, text); }
}
