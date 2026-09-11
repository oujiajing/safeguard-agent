package com.safeguard.agent.legal;

import com.safeguard.agent.infra.token.HeuristicTokenCounterService;
import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalChunk;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalQualityReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "legal.corpus.dir", matches = ".+")
class LegalCorpusDryRunTest {

    private static final List<String> REPRESENTATIVE_FILES = List.of(
            "《建设工程安全生产管理条例》2003.txt",
            "《房屋市政工程生产安全重大事故隐患判定标准》（2022版）.txt",
            "《建筑施工安全检查标准》JGJ 59-2011.txt",
            "《建筑施工扣件式钢管脚手架安全技术规范[附条文说明]》JGJ 130-2011.txt",
            "《建筑拆除工程安全技术规范》JGJ 147-2016.txt",
            "《建筑施工碗扣式钢管脚手架安全技术规范》JGJ 166-2016.txt",
            "《头部防护 安全帽》GB 2811-2019.txt",
            "《建设工程项目管理规范[附条文说明]》GBT 50326-2017.txt",
            "建筑结构加固工程施工质量验收规范 GB 50550-2010.txt",
            "建设工程质量管理条例.txt");

    private final HeuristicTokenCounterService tokenCounter = new HeuristicTokenCounterService();

    @Test
    void shouldDryRunTenRepresentativeDocumentsAndExportReport() throws IOException {
        Path corpus = Path.of(System.getProperty("legal.corpus.dir"));
        assertTrue(Files.isDirectory(corpus), "corpus directory 不存在: " + corpus);

        List<DocumentRun> runs = new ArrayList<>();
        for (int i = 0; i < REPRESENTATIVE_FILES.size(); i++) {
            String filename = REPRESENTATIVE_FILES.get(i);
            Path source = corpus.resolve(filename);
            assertTrue(Files.isRegularFile(source), "缺少代表样本: " + source);
            CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                    String.format("21000000000000000%02d", i),
                    filename,
                    Files.readAllBytes(source),
                    CleanedTextImportMode.DRY_RUN);
            runs.add(new DocumentRun(filename, result));
        }

        assertEquals(10, runs.size());
        assertTrue(runs.stream().flatMap(r -> r.result().document().clauses().stream())
                .anyMatch(c -> c.structureType().name().equals("ARTICLE")));
        assertTrue(runs.stream().flatMap(r -> r.result().document().clauses().stream())
                .anyMatch(c -> c.clauseNo().matches("\\d+(?:\\.\\d+){2,}")));
        assertTrue(hasNormativeCommentaryPair(runs));
        assertTrue(runs.stream().anyMatch(r -> r.result().qualityReport().supplementaryCount() > 0));
        assertTrue(runs.stream().anyMatch(r -> r.result().qualityReport().appendixCount() > 0));
        assertTrue(runs.stream().allMatch(r -> r.result().chunks().stream()
                .allMatch(c -> c.metadata().parentClauseId() != null)));
        assertTrue(runs.stream().allMatch(r -> r.result().qualityReport().emptyChunkCount() == 0));
        assertTrue(runs.stream().allMatch(r -> r.result().qualityReport().pageCount() == null
                && r.result().qualityReport().tableCount() == 0));

        String report = buildReport(corpus, runs);
        Path target = Path.of("target", "legal-corpus-dry-run.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, report, StandardCharsets.UTF_8);
        assertTrue(Files.size(target) > 1000);
    }

    private String buildReport(Path corpus, List<DocumentRun> runs) {
        StringBuilder out = new StringBuilder("# Legal TXT 代表样本 DRY_RUN 原始结果\n\n")
                .append("- corpus: `").append(corpus).append("`\n")
                .append("- sampleCount: ").append(runs.size()).append("\n")
                .append("- mode: DRY_RUN\n")
                .append("- maxTokens: 450\n")
                .append("- hardLimitTokens: 600\n\n")
                .append("## 代表样本与覆盖\n\n")
                .append("1. 法规 Article：建设工程安全生产管理条例\n")
                .append("2. 短法规/metadata 变体：重大事故隐患判定标准\n")
                .append("3. decimal + 编号异常空格 + 多 item：建筑施工安全检查标准\n")
                .append("4. 同号交错条文说明：JGJ 130-2011\n")
                .append("5. 本规范用词说明 + 显式条文说明：JGJ 147-2016\n")
                .append("6. 附录 + 显式条文说明：JGJ 166-2016\n")
                .append("7. 不规则 Unicode 标准号：GB 2811-2019\n")
                .append("8. GBT/附条文说明命名：GB/T 50326-2017\n")
                .append("9. 大文件/长条款：GB 50550-2010\n")
                .append("10. 文件名缺年份与标准号：建设工程质量管理条例\n\n")
                .append("## 逐文档 QC\n\n")
                .append("| 文件 | title | standardNo | clauses | normative | commentary | supplementary elements | appendix elements | unstructured | chunks | oversized | duplicate | QC | warnings |\n")
                .append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|\n");
        for (DocumentRun run : runs) appendDocumentRow(out, run);

        List<Integer> clauseTokens = runs.stream()
                .flatMap(run -> run.result().document().clauses().stream())
                .map(clause -> tokenCounter.countTokens(clause.normalizedText()))
                .sorted()
                .toList();
        Distribution distribution = Distribution.of(clauseTokens);
        long under = clauseTokens.stream().filter(v -> v <= 450).count();
        long middle = clauseTokens.stream().filter(v -> v >= 451 && v <= 600).count();
        long over = clauseTokens.stream().filter(v -> v > 600).count();
        long totalChunks = runs.stream().mapToLong(r -> r.result().chunks().size()).sum();
        long oversizedChunks = runs.stream().mapToLong(r -> r.result().qualityReport().oversizedChunkCount()).sum();
        long emptyChunks = runs.stream().mapToLong(r -> r.result().qualityReport().emptyChunkCount()).sum();

        out.append("\n## Clause token distribution\n\n")
                .append("| clauses | min | mean | median | p90 | p95 | p99 | max | <=450 | 451-600 | >600 | long-clause % |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n")
                .append("| ").append(clauseTokens.size()).append(" | ")
                .append(distribution.min()).append(" | ")
                .append(String.format("%.2f", distribution.mean())).append(" | ")
                .append(distribution.median()).append(" | ")
                .append(distribution.p90()).append(" | ")
                .append(distribution.p95()).append(" | ")
                .append(distribution.p99()).append(" | ")
                .append(distribution.max()).append(" | ")
                .append(under).append(" | ").append(middle).append(" | ").append(over).append(" | ")
                .append(String.format("%.2f", ratio(middle + over, clauseTokens.size()) * 100)).append("% |\n")
                .append("\nChunk: count=").append(totalChunks)
                .append(", oversized=").append(oversizedChunks)
                .append(", empty=").append(emptyChunks).append("\n");

        out.append("\n## Clause 示例（前 5 个跨文档样本）\n");
        runs.stream().flatMap(run -> run.result().document().clauses().stream()
                        .map(clause -> Map.entry(run.filename(), clause)))
                .limit(5).forEach(entry -> appendClause(out, entry.getKey(), entry.getValue()));

        out.append("\n## Chunk 示例（前 10 个跨文档样本）\n");
        runs.stream().flatMap(run -> run.result().chunks().stream()
                        .map(chunk -> Map.entry(run.filename(), chunk)))
                .limit(10).forEach(entry -> appendChunk(out, entry.getKey(), entry.getValue()));

        out.append("\n## NORMATIVE / COMMENTARY 同号隔离\n\n");
        appendRolePairs(out, runs);

        out.append("\n## SUPPLEMENTARY / APPENDIX / 未结构化\n\n");
        for (DocumentRun run : runs) {
            LegalQualityReport qc = run.result().qualityReport();
            if (qc.supplementaryCount() > 0 || qc.appendixCount() > 0 || qc.unstructuredParagraphCount() > 0) {
                out.append("- ").append(run.filename())
                        .append(": supplementary=").append(qc.supplementaryCount())
                        .append(", appendix=").append(qc.appendixCount())
                        .append(", unstructured=").append(qc.unstructuredParagraphCount()).append('\n');
            }
        }
        out.append("\n## 空 Clause 诊断\n\n");
        runs.forEach(run -> run.result().document().clauses().stream()
                .filter(clause -> clause.normalizedText().isBlank())
                .forEach(clause -> out.append("- ").append(run.filename()).append(" / ")
                        .append(clause.contentRole()).append(" / ").append(clause.clauseNo())
                        .append(" / raw=").append(preview(clause.rawText(), 160)).append('\n')));

        out.append("\n## 同 role 重复 Clause 诊断（每文档前 20 组）\n\n");
        for (DocumentRun run : runs) appendDuplicateDiagnostics(out, run);
        return out.toString();
    }

    private void appendDuplicateDiagnostics(StringBuilder out, DocumentRun run) {
        Map<String, List<LegalClause>> groups = new LinkedHashMap<>();
        for (LegalClause clause : run.result().document().clauses()) {
            groups.computeIfAbsent(clause.contentRole() + "|" + clause.clauseNo(), ignored -> new ArrayList<>())
                    .add(clause);
        }
        groups.entrySet().stream().filter(entry -> entry.getValue().size() > 1).limit(20).forEach(entry -> {
            out.append("- ").append(run.filename()).append(" / ").append(entry.getKey())
                    .append(" / count=").append(entry.getValue().size()).append("\n");
            entry.getValue().stream().limit(3).forEach(clause -> out.append("  - ")
                    .append(preview(clause.rawText(), 180)).append("\n"));
        });
    }

    private void appendDocumentRow(StringBuilder out, DocumentRun run) {
        LegalQualityReport qc = run.result().qualityReport();
        var meta = run.result().document().metadata();
        out.append("| ").append(cell(run.filename()))
                .append(" | ").append(cell(meta.docTitle()))
                .append(" | ").append(cell(meta.standardNo()))
                .append(" | ").append(qc.clauseCount())
                .append(" | ").append(qc.normativeClauseCount())
                .append(" | ").append(qc.commentaryClauseCount())
                .append(" | ").append(qc.supplementaryCount())
                .append(" | ").append(qc.appendixCount())
                .append(" | ").append(qc.unstructuredParagraphCount())
                .append(" | ").append(qc.chunkCount())
                .append(" | ").append(qc.oversizedChunkCount())
                .append(" | ").append(qc.duplicateClauseCount())
                .append(" | ").append(qc.qualityStatus())
                .append(" | ").append(cell(String.join("; ", qc.warnings()))).append(" |\n");
    }

    private void appendClause(StringBuilder out, String filename, LegalClause clause) {
        out.append("\n### ").append(filename).append(" / ").append(clause.contentRole())
                .append(" / ").append(clause.clauseNo()).append("\n\n")
                .append("- parentClauseId: `").append(clause.clauseId()).append("`\n")
                .append("- hierarchy: ").append(clause.hierarchyPath()).append("\n")
                .append("- raw preview: ").append(preview(clause.rawText(), 300)).append("\n");
    }

    private void appendChunk(StringBuilder out, String filename, LegalChunk chunk) {
        out.append("\n### ").append(filename).append(" / chunk ").append(chunk.chunkIndex()).append("\n\n")
                .append("- parentClauseId: `").append(chunk.metadata().parentClauseId()).append("`\n")
                .append("- clauseNo: ").append(chunk.metadata().clauseNo()).append("\n")
                .append("- role: ").append(chunk.metadata().contentRole()).append("\n")
                .append("- tokens: ").append(chunk.tokenCount()).append("\n")
                .append("- preview: ").append(preview(chunk.content(), 300)).append("\n");
    }

    private void appendRolePairs(StringBuilder out, List<DocumentRun> runs) {
        int pairs = 0;
        for (DocumentRun run : runs) {
            Map<String, List<LegalClause>> byNo = new LinkedHashMap<>();
            for (LegalClause clause : run.result().document().clauses()) {
                byNo.computeIfAbsent(clause.clauseNo(), ignored -> new ArrayList<>()).add(clause);
            }
            for (Map.Entry<String, List<LegalClause>> entry : byNo.entrySet()) {
                boolean normative = entry.getValue().stream().anyMatch(c -> c.contentRole() == LegalContentRole.NORMATIVE);
                boolean commentary = entry.getValue().stream().anyMatch(c -> c.contentRole() == LegalContentRole.COMMENTARY);
                if (normative && commentary) {
                    out.append("- ").append(run.filename()).append(" / ").append(entry.getKey())
                            .append(": 独立 clauseId=")
                            .append(entry.getValue().stream().map(LegalClause::clauseId).toList()).append('\n');
                    if (++pairs >= 10) return;
                }
            }
        }
        assertTrue(pairs > 0, "代表样本中未形成同号 NORMATIVE/COMMENTARY 对");
    }

    private boolean hasNormativeCommentaryPair(List<DocumentRun> runs) {
        for (DocumentRun run : runs) {
            for (LegalClause a : run.result().document().clauses()) {
                for (LegalClause b : run.result().document().clauses()) {
                    if (a != b && a.clauseNo().equals(b.clauseNo())
                            && a.contentRole() == LegalContentRole.NORMATIVE
                            && b.contentRole() == LegalContentRole.COMMENTARY) return true;
                }
            }
        }
        return false;
    }

    private String cell(String text) {
        return text == null || text.isBlank() ? "-" : text.replace("|", "\\|").replace("\n", " ");
    }

    private String preview(String text, int limit) {
        String flat = text == null ? "" : text.replace('\n', ' ').strip();
        return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private record DocumentRun(String filename, CleanedTextImportResult result) {
    }

    private record Distribution(int min, double mean, int median, int p90, int p95, int p99, int max) {
        private static Distribution of(List<Integer> sorted) {
            assertFalse(sorted.isEmpty());
            double mean = sorted.stream().mapToInt(Integer::intValue).average().orElse(0);
            return new Distribution(sorted.get(0), mean, percentile(sorted, 0.50), percentile(sorted, 0.90),
                    percentile(sorted, 0.95), percentile(sorted, 0.99), sorted.get(sorted.size() - 1));
        }

        private static int percentile(List<Integer> sorted, double p) {
            assertNotNull(sorted);
            int index = (int) Math.ceil(p * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }
}
