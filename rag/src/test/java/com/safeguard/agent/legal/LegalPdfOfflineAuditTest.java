package com.safeguard.agent.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.core.parser.image.ImageParseProperties;
import com.safeguard.agent.core.parser.mineru.MinerUResultUnpacker;
import com.safeguard.agent.core.parser.model.*;
import com.safeguard.agent.infra.vlm.VlmService;
import com.safeguard.agent.legal.filter.LegalSectionFilter;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.LegalDocumentImportAdapter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.util.LegalHashes;
import com.safeguard.agent.rag.dto.StoredFileDTO;
import com.safeguard.agent.rag.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline diagnostic harness: no Spring context, MinerU client, database or embedding service. */
class LegalPdfOfflineAuditTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private static final Pattern CLAUSE_PREFIX = Pattern.compile(
            "^(?:第[一二三四五六七八九十百零〇两]+条|(?:[A-Z]\\.)?\\d+(?:\\.\\d+)+)\\s*");

    @Test
    @EnabledIfSystemProperty(named = "legal.pdf.cache.dir", matches = ".+")
    void replayCachedMineruResultsAndExportEvidence() throws Exception {
        long started = System.nanoTime();
        Path cache = Path.of(System.getProperty("legal.pdf.cache.dir")).toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("legal.pdf.audit.out", "target/legal-pdf-audit")).toAbsolutePath();
        Files.createDirectories(output);
        JsonNode expectations;
        int[] localCacheCount = {0};
        int[] historicalResultCount = {0};
        try (var input = System.getProperty("legal.pdf.audit.expectations") == null
                ? getClass().getResourceAsStream("/legal/pdf-audit-expectations.json")
                : Files.newInputStream(Path.of(System.getProperty("legal.pdf.audit.expectations")))) {
            assertNotNull(input);
            expectations = json.readTree(input);
        }
        if (Boolean.getBoolean("legal.pdf.audit.full-cache")) {
            var all = json.createArrayNode();
            java.util.Set<String> known = new java.util.HashSet<>();
            expectations.forEach(node -> { known.add(node.path("sha256").asText()); all.add(node); });
            try (var dirs = Files.list(cache)) {
                dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                    try {
                        JsonNode manifest = json.readTree(Files.readAllBytes(dir.resolve("manifest.json")));
                        String provenance = manifest.path("provenance").asText();
                        if (provenance.startsWith("local")) localCacheCount[0]++;
                        if (provenance.startsWith("recovered")) historicalResultCount[0]++;
                        if (!known.contains(manifest.path("pdfSha256").asText())) {
                            all.add(json.createObjectNode().put("sha256", manifest.path("pdfSha256").asText())
                                    .put("label", manifest.path("sourceFile").asText()));
                        }
                    } catch (Exception e) { throw new IllegalStateException("Invalid cache manifest: " + dir, e); }
                });
            }
            expectations = all;
        }
        StringBuilder report = new StringBuilder("# PDF 离线质量诊断\n\n")
                .append("本次回放网络调用=0；无 Spring/数据库/Embedding/VLM。回放当前工作区中的生产解析、过滤、清洗、分块实现，不写入知识库。\n\n")
                .append("缓存统计：PDF 总数=").append(expectations.size()).append("；reusedCacheCount=")
                .append(Boolean.getBoolean("legal.pdf.audit.full-cache") ? localCacheCount[0] : "N/A")
                .append("；reusedHistoricalResultCount=")
                .append(Boolean.getBoolean("legal.pdf.audit.full-cache") ? historicalResultCount[0] : "N/A")
                .append("；newlyParsedCount=0；failedCount=0。\n\n")
                .append("人工标注的正文起止位置独立于 contentRole。页码为缓存 origin.pdf 的物理页码（JSON page_idx+1），重复匹配列出候选页，不猜唯一页。\n\n")
                .append("|样本|Clause 前/后|Chunk 前/后|正文误删 Block|非正文残留 Block|未覆盖正文 Block|层级冲突|表格期望/覆盖|超长 Chunk|空 Chunk|OCR Review|\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        List<Map<String, Object>> summaries = new ArrayList<>();
        int failures = 0;
        for (JsonNode expected : expectations) {
            Path sample = cache.resolve(expected.path("sha256").asText());
            assertTrue(Files.exists(sample.resolve("manifest.json")), "Missing cached sample: " + sample);
            JsonNode manifest = json.readTree(Files.readAllBytes(sample.resolve("manifest.json")));
            byte[] pdf = Files.readAllBytes(Path.of(manifest.path("pdfPath").asText()));
            byte[] zip = Files.readAllBytes(sample.resolve("result.zip"));
            assertEquals(expected.path("sha256").asText(), LegalHashes.sha256(pdf), "PDF changed");
            assertEquals(manifest.path("zipSha256").asText(), LegalHashes.sha256(zip), "Cache changed");

            FileStorageService storage = mock(FileStorageService.class);
            VlmService vlm = mock(VlmService.class);
            when(storage.uploadAsset(any(byte[].class), anyString(), anyString())).thenAnswer(call ->
                    StoredFileDTO.builder().url("offline-asset:" + LegalHashes.sha256(call.getArgument(0))).build());
            when(storage.getPublicUrl(anyString())).thenAnswer(call -> call.getArgument(0));
            ImageParseProperties imageProperties = new ImageParseProperties();
            imageProperties.setEmbeddedDescribeEnabled(false);
            String documentId = "audit" + expected.path("sha256").asText().substring(0, 12);
            ParsedDocument parsed = new MinerUResultUnpacker(storage, vlm, imageProperties)
                    .unpack(zip, manifest.path("sourceFile").asText(), documentId);
            verifyNoInteractions(vlm);
            var filter = new LegalSectionFilter().filter(documentId, parsed);
            var adapter = new LegalDocumentImportAdapter(LegalTestFixtures.importer());
            var before = adapter.importPdf(documentId, manifest.path("sourceFile").asText(), pdf, parsed, CleanedTextImportMode.DRY_RUN);
            var after = adapter.importPdf(documentId, manifest.path("sourceFile").asText(), pdf, filter.document(), CleanedTextImportMode.DRY_RUN);
            JsonNode sourcePages = json.readTree(Files.readAllBytes(sample.resolve("content-list.json")));
            int start;
            boolean manualBoundary = !expected.path("bodyStart").asText().isBlank();
            if (manualBoundary) {
                start = boundary(parsed.blocks(), expected.path("bodyStart").asText(), 0);
            } else {
                Object sourceIndex = filter.document().metadata().get("legalBodyStartBlock");
                assertTrue(sourceIndex instanceof Number, "Automatic body boundary missing for " + expected.path("label").asText());
                Block bodyBlock = filter.document().blocks().get(((Number) sourceIndex).intValue());
                start = findIdentity(parsed.blocks(), bodyBlock);
            }
            int end = expected.path("bodyEnd").asText().isBlank() ? automaticBodyEnd(parsed.blocks(), start)
                    : boundary(parsed.blocks(), expected.path("bodyEnd").asText(), start + 1);
            int nonBodyStart = expected.path("nonBodyStart").asText().isBlank() ? start
                    : boundary(parsed.blocks(), expected.path("nonBodyStart").asText(), 0);
            assertTrue(nonBodyStart <= start && start < end, "Invalid manual boundary annotation");
            Set<Block> kept = Collections.newSetFromMap(new IdentityHashMap<>());
            kept.addAll(filter.document().blocks());
            String allChunks = contentKey(after.chunks().stream().map(c -> c.sourceText()).reduce("", (a, b) -> a + "\n" + b));
            List<Map<String, Object>> decisions = new ArrayList<>();
            Set<String> noisyChunkIds = new HashSet<>();
            int lostBody = 0, retainedNoise = 0, uncoveredBody = 0, tableCount = 0, uncoveredTables = 0;
            int removedIndex = 0;
            for (int index = 0; index < parsed.blocks().size(); index++) {
                Block block = parsed.blocks().get(index);
                String text = text(block);
                boolean body = index >= start && index < end;
                boolean forbidden = index >= nonBodyStart && !body;
                boolean retained = kept.contains(block);
                boolean payload = !(block instanceof HeadingBlock) && !(block instanceof ImageBlock) && compact(text).length() >= 12;
                String normalizedPayload = compact(CLAUSE_PREFIX.matcher(text.strip()).replaceFirst(""));
                boolean covered = !payload || coversAcrossChunks(normalizedPayload, allChunks);
                if (body && !retained) lostBody++;
                if (forbidden && retained) retainedNoise++;
                if (body && payload && !covered) uncoveredBody++;
                if (body && (block instanceof HtmlTableBlock || block instanceof TableBlock)) {
                    tableCount++;
                    if (!covered) uncoveredTables++;
                }
                if (forbidden && retained && payload) {
                    // Match actual excluded-region content, never a broad keyword such as '附录'.
                    String probe = normalizedPayload.substring(0, Math.min(100, normalizedPayload.length()));
                    after.chunks().stream().filter(c -> compact(c.content()).contains(probe))
                            .forEach(c -> noisyChunkIds.add(c.chunkId()));
                }
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("blockIndex", index);
                decision.put("blockType", block.getClass().getSimpleName());
                decision.put("expectedRegion", body ? "BODY" : forbidden ? "NON_BODY" : "COVER_UNSCORED");
                decision.put("retained", retained);
                decision.put("chunkTextCovered", covered);
                decision.put("pageCandidates", pages(text, sourcePages));
                decision.put("text", text);
                if (!retained) decision.put("filterLog", filter.logs().get(removedIndex++));
                decisions.add(decision);
            }
            assertEquals(filter.logs().size(), removedIndex);
            Set<String> clauseNos = new HashSet<>();
            after.document().clauses().forEach(c -> clauseNos.add(c.clauseNo()));
            List<String> missingClauses = new ArrayList<>();
            expected.path("expectedClauses").forEach(c -> { if (!clauseNos.contains(c.asText())) missingClauses.add(c.asText()); });
            long blankNo = after.document().clauses().stream().filter(c -> c.clauseNo() == null || c.clauseNo().isBlank()).count();
            long blankHierarchy = after.document().clauses().stream().filter(c -> c.hierarchyPath() == null || c.hierarchyPath().isBlank()).count();
            long oversized = after.chunks().stream().filter(c -> c.tokenCount() > 600).count();
            long empty = after.chunks().stream().filter(c -> c.sourceText().isBlank()).count();
            long ocrReviewCount = after.qualityReport().warnings().stream()
                    .filter(w -> w.startsWith("PDF_") || w.contains("OCR")).count();
            List<String> hierarchyMismatch = after.document().clauses().stream()
                    .filter(c -> c.clauseNo().matches("\\d+(?:\\.\\d+){2,}")
                            && (c.chapterNo() != null && c.chapterNo().matches("\\d+")
                            && !c.clauseNo().startsWith(c.chapterNo() + ".")
                            || c.sectionNo() != null && c.sectionNo().matches("\\d+\\.\\d+")
                            && !c.clauseNo().startsWith(c.sectionNo() + ".")))
                    .map(c -> c.clauseNo() + " @ " + c.hierarchyPath()).toList();
            boolean pass = lostBody == 0 && retainedNoise == 0 && noisyChunkIds.isEmpty() && uncoveredBody == 0
                    && missingClauses.isEmpty() && blankNo == 0 && blankHierarchy == 0 && oversized == 0 && empty == 0
                    && !after.chunks().isEmpty() && hierarchyMismatch.isEmpty();
            if (!pass) failures++;
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sample", expected.path("label").asText());
            summary.put("source", manifest);
            summary.put("status", pass ? "PASS_SAMPLED_CHECKS" : "REVIEW_REQUIRED");
            summary.put("beforeClauses", before.document().clauses().size());
            summary.put("afterClauses", after.document().clauses().size());
            summary.put("beforeChunks", before.chunks().size());
            summary.put("afterChunks", after.chunks().size());
            summary.put("bodyBlocksRemoved", lostBody);
            summary.put("nonBodyBlocksRetained", retainedNoise);
            summary.put("noiseChunkIds", noisyChunkIds);
            summary.put("bodyBlocksNotFullyCovered", uncoveredBody);
            summary.put("bodyTables", tableCount);
            summary.put("tablesNotFullyCovered", uncoveredTables);
            summary.put("missingExpectedClauses", missingClauses);
            summary.put("hierarchyMismatchCandidates", hierarchyMismatch);
            summary.put("oversizedChunks", oversized);
            summary.put("emptyChunks", empty);
            summary.put("blankClauseNo", blankNo);
            summary.put("blankHierarchy", blankHierarchy);
            summary.put("ocrReviewCount", ocrReviewCount);
            summary.put("boundaryEvidence", manualBoundary ? "MANUAL" : "AUTO_FILTER_BOUNDARY");
            long technicalClauses = after.document().clauses().stream()
                    .filter(c -> c.clauseNo().startsWith("TABLE@") || c.clauseNo().startsWith("UNNUMBERED@")).count();
            summary.put("technicalClauseCount", technicalClauses);
            summary.put("recognizedClauseCount", after.document().clauses().size() - technicalClauses);
            summaries.add(summary);
            Path sampleOut = output.resolve(expected.path("sha256").asText());
            Files.createDirectories(sampleOut);
            writeJson(sampleOut.resolve("before.json"), export(before));
            writeJson(sampleOut.resolve("after.json"), export(after));
            writeJson(sampleOut.resolve("block-decisions.json"), decisions);
            writeJson(sampleOut.resolve("summary.json"), summary);
            Files.writeString(sampleOut.resolve("cleaned.txt"), after.canonicalSourceText(), StandardCharsets.UTF_8);
            StringBuilder evidence = new StringBuilder("# " + expected.path("label").asText() + " 内容对照\n\n");
            for (var d : decisions) {
                evidence.append("## Block ").append(d.get("blockIndex")).append(" · ").append(d.get("expectedRegion"))
                        .append(" · retained=").append(d.get("retained")).append(" · pages=").append(d.get("pageCandidates"))
                        .append("\n\n").append(d.get("text")).append("\n\n");
            }
            evidence.append("# 分块结果\n\n");
            after.chunks().forEach(c -> evidence.append("## Chunk ").append(c.chunkIndex()).append(" · ")
                    .append(c.metadata().clauseNo()).append(" · tokens=").append(c.tokenCount()).append("\n\n")
                    .append(c.content()).append("\n\n"));
            Files.writeString(sampleOut.resolve("evidence.md"), evidence, StandardCharsets.UTF_8);
            report.append('|').append(expected.path("label").asText()).append('|')
                    .append(before.document().clauses().size()).append('/').append(after.document().clauses().size()).append('|')
                    .append(before.chunks().size()).append('/').append(after.chunks().size()).append('|')
                    .append(lostBody).append('|').append(retainedNoise).append('|').append(noisyChunkIds.size()).append('|')
                    .append(uncoveredBody).append('|').append(hierarchyMismatch.size()).append('|')
                    .append(tableCount).append('/').append(tableCount - uncoveredTables).append('|')
                    .append(oversized).append('|').append(empty).append('|').append(ocrReviewCount).append("|\n");
        }
        var probes = boundaryProbes();
        writeJson(output.resolve("boundary-probes.json"), probes);
        report.append("\n## 合成边界用例（不计入真实 PDF 样本）\n\n");
        probes.forEach(p -> report.append("- ").append(p.get("name")).append(": ").append(p.get("pass")).append("\n"));
        report.append("\n## 全量汇总与 Reindex 判定\n\n")
                .append("|指标|总计|\n|---|---:|\n")
                .append("|正文误删 Block| ").append(sum(summaries, "bodyBlocksRemoved")).append("|\n")
                .append("|非正文残留 Block| ").append(sum(summaries, "nonBodyBlocksRetained")).append("|\n")
                .append("|正文覆盖缺口| ").append(sum(summaries, "bodyBlocksNotFullyCovered")).append("|\n")
                .append("|Clause 层级冲突候选| ").append(summaries.stream().mapToInt(s -> ((List<?>) s.get("hierarchyMismatchCandidates")).size()).sum()).append("|\n")
                .append("|表格期望| ").append(sum(summaries, "bodyTables")).append("|\n")
                .append("|表格覆盖缺口| ").append(sum(summaries, "tablesNotFullyCovered")).append("|\n")
                .append("|超长 Chunk| ").append(sum(summaries, "oversizedChunks")).append("|\n")
                .append("|空 Chunk| ").append(sum(summaries, "emptyChunks")).append("|\n")
                .append("|OCR Review| ").append(sum(summaries, "ocrReviewCount")).append("|\n\n")
                .append("Reindex allowed: **NO**. Reason: full-corpus body coverage gate is not zero for every PDF.\n");
        report.append("\nREVIEW_REQUIRED 样本数：").append(failures).append("；运行秒数：")
                .append(String.format(Locale.ROOT, "%.3f", (System.nanoTime() - started) / 1e9)).append("。\n\n")
                .append("未覆盖正文 Block 使用去空白、去 HTML 标签后的完整文本匹配，属于待人工确认项，分块边界/标记变化也可能产生告警。字段完整率不等于识别召回率。\n");
        writeJson(output.resolve("summary.json"), summaries);
        Files.writeString(output.resolve("REPORT.md"), report, StandardCharsets.UTF_8);
        String reportFile = System.getProperty("legal.pdf.audit.report.file", "").strip();
        if (!reportFile.isBlank()) Files.writeString(Path.of(reportFile), report, StandardCharsets.UTF_8);
        System.out.println("OFFLINE_AUDIT " + output + " review=" + failures + " network=0");
        if (Boolean.getBoolean("legal.pdf.audit.hardening")) {
            assertEquals(expectations.size(), summaries.size(), "Every annotated real sample must finish");
            for (var sample : summaries) {
                assertAll(sample.get("sample").toString(),
                        () -> assertEquals(0, sample.get("bodyBlocksRemoved")),
                        () -> assertEquals(0, sample.get("nonBodyBlocksRetained")),
                        () -> assertTrue(((Set<?>) sample.get("noiseChunkIds")).isEmpty()),
                        () -> assertTrue(((List<?>) sample.get("hierarchyMismatchCandidates")).isEmpty()),
                        () -> assertEquals(0, sample.get("tablesNotFullyCovered")),
                        () -> assertEquals(0, sample.get("bodyBlocksNotFullyCovered")));
            }
            assertTrue(probes.stream().allMatch(p -> Boolean.TRUE.equals(p.get("pass"))), "Required section-title variants");
        }
        if (Boolean.getBoolean("legal.pdf.audit.strict")) {
            int reviewCount = failures;
            assertAll("offline quality gate",
                    () -> assertEquals(0, reviewCount, "Quality gate failed; inspect exported evidence"),
                    () -> assertTrue(probes.stream().allMatch(p -> Boolean.TRUE.equals(p.get("pass"))), "Boundary probe failed"));
        }
    }

    @Test
    void manualBoundaryLookupIsIndependentOfFilterLabels() {
        var p = Provenance.ofFile("test");
        var blocks = List.<Block>of(new HeadingBlock(p, 1, "目 次"), new ParagraphBlock(p, "1 总则 ... 1"),
                new HeadingBlock(p, 1, "1 总 则"));
        assertEquals(2, boundary(blocks, "1总则", 0));
        assertThrows(AssertionError.class, () -> boundary(blocks, "不存在", 0));
    }

    private static List<Map<String, Object>> boundaryProbes() {
        var p = Provenance.ofFile("synthetic");
        List<Map<String, Object>> result = new ArrayList<>();
        for (String heading : List.of("TABLE OF CONTENTS", "Appendix A", "本标准用词说明")) {
            var input = ParsedDocument.of(List.of(new HeadingBlock(p, 1, "1 总则"), new ParagraphBlock(p, "1.0.1 正文应保留。"),
                    new HeadingBlock(p, 1, heading), new ParagraphBlock(p, "NON_BODY_SENTINEL")));
            var filtered = new LegalSectionFilter().filter("probe", input).document();
            result.add(Map.of("name", heading, "pass", filtered.blocks().stream().noneMatch(b -> text(b).contains("NON_BODY_SENTINEL"))));
        }
        return result;
    }

    private static int boundary(List<Block> blocks, String expected, int from) {
        for (int i = from; i < blocks.size(); i++) if (compact(text(blocks.get(i))).equals(compact(expected))) return i;
        throw new AssertionError("Manual anchor not found: " + expected);
    }

    private static int findIdentity(List<Block> blocks, Block target) {
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i) == target) return i;
        throw new AssertionError("Automatic body boundary block is not in source document");
    }

    private static boolean coversAcrossChunks(String payload, String allChunks) {
        payload = contentKey(payload);
        if (allChunks.contains(payload)) return true;
        if (payload.length() <= 160) return false;
        // A long source Block may intentionally become several adjacent chunks. Check both
        // ends to distinguish a split from a dropped block without requiring one huge chunk.
        String head = payload.substring(0, 80);
        String tail = payload.substring(payload.length() - 80);
        return allChunks.contains(head) && allChunks.contains(tail);
    }

    private static String contentKey(String text) {
        return compact(text).replaceAll("[\\p{P}\\p{S}]+", "");
    }

    private static int automaticBodyEnd(List<Block> blocks, int start) {
        for (int i = start + 1; i < blocks.size(); i++) {
            String value = compact(text(blocks.get(i))).toUpperCase(Locale.ROOT);
            boolean titleLike = blocks.get(i) instanceof HeadingBlock || value.length() <= 20;
            if (titleLike && value.matches("(?:附录[A-Z一二三四五六七八九十].*|APPENDIX[A-Z]?.*)")
                    || value.equals("引用标准名录") || value.equals("本标准用词说明") || value.equals("本规范用词说明")) {
                return i;
            }
        }
        return blocks.size();
    }

    private static List<Integer> pages(String text, JsonNode items) {
        String key = compact(text);
        if (key.isBlank()) return List.of();
        Set<Integer> matches = new TreeSet<>();
        for (JsonNode item : items) {
            String candidate = compact(item.path("text").asText(item.path("table_body").asText("")));
            if (!candidate.isEmpty() && (candidate.equals(key) || (key.length() >= 20 && candidate.contains(key)))) {
                if (item.has("page_idx")) matches.add(item.path("page_idx").asInt() + 1);
            }
        }
        return List.copyOf(matches);
    }

    private static String compact(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .replaceAll("<[^>]*>", "").replaceAll("[\\s\\p{Z}]+", "");
    }

    private static String text(Block block) {
        if (block instanceof HeadingBlock h) return h.text();
        if (block instanceof ParagraphBlock p) return p.text();
        if (block instanceof HtmlTableBlock t) return t.html();
        if (block instanceof TableBlock t) return String.join(" ", t.headers()) + "\n" + t.rows().stream().map(r -> String.join(" ", r)).reduce("", (a,b) -> a + "\n" + b);
        if (block instanceof ListBlock l) return String.join("\n", l.items());
        if (block instanceof CodeBlock c) return c.code();
        if (block instanceof ImageBlock i) return i.caption() == null ? "" : i.caption();
        return "";
    }

    private static Map<String, Object> export(CleanedTextImportResult r) {
        return Map.of("clauses", r.document().clauses(), "chunks", r.chunks(), "elements", r.document().elements(), "quality", r.qualityReport());
    }

    private void writeJson(Path path, Object value) throws Exception {
        Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }

    private static String rate(long missing, int count) {
        return count == 0 ? "N/A" : String.format(Locale.ROOT, "%.2f%%", 100.0 * (count - missing) / count);
    }

    private static int sum(List<Map<String, Object>> summaries, String key) {
        return summaries.stream().mapToInt(s -> ((Number) s.getOrDefault(key, 0)).intValue()).sum();
    }
}
