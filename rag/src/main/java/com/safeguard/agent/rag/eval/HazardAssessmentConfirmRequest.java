package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;

public record HazardAssessmentConfirmRequest(Long companyId, Long departmentId, Long teamId,
                                             String idempotencyKey,
                                             SafeGuardExecutionContext executionContext) {
    public HazardAssessmentConfirmRequest(Long companyId, Long departmentId, Long teamId) {
        this(companyId, departmentId, teamId, null, null);
    }
}
