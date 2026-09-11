package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHazardAssessmentRepository implements HazardAssessmentRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcHazardAssessmentRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        jdbc.execute("CREATE TABLE IF NOT EXISTS safeguard_hazard_assessment (assessment_id varchar(64) primary key, payload text not null, status varchar(32) not null, task_id varchar(128), task_status varchar(64), error_reason text, created_time timestamp with time zone not null, confirmed_time timestamp with time zone)");
    }
    @Override public void save(HazardAssessment a) {
        try {
            String payload = mapper.writeValueAsString(a);
            jdbc.update("INSERT INTO safeguard_hazard_assessment(assessment_id,payload,status,created_time) VALUES (?,?,?,?)", a.assessmentId(), payload, a.status(), timestamp(a.createdTime()));
        } catch (Exception e) { throw new IllegalStateException("保存隐患评估失败", e); }
    }
    @Override public void update(HazardAssessment a) {
        try { jdbc.update("UPDATE safeguard_hazard_assessment SET payload=?, status=?, task_id=?, task_status=?, error_reason=?, confirmed_time=? WHERE assessment_id=?", mapper.writeValueAsString(a), a.status(), a.taskId(), a.taskStatus(), a.errorReason(), timestamp(a.confirmedTime()), a.assessmentId()); }
        catch (Exception e) { throw new IllegalStateException("更新隐患评估失败", e); }
    }
    @Override public HazardAssessment find(String id) {
        return jdbc.query("SELECT payload,status,task_id,task_status,error_reason,confirmed_time FROM safeguard_hazard_assessment WHERE assessment_id=?", rs -> rs.next() ? read(rs) : null, id);
    }
    @Override public boolean markConfirmed(String id) {
        return jdbc.update("UPDATE safeguard_hazard_assessment SET status='CONFIRMED', confirmed_time=now() WHERE assessment_id=? AND status='CONFIRMATION_REQUIRED'", id) == 1;
    }
    @Override public void markTaskCreated(String id, String taskId, String taskStatus) { jdbc.update("UPDATE safeguard_hazard_assessment SET status='TASK_CREATED', task_id=?, task_status=? WHERE assessment_id=?", taskId, taskStatus, id); }
    @Override public void markFailed(String id, String reason) { jdbc.update("UPDATE safeguard_hazard_assessment SET status='FAILED', error_reason=? WHERE assessment_id=?", reason, id); }
    private HazardAssessment read(ResultSet rs) throws SQLException { try {
        HazardAssessment a = mapper.readValue(rs.getString(1), HazardAssessment.class);
        java.sql.Timestamp confirmed = rs.getTimestamp(6);
        return new HazardAssessment(a.assessmentId(), a.hazardDescription(), a.category(), a.riskLevel(), a.riskSummary(), a.rectificationSuggestions(), a.acceptanceCriteria(), a.evidence(), rs.getString(2), a.toolProposal(), rs.getString(3), rs.getString(4), rs.getString(5), a.createdTime(), confirmed == null ? a.confirmedTime() : confirmed.toInstant(), a.trace());
    } catch (Exception e) { throw new SQLException("读取隐患评估失败", e); } }
    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
