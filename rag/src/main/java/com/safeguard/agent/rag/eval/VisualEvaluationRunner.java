package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Runs the versioned visual-fixture manifest through the production visual analysis service. */
public class VisualEvaluationRunner {
    private final VisualHazardService visualHazardService;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public VisualEvaluationRunner(VisualHazardService visualHazardService, ObjectMapper jsonMapper) {
        this(visualHazardService, new YAMLMapper(), jsonMapper);
    }

    VisualEvaluationRunner(VisualHazardService visualHazardService, ObjectMapper yamlMapper, ObjectMapper jsonMapper) {
        this.visualHazardService = visualHazardService;
        this.yamlMapper = yamlMapper;
        this.jsonMapper = jsonMapper;
    }

    public Report run(Path manifestPath, Path reportPath) throws IOException {
        Manifest manifest = yamlMapper.readValue(manifestPath.toFile(), Manifest.class);
        if (manifest.samples() == null || manifest.samples().isEmpty()) {
            throw new IllegalArgumentException("视觉评测 manifest 不包含样本");
        }
        List<SampleReport> reports = new ArrayList<>();
        for (Sample sample : manifest.samples()) {
            if (!Set.of("generated", "real-deidentified").contains(sample.source())) {
                throw new IllegalArgumentException("视觉评测样本必须是 generated 或已脱敏的 real-deidentified: " + sample.id());
            }
            Path imagePath = manifestPath.getParent().resolve(sample.file()).normalize();
            if (!Files.isRegularFile(imagePath)) {
                throw new IllegalArgumentException("视觉评测图片不存在: " + imagePath);
            }
            String image = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            VisualHazardContext result = visualHazardService.analyze(new VisualHazardAnalysisRequest(
                    "仅依据图片输出可观察施工安全隐患；不可观察时请标为待人工复核。",
                    image,
                    new SafeGuardExecutionContext(0L, null, null, null, sample.id(), "visual-eval-" + sample.id())));
            reports.add(toReport(sample, result));
        }
        Map<String, Metrics> metricsBySource = reports.stream().collect(Collectors.groupingBy(
                SampleReport::source, java.util.LinkedHashMap::new, Collectors.collectingAndThen(Collectors.toList(), this::metrics)));
        Report report = new Report(java.time.Instant.now().toString(), manifest.version(), reports, metricsBySource);
        Path parent = reportPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        return report;
    }

    private SampleReport toReport(Sample sample, VisualHazardContext result) {
        Set<String> expected = normalized(sample.expectedPresent());
        Set<String> forbidden = normalized(sample.expectedAbsent());
        List<VisualHazardContext.Candidate> candidates = result.hazardCandidates() == null ? List.of() : result.hazardCandidates();
        Set<String> returned = new LinkedHashSet<>();
        int manualReviews = 0;
        for (VisualHazardContext.Candidate candidate : candidates) {
            if (candidate.needsManualVerification()) manualReviews++;
            if ("CONFIRMED".equals(candidate.judgement()) || "SUSPECTED".equals(candidate.judgement())) {
                returned.add(normalize(candidate.hazardType()));
            }
        }
        Set<String> foundExpected = intersection(expected, returned);
        Set<String> foundForbidden = intersection(forbidden, returned);
        return new SampleReport(sample.id(), sample.source(), expected, forbidden, returned, foundExpected, foundForbidden,
                candidates.size(), manualReviews);
    }

    private Metrics metrics(List<SampleReport> reports) {
        int expected = reports.stream().mapToInt(report -> report.expectedPresent().size()).sum();
        int foundExpected = reports.stream().mapToInt(report -> report.foundExpected().size()).sum();
        int returned = reports.stream().mapToInt(report -> report.returned().size()).sum();
        int forbidden = reports.stream().mapToInt(report -> report.expectedAbsent().size()).sum();
        int foundForbidden = reports.stream().mapToInt(report -> report.foundForbidden().size()).sum();
        int candidates = reports.stream().mapToInt(SampleReport::candidateCount).sum();
        int manualReviews = reports.stream().mapToInt(SampleReport::manualReviewCount).sum();
        return new Metrics(rate(foundExpected, expected), rate(foundExpected, returned), rate(foundForbidden, forbidden),
                rate(manualReviews, candidates), expected, foundExpected, returned, forbidden, foundForbidden, candidates, manualReviews);
    }

    private static Double rate(int numerator, int denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    private static Set<String> normalized(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) values.forEach(value -> result.add(normalize(value)));
        return result;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Manifest(int version, List<Sample> samples) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Sample(String id, String file, String source,
                  @JsonProperty("expected_present") List<String> expectedPresent,
                  @JsonProperty("expected_absent") List<String> expectedAbsent) {}
    public record Report(String generatedAt, int manifestVersion, List<SampleReport> samples,
                         Map<String, Metrics> metricsBySource) {}
    public record SampleReport(String sampleId, String source, Set<String> expectedPresent, Set<String> expectedAbsent,
                               Set<String> returned, Set<String> foundExpected, Set<String> foundForbidden,
                               int candidateCount, int manualReviewCount) {}
    public record Metrics(Double candidateRecall, Double candidatePrecision, Double forbiddenHallucinationRate,
                          Double manualReviewRate, int expectedPresentCount, int foundExpectedCount, int returnedCount,
                          int expectedAbsentCount, int foundForbiddenCount, int candidateCount, int manualReviewCount) {}
}
