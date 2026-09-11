package com.safeguard.agent.legal;

import com.safeguard.agent.legal.enums.LegalStructureType;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalChunk;
import com.safeguard.agent.legal.model.LegalSubUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "legal.corpus.dir", matches = ".+")
class LegalStructurePreservationTest {

    private static final List<String> REPRESENTATIVE_FILES = List.of(
            "《建设工程安全生产管理条例》2003.txt",
            "《建筑施工高处作业安全技术规范》JGJ 80-2016.txt",
            "《建筑施工扣件式钢管脚手架安全技术规范[附条文说明]》JGJ 130-2011.txt",
            "《施工现场临时用电安全技术规范》JGJ 46-2005.txt",
            "《建筑施工模板安全技术规范》JGJ 162-2008.txt",
            "《建筑深基坑工程施工安全技术规范》JGJ 311-2013.txt",
            "《建筑施工塔式起重机安装、使用、拆卸安全技术规程》JGJ 196-2010.txt",
            "《建筑施工工具式脚手架安全技术规范[附条文说明]》JGJ 202-2010.txt",
            "《建筑施工脚手架安全技术统一标准[附条文说明]》GB 51210-2016.txt",
            "城市住宅小区竣工综合验收管理办法.txt");

    private static final Map<String, Integer> BASELINE_CHUNKS = Map.ofEntries(
            Map.entry("《建设工程安全生产管理条例》2003.txt", 71),
            Map.entry("《建筑施工高处作业安全技术规范》JGJ 80-2016.txt", 218),
            Map.entry("《建筑施工扣件式钢管脚手架安全技术规范[附条文说明]》JGJ 130-2011.txt", 367),
            Map.entry("《施工现场临时用电安全技术规范》JGJ 46-2005.txt", 548),
            Map.entry("《建筑施工模板安全技术规范》JGJ 162-2008.txt", 448),
            Map.entry("《建筑深基坑工程施工安全技术规范》JGJ 311-2013.txt", 419),
            Map.entry("《建筑施工塔式起重机安装、使用、拆卸安全技术规程》JGJ 196-2010.txt", 134),
            Map.entry("《建筑施工工具式脚手架安全技术规范[附条文说明]》JGJ 202-2010.txt", 419),
            Map.entry("《建筑施工脚手架安全技术统一标准[附条文说明]》GB 51210-2016.txt", 359),
            Map.entry("城市住宅小区竣工综合验收管理办法.txt", 15));

    @Test
    void shouldPreserveMarkersAcrossTenRepresentativeRegulations() throws Exception {
        Path corpus = Path.of(System.getProperty("legal.corpus.dir"));
        Path reportDir = Path.of(System.getProperty("legal.report.dir", "target"));
        Files.createDirectories(reportDir);
        StringBuilder report = new StringBuilder("# Phase 2.6 Structure Preservation Report\n\n")
                .append("Baseline chunk counts are from the pre-fix Phase 2A-3 report.\n\n")
                .append("| document | clauses | chunks before | chunks after | delta | structured_clause_total | marker_expected_count | marker_preserved_count | preservation_rate |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        int totalExpected = 0;
        int totalPreserved = 0;
        for (String filename : REPRESENTATIVE_FILES) {
            Path source = corpus.resolve(filename);
            assertTrue(Files.isRegularFile(source), "缺少代表样本: " + source);
            CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                    "phase26-" + REPRESENTATIVE_FILES.indexOf(filename), filename,
                    Files.readAllBytes(source), CleanedTextImportMode.DRY_RUN);
            int structuredClauses = 0;
            int expected = 0;
            int preserved = 0;
            for (LegalClause clause : result.document().clauses()) {
                List<LegalSubUnit> marked = clause.children().stream()
                        .filter(child -> child.marker() != null && !child.marker().isBlank())
                        .toList();
                if (!marked.isEmpty()) structuredClauses++;
                for (LegalSubUnit child : marked) {
                    expected++;
                    if (result.chunks().stream().filter(chunk -> chunk.metadata().parentClauseId().equals(clause.clauseId()))
                            .anyMatch(chunk -> chunk.content().contains(child.marker() + " "))) {
                        preserved++;
                    }
                }
            }
            totalExpected += expected;
            totalPreserved += preserved;
            double rate = expected == 0 ? 1.0 : (double) preserved / expected;
            report.append("| ").append(filename.replace("|", "\\|"))
                    .append(" | ").append(result.document().clauses().size())
                    .append(" | ").append(BASELINE_CHUNKS.get(filename))
                    .append(" | ").append(result.chunks().size())
                    .append(" | ").append(result.chunks().size() - BASELINE_CHUNKS.get(filename))
                    .append(" | ").append(structuredClauses)
                    .append(" | ").append(expected)
                    .append(" | ").append(preserved)
                    .append(" | ").append(String.format("%.2f%%", rate * 100)).append(" |\n");
        }
        double totalRate = totalExpected == 0 ? 1.0 : (double) totalPreserved / totalExpected;
        report.append("\n- structured_clause_total: ").append("see table").append("\n")
                .append("- marker_expected_count: ").append(totalExpected).append("\n")
                .append("- marker_preserved_count: ").append(totalPreserved).append("\n")
                .append("- preservation_rate: ").append(String.format("%.2f%%", totalRate * 100)).append("\n")
                .append("- ITEM/SUB_ITEM/NUMBERED_LIST: current legal model classifies these as `ITEM`; markers are preserved in `LegalSubUnit.marker`.\n")
                .append("\nChunk thresholds and parser behavior were unchanged.\n");
        Files.writeString(reportDir.resolve("PHASE2_6_STRUCTURE_PRESERVATION_REPORT.md"), report.toString(), StandardCharsets.UTF_8);
        assertEquals(100.0, totalRate * 100, 0.001);
    }
}
