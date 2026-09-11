package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "safeguard", name = "demo-mode", havingValue = "true")
public class DemoRectificationTaskCreator implements RectificationTaskCreator {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, TaskCreationResult> tasks = new ConcurrentHashMap<>();

    @Override
    public TaskCreationResult create(HazardAssessment assessment, TaskCreationContext context) {
        if (context == null || context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
            return failed("Demo 任务创建缺少幂等键");
        }
        if (context.executionContext() == null) {
            return failed("Demo 任务创建缺少执行上下文");
        }
        return tasks.computeIfAbsent(context.idempotencyKey(), key -> new TaskCreationResult(
                true, "DEMO-TASK-%04d".formatted(sequence.incrementAndGet()), "PENDING_ASSIGN", null));
    }

    @Override
    public TaskCreationResult findExisting(TaskCreationContext context) {
        if (context == null || context.idempotencyKey() == null) return failed("未查询到既有 Demo 任务");
        return tasks.getOrDefault(context.idempotencyKey(), failed("未查询到既有 Demo 任务"));
    }

    private TaskCreationResult failed(String reason) {
        return new TaskCreationResult(false, null, null, reason);
    }
}
