package com.safeguard.agent.legal;

import com.safeguard.agent.legal.config.LegalIngestionProperties;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalChunkerTest {

    @Test
    void shouldSplitLongClauseAtItemsAndSentencesWithoutSubstringCut() {
        LegalIngestionProperties properties = LegalTestFixtures.properties();
        properties.getChunk().setMaxTokens(45);
        properties.getChunk().setHardLimitTokens(100);
        String text = """
                建筑施工安全检查标准
                JGJ 59-2011
                3 检查评定项目
                3.1 安全管理
                3.1.1 安全管理检查评定应符合下列规定：
                1. 工程项目部应建立安全生产责任制，并由责任人签字确认。
                2. 工程项目部应编制施工组织设计。专项施工方案应按规定审核批准。
                3. 工程项目部应定期组织安全检查，对发现的隐患及时整改。
                """;

        CleanedTextImportResult result = LegalTestFixtures.importer(properties).importText(
                "2000000000000000002", "《建筑施工安全检查标准》JGJ 59-2011.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);

        assertTrue(result.chunks().size() >= 2);
        assertTrue(result.chunks().stream().allMatch(c -> c.metadata().parentClauseId()
                .equals(result.document().clauses().get(0).clauseId())));
        assertTrue(result.chunks().stream().allMatch(c -> c.content().contains("3.1.1")));
        assertTrue(result.chunks().stream().noneMatch(c -> c.sourceText().isBlank()));
        assertEquals(0, result.qualityReport().emptyChunkCount());
        assertEquals(0, result.qualityReport().oversizedChunkCount());
    }

    @Test
    void shouldKeepNormalClauseAsOneChunk() {
        String text = """
                建筑施工安全检查标准
                JGJ 59-2011
                1 总则
                1.0.1 为规范建筑施工安全检查，制定本标准。
                """;
        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000003", "《建筑施工安全检查标准》JGJ 59-2011.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
        assertEquals(1, result.chunks().size());
        assertEquals("1.0.1", result.chunks().get(0).metadata().clauseNo());
    }

    @Test
    void shouldPreserveDigitItemMarkersInShortClause() {
        String text = """
                城市住宅小区竣工综合验收管理办法
                第六条 住宅小区竣工综合验收必须符合下列要求：
                1 所有建设项目按批准的小区规划全部建成；
                2 单项工程全部验收合格；
                3 各类建筑物符合规划设计要求。
                """;

        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000004", "城市住宅小区竣工综合验收管理办法.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);

        assertEquals(1, result.chunks().size());
        String content = result.chunks().get(0).content();
        assertTrue(content.contains("1 所有建设项目"));
        assertTrue(content.contains("2 单项工程"));
        assertTrue(content.contains("3 各类建筑物"));
    }

    @Test
    void shouldPreserveChineseItemMarkersInShortClause() {
        String text = """
                城市住宅小区竣工综合验收管理办法
                第六条 住宅小区竣工综合验收必须符合下列要求：
                （一）所有建设项目全部建成；
                （二）单项工程全部验收合格；
                （三）各类建筑物符合规划设计要求。
                """;

        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000005", "城市住宅小区竣工综合验收管理办法.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);

        String content = result.chunks().get(0).content();
        assertTrue(content.contains("(一) 所有建设项目"));
        assertTrue(content.contains("(二) 单项工程"));
        assertTrue(content.contains("(三) 各类建筑物"));
    }

    @Test
    void shouldKeepOrdinaryClauseUnchangedByStructuredRendering() {
        String text = """
                城市住宅小区竣工综合验收管理办法
                第五条 城市人民政府建设行政主管部门应当组织验收。
                """;

        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000006", "城市住宅小区竣工综合验收管理办法.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);

        assertEquals(1, result.chunks().size());
        assertTrue(result.chunks().get(0).content().contains("第五条\n城市人民政府建设行政主管部门应当组织验收。"));
    }
}
