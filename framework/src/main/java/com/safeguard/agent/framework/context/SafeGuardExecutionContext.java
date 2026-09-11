package com.safeguard.agent.framework.context;

public record SafeGuardExecutionContext(
        Long actorUserId,
        Long enterpriseId,
        Long projectId,
        Long teamId,
        String sourceHazardId,
        String traceId) {
    public SafeGuardExecutionContext {
        if (actorUserId == null || sourceHazardId == null || sourceHazardId.isBlank()
                || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("SafeGuard执行上下文不完整");
        }
    }
}
