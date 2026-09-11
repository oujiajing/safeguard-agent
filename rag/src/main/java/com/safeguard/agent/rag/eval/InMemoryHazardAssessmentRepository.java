package com.safeguard.agent.rag.eval;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class InMemoryHazardAssessmentRepository implements HazardAssessmentRepository {
    private final Map<String, HazardAssessment> data = new ConcurrentHashMap<>();
    public void save(HazardAssessment a) { data.put(a.assessmentId(), a); }
    public void update(HazardAssessment a) { data.put(a.assessmentId(), a); }
    public HazardAssessment find(String id) { return data.get(id); }
    public boolean markConfirmed(String id) { return data.computeIfPresent(id, (k,a) -> a.status().equals("CONFIRMATION_REQUIRED") ? new HazardAssessment(a.assessmentId(),a.hazardDescription(),a.category(),a.riskLevel(),a.riskSummary(),a.rectificationSuggestions(),a.acceptanceCriteria(),a.evidence(),"CONFIRMED",a.toolProposal(),a.taskId(),a.taskStatus(),a.errorReason(),a.createdTime(),java.time.Instant.now(),a.trace()) : a).status().equals("CONFIRMED"); }
    public void markTaskCreated(String id,String taskId,String taskStatus) { data.computeIfPresent(id,(k,a)->new HazardAssessment(a.assessmentId(),a.hazardDescription(),a.category(),a.riskLevel(),a.riskSummary(),a.rectificationSuggestions(),a.acceptanceCriteria(),a.evidence(),"TASK_CREATED",a.toolProposal(),taskId,taskStatus,a.errorReason(),a.createdTime(),a.confirmedTime(),a.trace())); }
    public void markFailed(String id,String reason) { data.computeIfPresent(id,(k,a)->new HazardAssessment(a.assessmentId(),a.hazardDescription(),a.category(),a.riskLevel(),a.riskSummary(),a.rectificationSuggestions(),a.acceptanceCriteria(),a.evidence(),"FAILED",a.toolProposal(),a.taskId(),a.taskStatus(),reason,a.createdTime(),a.confirmedTime(),a.trace())); }
}
