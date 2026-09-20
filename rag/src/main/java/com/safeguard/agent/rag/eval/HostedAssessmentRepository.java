package com.safeguard.agent.rag.eval;

public interface HostedAssessmentRepository {
    void save(HostedAssessmentResponse assessment);
    HostedAssessmentResponse find(String assessmentId);
}
