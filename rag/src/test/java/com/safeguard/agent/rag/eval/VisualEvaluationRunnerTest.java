package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisualEvaluationRunnerTest {
    @Test
    void calculatesFixtureMetricsAndWritesScenarioReport() throws Exception {
        VisualHazardService service = mock(VisualHazardService.class);
        when(service.analyze(any())).thenAnswer(invocation -> {
            VisualHazardAnalysisRequest request = invocation.getArgument(0);
            return switch (request.executionContext().sourceHazardId()) {
                case "synthetic-unprotected-stairwell-no-helmet" -> context(List.of(
                        candidate("临边无防护", "CONFIRMED", false), candidate("未佩戴安全帽", "CONFIRMED", false),
                        candidate("未穿戴反光背心", "SUSPECTED", true), candidate("未佩戴安全带", "CONFIRMED", false)));
                case "synthetic-small-no-helmet-group" -> context(List.of(candidate("未佩戴安全帽", "CONFIRMED", true)));
                default -> context(List.of(candidate("临时用电", "CONFIRMED", false), candidate("湿滑地面", "CONFIRMED", false),
                        candidate("消防通道堵塞", "CONFIRMED", false)));
            };
        });
        Path report = Files.createTempDirectory("visual-evaluation").resolve("report.json");
        VisualEvaluationRunner.Report result = new VisualEvaluationRunner(service, new YAMLMapper(), new ObjectMapper())
                .run(Path.of("src/test/resources/fixtures/visual/manifest.yaml"), report);

        assertEquals(3, result.samples().size());
        VisualEvaluationRunner.Metrics metrics = result.metricsBySource().get("generated");
        assertEquals(1.0, metrics.candidateRecall());
        assertEquals(7.0 / 8.0, metrics.candidatePrecision());
        assertEquals(1.0 / 3.0, metrics.forbiddenHallucinationRate());
        assertEquals(2.0 / 8.0, metrics.manualReviewRate());
        assertEquals(3, new ObjectMapper().readTree(report.toFile()).path("samples").size());
    }

    @Test
    void keepsApprovedDeidentifiedRealMetricsSeparateFromSyntheticMetrics() throws Exception {
        VisualHazardService service = mock(VisualHazardService.class);
        when(service.analyze(any())).thenReturn(context(List.of()));
        Path report = Files.createTempDirectory("visual-evaluation-real").resolve("report.json");

        VisualEvaluationRunner.Report result = new VisualEvaluationRunner(service, new YAMLMapper(), new ObjectMapper()).run(
                Path.of("src/test/resources/fixtures/visual/real/keremberke-valid-mini-a19eace/real-manifest.yaml"), report);

        assertEquals(1, result.samples().size());
        assertEquals(0.0, result.metricsBySource().get("real-deidentified").forbiddenHallucinationRate());
    }

    private static VisualHazardContext context(List<VisualHazardContext.Candidate> candidates) {
        return new VisualHazardContext("analysis", "scene", null, "test", Instant.EPOCH, candidates);
    }

    private static VisualHazardContext.Candidate candidate(String type, String judgement, boolean manual) {
        return new VisualHazardContext.Candidate("candidate", type, "object", type, List.of(type), "risk", judgement, 0.8, manual);
    }
}
