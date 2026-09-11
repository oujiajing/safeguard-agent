package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRectificationTaskCreatorTest {
    @Test
    void sameIdempotencyKeyReturnsTheSameDemoTask() {
        DemoRectificationTaskCreator creator = new DemoRectificationTaskCreator();
        var context = new RectificationTaskCreator.TaskCreationContext(
                4L, 101109L, 1011001L, "demo-idem-1",
                new SafeGuardExecutionContext(1L, 4L, 4L, 1011001L, "demo:source-1", "trace-1"));
        var assessment = new HazardAssessment(
                "assessment-1", "临边防护缺失", "高处作业", "高", "存在坠落风险",
                List.of("设置临边防护"), List.of("防护牢固"), List.of(),
                "CONFIRMATION_REQUIRED", null, null, null, null, Instant.now(), null, List.of());

        var first = creator.create(assessment, context);
        var existing = creator.findExisting(context);

        assertThat(first.success()).isTrue();
        assertThat(existing.taskId()).isEqualTo(first.taskId());
        assertThat(existing.taskStatus()).isEqualTo("PENDING_ASSIGN");
    }
}
