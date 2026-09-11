package com.safeguard.agent.legal;

import com.safeguard.agent.legal.diagnostics.LegalCorpusDiagnosticsService;
import com.safeguard.agent.legal.diagnostics.LegalDocumentDiagnostics;
import com.safeguard.agent.legal.diagnostics.LegalDuplicateGroup;
import com.safeguard.agent.legal.diagnostics.LegalDuplicateType;
import com.safeguard.agent.legal.diagnostics.LegalUnstructuredItem;
import com.safeguard.agent.legal.diagnostics.UnstructuredDiagnosticType;
import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalChunk;
import com.safeguard.agent.legal.model.LegalQualityReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "legal.corpus.dir", matches = ".+")
class LegalFullCorpusDiagnosticsTest {

    @Test
    void shouldGenerateFullCorpusReadOnlyDiagnostics() throws IOException {
        Path corpus = Path.of(System.getProperty("legal.corpus.dir"));
        Path reportDir = Path.of(System.getProperty("legal.report.dir", "target"));
        Files.createDirectories(reportDir);

        LegalCorpusDiagnosticsService service = new LegalCorpusDiagnosticsService(LegalTestFixtures.importer());
        List<LegalDocumentDiagnostics> documents = service.analyzeAll(corpus);
        assertEquals(93, documents.size());
        assertTrue(documents.stream().allMatch(LegalDocumentDiagnostics::deterministic));
        assertTrue(documents.stream().allMatch(d -> d.result().qualityReport().emptyChunkCount() == 0));
        assertTrue(documents.stream().allMatch(d -> d.result().qualityReport().pageCount() == null));
        assertTrue(documents.stream().allMatch(d -> d.result().qualityReport().tableCount() == 0));

        write(reportDir.resolve("PHASE2A_FULL_CORPUS_DRY_RUN_REPORT.md"), buildFullReport(documents));
        write(reportDir.resolve("PHASE2A_REVIEW_DOCUMENTS.md"), buildReviewReport(documents));
        write(reportDir.resolve("PHASE2A_DUPLICATE_DIAGNOSTICS.md"), buildDuplicateReport(documents));
        write(reportDir.resolve("PHASE2A_UNSTRUCTURED_DIAGNOSTICS.md"), buildUnstructuredReport(documents));
        if (Boolean.getBoolean("legal.write.baseline")) {
            write(reportDir.resolve("PHASE2A2_BASELINE.json"), buildBaselineJson(documents));
        }
        write(reportDir.resolve("PHASE2A3_BEFORE_AFTER_REPORT.md"), buildBeforeAfterReport(reportDir.resolve("PHASE2A2_BASELINE.json"), documents));
    }

    private String buildFullReport(List<LegalDocumentDiagnostics> documents) {
        int pass = countQuality(documents, "PASS");
        int review = countQuality(documents, "REVIEW");
        int failed = countQuality(documents, "FAILED");
        int totalClauses = documents.stream().mapToInt(d -> d.quality().clauseCount()).sum();
        int normative = documents.stream().mapToInt(d -> d.quality().normativeClauseCount()).sum();
        int commentary = documents.stream().mapToInt(d -> d.quality().commentaryClauseCount()).sum();
        int supplementary = documents.stream().mapToInt(d -> d.quality().supplementaryCount()).sum();
        int appendix = documents.stream().mapToInt(d -> d.quality().appendixCount()).sum();
        int unstructured = documents.stream().mapToInt(d -> d.quality().unstructuredParagraphCount()).sum();
        int chunks = documents.stream().mapToInt(d -> d.quality().chunkCount()).sum();
        int empty = documents.stream().mapToInt(d -> d.quality().emptyChunkCount()).sum();
        int oversized = documents.stream().mapToInt(d -> d.quality().oversizedChunkCount()).sum();
        int duplicates = documents.stream().mapToInt(d -> d.quality().duplicateClauseCount()).sum();
        List<Integer> clauseTokens = documents.stream().flatMap(d -> d.result().document().clauses().stream())
                .map(c -> tokenCount(c.normalizedText())).sorted().toList();
        List<Integer> chunkTokens = documents.stream().flatMap(d -> d.result().chunks().stream())
                .map(LegalChunk::tokenCount).sorted().toList();
        StringBuilder out = new StringBuilder("# Phase 2A-2 全量 cleaned TXT 只读 DRY_RUN 报告\n\n")
                .append("- mode: DRY_RUN only\n- sourceFormat: CLEANED_TXT\n- no DB/vector/ES/LLM calls\n\n")
                .append("## Corpus Summary\n\n")
                .append("| metric | value |\n|---|---:|\n")
                .append(row("totalDocuments", documents.size()))
                .append(row("PASS", pass)).append(row("REVIEW", review)).append(row("FAILED", failed))
                .append(row("totalClauses", totalClauses)).append(row("normativeClauses", normative))
                .append(row("commentaryClauses", commentary)).append(row("supplementaryElements", supplementary))
                .append(row("appendixElements", appendix)).append(row("unstructuredElements", unstructured))
                .append(row("totalChunks", chunks)).append(row("emptyChunks", empty))
                .append(row("oversizedChunks", oversized)).append(row("duplicateClauses", duplicates))
                .append(row("longClauseCount", clauseTokens.stream().filter(v -> v > 450).count()))
                .append(row("longClauseRatio", String.format("%.2f%%", ratio(clauseTokens.stream().filter(v -> v > 450).count(), clauseTokens.size()) * 100)))
                .append("\n## Clause token distribution\n\n")
                .append(distributionTable(clauseTokens))
                .append("\n## Chunk token distribution\n\n")
                .append(distributionTable(chunkTokens))
                .append("\n## Deterministic check\n\n")
                .append("- documents checked twice: ").append(documents.size()).append("\n")
                .append("- deterministic PASS: ").append(documents.stream().filter(LegalDocumentDiagnostics::deterministic).count()).append("\n")
                .append("- source coverage min: ").append(String.format("%.4f", documents.stream().mapToDouble(LegalDocumentDiagnostics::sourceTextCoverageRatio).min().orElse(0))).append("\n")
                .append("- source coverage mean: ").append(String.format("%.4f", documents.stream().mapToDouble(LegalDocumentDiagnostics::sourceTextCoverageRatio).average().orElse(0))).append("\n")
                .append("- source text total: ").append(documents.stream().mapToInt(LegalDocumentDiagnostics::sourceTextLength).sum()).append("\n")
                .append("- accounted text total: ").append(documents.stream().mapToInt(LegalDocumentDiagnostics::accountedTextLength).sum()).append("\n")
                .append("- unaccounted text total: ").append(documents.stream().mapToInt(LegalDocumentDiagnostics::unaccountedTextLength).sum()).append("\n")
                .append("- structured text ratio min: ").append(String.format("%.4f", documents.stream().mapToDouble(LegalDocumentDiagnostics::structuredTextRatio).min().orElse(0))).append("\n")
                .append("- structured text ratio mean: ").append(String.format("%.4f", documents.stream().mapToDouble(LegalDocumentDiagnostics::structuredTextRatio).average().orElse(0))).append("\n")
                .append("\n## Appendix diagnostics\n\n");
        documents.stream().filter(d -> d.quality().appendixCount() > 0).sorted(Comparator.comparingInt((LegalDocumentDiagnostics d) -> d.quality().appendixCount()).reversed())
                .limit(20).forEach(d -> out.append("- ").append(d.document()).append(": appendixElements=")
                        .append(d.quality().appendixCount()).append(", appendixClauses=")
                        .append(d.result().document().clauses().stream().filter(c -> c.contentRole() == LegalContentRole.APPENDIX).count())
                        .append(", totalElements=").append(d.result().document().elements().size()).append('\n'));
        out.append("\n## Top 5 parser/QC problems\n\n")
                .append("1. Source exact/near duplicates in multi-section TXT exports.\n")
                .append("2. Appendix numbering and large appendix regions require context review.\n")
                .append("3. Unstructured heading/table/formula residue in low-structure standards.\n")
                .append("4. Filename/body metadata gaps for regulations without standard numbers.\n")
                .append("5. Long clauses above 450 tokens need human review despite safe chunk output.\n")
                .append("\n## Recommendation\n\n")
                .append("本轮结果保留 source duplicate / source-format REVIEW；是否进入下一阶段以 Phase 2A-3 Before / After 报告中的硬条件判定为准。\n");
        return out.toString();
    }

    private String buildReviewReport(List<LegalDocumentDiagnostics> docs) {
        StringBuilder out = new StringBuilder("# Phase 2A REVIEW / FAILED 文档\n\n");
        docs.stream().filter(d -> !"PASS".equals(d.quality().qualityStatus().name())).forEach(d -> {
            out.append("## ").append(d.document()).append("\n\n")
                    .append("- QC: ").append(d.quality().qualityStatus()).append("\n")
                    .append("- duplicate: ").append(d.duplicateGroups().size()).append(" groups\n")
                    .append("- unstructured: ").append(d.unstructuredItems().size()).append(" (coverage ")
                    .append(String.format("%.4f", d.sourceTextCoverageRatio())).append(")\n")
                    .append("- source/accounted/unaccounted: ").append(d.sourceTextLength()).append("/")
                    .append(d.accountedTextLength()).append("/").append(d.unaccountedTextLength()).append("\n")
                    .append("- structuredTextRatio: ").append(String.format("%.4f", d.structuredTextRatio())).append("\n")
                    .append("- metadata warnings: ").append(String.join("; ", d.metadataWarnings())).append("\n")
                    .append("- QC warnings: ").append(String.join("; ", d.quality().warnings())).append("\n\n");
        });
        return out.toString();
    }

    private String buildBaselineJson(List<LegalDocumentDiagnostics> docs) {
        StringBuilder out = new StringBuilder("{\n  \"documents\": [\n");
        for (int i = 0; i < docs.size(); i++) {
            LegalDocumentDiagnostics d = docs.get(i);
            var q = d.quality();
            out.append("    {\"document\":\"").append(json(d.document())).append("\"")
                    .append(",\"title\":\"").append(json(d.result().document().metadata().docTitle())).append("\"")
                    .append(",\"standardNo\":\"").append(json(d.result().document().metadata().standardNo())).append("\"")
                    .append(",\"clauseCount\":").append(q.clauseCount())
                    .append(",\"chunkCount\":").append(q.chunkCount())
                    .append(",\"normative\":").append(q.normativeClauseCount())
                    .append(",\"commentary\":").append(q.commentaryClauseCount())
                    .append(",\"appendix\":").append(q.appendixCount())
                    .append(",\"unstructured\":").append(q.unstructuredParagraphCount())
                    .append(",\"duplicateGroups\":").append(d.duplicateGroups().size())
                    .append(",\"qc\":\"").append(q.qualityStatus()).append("\"}");
            if (i + 1 < docs.size()) out.append(',');
            out.append('\n');
        }
        return out.append("  ]\n}\n").toString();
    }

    private String buildBeforeAfterReport(Path baselinePath, List<LegalDocumentDiagnostics> documents) throws IOException {
        Map<String, BaselineRow> before = loadBaseline(baselinePath, documents);
        StringBuilder out = new StringBuilder("# Phase 2A-3 Before / After\n\n")
                .append("Baseline: `PHASE2A2_BASELINE.json`\n\n")
                .append("- baseline documents loaded: ").append(before.size()).append("/93\n")
                .append("- PARSER_DUPLICATE groups: ").append(countOrigin(documents, LegalDuplicateType.PARSER_DUPLICATE)).append("\n")
                .append("- SOURCE_EXACT_DUPLICATE groups: ").append(countOrigin(documents, LegalDuplicateType.SOURCE_EXACT_DUPLICATE)).append("\n")
                .append("- SOURCE_NEAR_DUPLICATE groups: ").append(countOrigin(documents, LegalDuplicateType.SOURCE_NEAR_DUPLICATE)).append("\n")
                .append("- unaccounted text total: ").append(documents.stream().mapToInt(LegalDocumentDiagnostics::unaccountedTextLength).sum()).append("\n")
                .append("- >10% clause delta documents: ").append(documents.stream().filter(d -> Math.abs(clauseDelta(before.get(d.document()), d.quality().clauseCount())) > 10).count()).append("\n")
                .append("- REVIEW → PASS: ").append(countTransition(before, documents, "REVIEW", "PASS")).append("\n")
                .append("- PASS → REVIEW: ").append(countTransition(before, documents, "PASS", "REVIEW")).append("\n")
                .append("| document | clause before | clause after | delta % | chunk before | chunk after | unstructured before | unstructured after | appendix before | appendix after | QC before | QC after | coverage | deterministic |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|---:|---|\n");
        for (LegalDocumentDiagnostics d : documents) {
            LegalQualityReport q = d.quality();
            BaselineRow b = before.get(d.document());
            double delta = b.clauseCount() == 0 ? 0 : ((double) q.clauseCount() - b.clauseCount()) / b.clauseCount() * 100;
            out.append("| ").append(cell(d.document())).append(" | ").append(b.clauseCount()).append(" | ").append(q.clauseCount()).append(" | ")
                    .append(String.format("%.2f%%", delta)).append(" | ").append(b.chunkCount()).append(" | ").append(q.chunkCount())
                    .append(" | ").append(b.unstructuredCount()).append(" | ").append(q.unstructuredParagraphCount()).append(" | ").append(b.appendixCount()).append(" | ").append(q.appendixCount())
                    .append(" | ").append(b.qualityStatus()).append(" | ").append(q.qualityStatus()).append(" | ").append(String.format("%.4f", d.sourceTextCoverageRatio()))
                    .append(" | ").append(d.deterministic()).append(" |\n");
        }
        boolean hardConditions = documents.size() == 93
                && documents.stream().allMatch(LegalDocumentDiagnostics::deterministic)
                && documents.stream().allMatch(d -> d.result().qualityReport().emptyChunkCount() == 0)
                && documents.stream().allMatch(d -> d.result().qualityReport().oversizedChunkCount() == 0)
                && documents.stream().flatMap(d -> d.duplicateGroups().stream())
                .noneMatch(g -> g.duplicateOrigin() == LegalDuplicateType.PARSER_DUPLICATE)
                && documents.stream().mapToInt(LegalDocumentDiagnostics::unaccountedTextLength).sum() == 0;
        out.append("\n## Recommendation\n\n")
                .append(hardConditions
                        ? "建议进入 Phase 2A-4：93/93 deterministic，空/超大 chunk、PARSER_DUPLICATE 与 unaccounted text 均为 0，且 baseline 已完整加载。\n"
                        : "暂不建议进入 Phase 2A-4：未满足全部验收硬条件；请根据上表和诊断报告处理 deterministic、chunk、parser duplicate、baseline 或 unaccounted text 问题。\n");
        return out.toString();
    }

    private long countOrigin(List<LegalDocumentDiagnostics> documents, LegalDuplicateType origin) {
        return documents.stream().flatMap(d -> d.duplicateGroups().stream())
                .filter(g -> g.duplicateOrigin() == origin).count();
    }

    private double clauseDelta(BaselineRow baseline, int after) {
        return baseline == null || baseline.clauseCount() == 0 ? 0 : ((double) after - baseline.clauseCount()) / baseline.clauseCount() * 100;
    }

    private long countTransition(Map<String, BaselineRow> before, List<LegalDocumentDiagnostics> documents,
                                 String from, String to) {
        return documents.stream().filter(d -> from.equals(before.get(d.document()).qualityStatus())
                && to.equals(d.quality().qualityStatus().name())).count();
    }

    private Map<String, BaselineRow> loadBaseline(Path baselinePath, List<LegalDocumentDiagnostics> documents) throws IOException {
        assertTrue(Files.exists(baselinePath), "baseline 文件不存在: " + baselinePath);
        JsonNode root = new ObjectMapper().readTree(Files.readString(baselinePath, StandardCharsets.UTF_8));
        JsonNode rows = root.get("documents");
        assertTrue(rows != null && rows.isArray(), "baseline.documents 必须是数组");
        assertEquals(93, rows.size(), "baseline document count 必须为 93");
        Map<String, BaselineRow> loaded = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String document = requiredText(row, "document");
            assertTrue(loaded.put(document, new BaselineRow(
                    requiredInt(row, "clauseCount"), requiredInt(row, "chunkCount"),
                    requiredInt(row, "unstructured"), requiredInt(row, "appendix"),
                    requiredText(row, "qc"))) == null, "baseline document 重复: " + document);
        }
        assertEquals(93, loaded.size(), "baseline document 必须唯一");
        for (LegalDocumentDiagnostics document : documents) {
            assertTrue(loaded.containsKey(document.document()), "当前 document 缺少 baseline: " + document.document());
        }
        return loaded;
    }

    private String requiredText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        assertTrue(value != null && value.isTextual(), "baseline 字段必须是文本: " + field);
        return value.textValue();
    }

    private int requiredInt(JsonNode row, String field) {
        JsonNode value = row.get(field);
        assertTrue(value != null && value.canConvertToInt(), "baseline 字段必须是整数: " + field);
        return value.intValue();
    }

    private String buildDuplicateReport(List<LegalDocumentDiagnostics> docs) {
        StringBuilder out = new StringBuilder("# Phase 2A Duplicate Diagnostics\n\n");
        Map<LegalDuplicateType, Integer> counts = new EnumMap<>(LegalDuplicateType.class);
        docs.stream().flatMap(d -> d.duplicateGroups().stream()).forEach(g -> counts.merge(g.duplicateType(), 1, Integer::sum));
        out.append("## 分类汇总\n\n");
        counts.forEach((type, count) -> out.append("- ").append(type).append(": ").append(count).append(" groups\n"));
        out.append("\n## Same-number different-role confirmation\n\n");
        docs.stream().flatMap(d -> d.duplicateGroups().stream())
                .filter(g -> g.duplicateType() == LegalDuplicateType.SAME_NUMBER_DIFFERENT_ROLE).limit(30)
                .forEach(g -> out.append("- ").append(g.document()).append(" / ").append(g.clauseNo())
                        .append(" / origin=").append(g.duplicateOrigin()).append("\n"));
        out.append("\n## Exact and near duplicate groups\n\n");
        docs.stream().flatMap(d -> d.duplicateGroups().stream())
                .filter(g -> g.duplicateType() != LegalDuplicateType.SAME_NUMBER_DIFFERENT_ROLE)
                .forEach(g -> out.append("### ").append(g.document()).append(" / ").append(g.clauseNo()).append("\n\n")
                .append("- role: ").append(g.contentRole()).append("\n- type: ").append(g.duplicateType()).append("\n- duplicateOrigin: ").append(g.duplicateOrigin()).append("\n- count: ").append(g.duplicateClauseCount()).append("\n")
                .append("- preview A: ").append(g.textPreviews().isEmpty() ? "-" : g.textPreviews().get(0)).append("\n")
                .append("- preview B: ").append(g.textPreviews().size() < 2 ? "-" : g.textPreviews().get(1)).append("\n\n"));
        return out.toString();
    }

    private String buildUnstructuredReport(List<LegalDocumentDiagnostics> docs) {
        StringBuilder out = new StringBuilder("# Phase 2A Unstructured Diagnostics\n\n");
        docs.stream().sorted(Comparator.comparingInt((LegalDocumentDiagnostics d) -> d.unstructuredItems().size()).reversed())
                .limit(20).forEach(d -> {
                    out.append("## ").append(d.document()).append("\n\n")
                            .append("- count: ").append(d.unstructuredItems().size()).append("\n")
                            .append("- ratio: ").append(String.format("%.4f", ratio(d.unstructuredItems().size(), d.result().document().elements().size()))).append("\n\n")
                            .append("| order | diagnostic | raw text |\n|---:|---|---|\n");
                    d.unstructuredItems().stream().limit(30).forEach(item -> out.append("| ").append(item.elementOrder()).append(" | ").append(item.diagnosticType()).append(" | ").append(cell(item.rawText())).append(" |\n"));
                    out.append('\n');
                });
        return out.toString();
    }

    private int countQuality(List<LegalDocumentDiagnostics> docs, String status) {
        return (int) docs.stream().filter(d -> status.equals(d.quality().qualityStatus().name())).count();
    }

    private String distributionTable(List<Integer> values) {
        if (values.isEmpty()) return "| count | min | mean | median | p90 | p95 | p99 | max |\n|---:|---:|---:|---:|---:|---:|---:|---:|\n| 0 | - | - | - | - | - | - | - |\n";
        return "| count | min | mean | median | p90 | p95 | p99 | max |\n|---:|---:|---:|---:|---:|---:|---:|---:|\n| " + values.size() + " | " + values.get(0) + " | " + String.format("%.2f", values.stream().mapToInt(Integer::intValue).average().orElse(0)) + " | " + percentile(values, .5) + " | " + percentile(values, .9) + " | " + percentile(values, .95) + " | " + percentile(values, .99) + " | " + values.get(values.size() - 1) + " |\n";
    }

    private int tokenCount(String text) {
        Integer count = new com.safeguard.agent.infra.token.HeuristicTokenCounterService().countTokens(text);
        return count == null ? 0 : count;
    }

    private int percentile(List<Integer> values, double p) {
        return values.get(Math.max(0, Math.min(values.size() - 1, (int) Math.ceil(values.size() * p) - 1)));
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private String row(String key, Object value) {
        return "| " + key + " | " + value + " |\n";
    }

    private String cell(String value) {
        return value == null ? "-" : value.replace("|", "\\|").replace("\n", " ");
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        assertFalse(Files.size(path) == 0);
    }

    private record BaselineRow(int clauseCount, int chunkCount, int unstructuredCount, int appendixCount, String qualityStatus) {
    }
}
