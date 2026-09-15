package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "safeguard.visual-evaluation", name = "enabled", havingValue = "true")
class VisualEvaluationApplicationRunner implements ApplicationRunner {
    private final VisualHazardService visualHazardService;
    private final ObjectMapper objectMapper;

    @Value("${safeguard.visual-evaluation.manifest:rag/src/test/resources/fixtures/visual/manifest.yaml}")
    private String manifest;

    @Value("${safeguard.visual-evaluation.report:rag/target/visual-evaluation/report.json}")
    private String report;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        new VisualEvaluationRunner(visualHazardService, objectMapper).run(Path.of(manifest), Path.of(report));
    }
}
