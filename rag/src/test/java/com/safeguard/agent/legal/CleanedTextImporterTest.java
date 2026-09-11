package com.safeguard.agent.legal;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalQualityStatus;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanedTextImporterTest {

    @Test
    void shouldParseLawArticlesAndItemsWithoutChangingRawText() {
        String text = """
                建设工程安全生产管理条例
                第一章 总则
                第一条 为了加强建设工程安全生产管理，制定本条例。
                （一）施工单位应当建立安全生产责任制；
                1）项目负责人应当履职。
                第二条 在中华人民共和国境内从事建设活动，应当遵守本条例。
                """;

        CleanedTextImportResult result = run("《建设工程安全生产管理条例》2003.txt", text);

        assertEquals(2, result.document().clauses().size());
        assertEquals("第一条", result.document().clauses().get(0).clauseNo());
        assertEquals(3, result.document().clauses().get(0).children().size());
        assertTrue(result.document().clauses().get(0).rawText().contains("（一）"));
        assertEquals(LegalContentRole.NORMATIVE, result.document().clauses().get(0).contentRole());
        assertTrue(result.chunks().stream().allMatch(c -> c.metadata().parentClauseId() != null));
        assertEquals(LegalQualityStatus.PASS, result.qualityReport().qualityStatus());
    }

    @Test
    void shouldIsolateInlineCommentaryWithSameClauseNumber() {
        String text = """
                建筑施工扣件式钢管脚手架安全技术规范
                JGJ 130-2011
                1总则
                1.0.1 为确保施工人员安全，制定本规范。
                1.0.1 本条说明制定本规范的目的和依据。
                1.0.2 本规范适用于房屋建筑工程。
                1.0.2 本条明确了本规范的适用范围。
                """;

        CleanedTextImportResult result = run(
                "《建筑施工扣件式钢管脚手架安全技术规范[附条文说明]》JGJ 130-2011.txt", text);

        assertEquals(4, result.document().clauses().size());
        assertEquals(LegalContentRole.NORMATIVE, result.document().clauses().get(0).contentRole());
        assertEquals(LegalContentRole.COMMENTARY, result.document().clauses().get(1).contentRole());
        assertEquals("1.0.1", result.document().clauses().get(0).clauseNo());
        assertEquals("1.0.1", result.document().clauses().get(1).clauseNo());
        assertNotEquals(result.document().clauses().get(0).clauseId(), result.document().clauses().get(1).clauseId());
        assertEquals(0, result.qualityReport().duplicateClauseCount());
    }

    @Test
    void shouldKeepSupplementarySeparateFromCommentaryAndAppendix() {
        String text = """
                建筑拆除工程安全技术规范
                JGJ 147-2016
                1 总则
                1.0.1 为规范建筑拆除工程施工，制定本规范。
                附录A 检查记录
                A.0.1 检查记录应如实填写。
                本规范用词说明
                1 表示很严格，非这样做不可的用词：正面词采用“必须”。
                条文说明
                1 总则
                1.0.1 本条说明制定目的。
                """;

        CleanedTextImportResult result = run("《建筑拆除工程安全技术规范》JGJ 147-2016.txt", text);

        assertTrue(result.qualityReport().supplementaryCount() > 0);
        assertTrue(result.qualityReport().appendixCount() > 0);
        assertTrue(result.document().clauses().stream().anyMatch(c -> c.contentRole() == LegalContentRole.APPENDIX));
        assertTrue(result.document().clauses().stream().anyMatch(c -> c.contentRole() == LegalContentRole.COMMENTARY));
        assertFalse(result.document().clauses().stream().anyMatch(c -> c.contentRole() == LegalContentRole.SUPPLEMENTARY));
    }

    @Test
    void shouldCanonicalizeOnlyTheLegalNumberAndRetainOriginalLine() {
        String text = """
                建筑施工安全检查标准
                JGJ 59-2011
                3 检查评定项目
                3 .1 安全管理
                3 .1 .1 安全管理检查评定应符合有关规定。
                """;

        CleanedTextImportResult result = run("《建筑施工安全检查标准》JGJ 59-2011.txt", text);

        assertEquals("3.1.1", result.document().clauses().get(0).clauseNo());
        assertTrue(result.document().clauses().get(0).rawText().startsWith("3 .1 .1"));
        assertTrue(result.document().clauses().get(0).hierarchyPath().contains("3.1 安全管理"));
    }

    @Test
    void shouldExitAppendixOnAFollowingChapterBoundary() {
        String text = """
                1 总则
                1.0.1 正文。
                附录A 资料
                A.0.1 附录内容。
                2 后续正文
                2.0.1 后续正文条款。
                """;
        CleanedTextImportResult result = run("标准 JGJ 1-2020.txt", text);
        assertEquals(LegalContentRole.APPENDIX, result.document().clauses().get(1).contentRole());
        assertEquals(LegalContentRole.NORMATIVE, result.document().clauses().get(2).contentRole());
    }

    @Test
    void shouldSplitHeadingResidueFromPreviousClause() {
        String text = """
                1 总则
                1.0.1 正文内容。
                5 拆除施工
                5.1.1 拆除作业应安全。
                """;
        CleanedTextImportResult result = run("标准 JGJ 147-2016.txt", text);
        assertEquals(2, result.document().clauses().size());
        assertEquals("1.0.1", result.document().clauses().get(0).clauseNo());
        assertEquals("5.1.1", result.document().clauses().get(1).clauseNo());
        assertTrue(result.document().clauses().get(1).hierarchyPath().contains("5 拆除施工"));
    }

    private CleanedTextImportResult run(String file, String text) {
        return LegalTestFixtures.importer().importText(
                "2000000000000000001", file, text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
    }
}
