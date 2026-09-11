package com.safeguard.agent.rag.eval;

public interface HazardAssessmentRepository {
    void save(HazardAssessment assessment);
    void update(HazardAssessment assessment);
    HazardAssessment find(String assessmentId);
    boolean markConfirmed(String assessmentId);
    void markTaskCreated(String assessmentId, String taskId, String taskStatus);
    void markFailed(String assessmentId, String reason);
}
