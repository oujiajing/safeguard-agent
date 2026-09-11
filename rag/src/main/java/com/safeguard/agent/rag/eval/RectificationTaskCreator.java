package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;

public interface RectificationTaskCreator {
    TaskCreationResult create(HazardAssessment assessment, TaskCreationContext context);

    default TaskCreationResult findExisting(TaskCreationContext context) {
        return new TaskCreationResult(false, null, null, "未查询到既有任务");
    }

    record TaskCreationContext(Long companyId, Long departmentId, Long teamId,
                               String idempotencyKey, SafeGuardExecutionContext executionContext) {
        public TaskCreationContext(Long companyId, Long departmentId, Long teamId) {
            this(companyId, departmentId, teamId, null, null);
        }
    }
    record TaskCreationResult(boolean success, String taskId, String taskStatus, String errorReason) {}
}
