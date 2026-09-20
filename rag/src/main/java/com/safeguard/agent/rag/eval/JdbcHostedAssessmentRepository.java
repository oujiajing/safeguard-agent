package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHostedAssessmentRepository implements HostedAssessmentRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcHostedAssessmentRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        jdbc.execute("CREATE TABLE IF NOT EXISTS safeguard_hosted_hazard_assessment (assessment_id varchar(64) primary key, payload text not null, created_time timestamp with time zone not null default now())");
    }

    @Override public void save(HostedAssessmentResponse assessment) {
        try {
            jdbc.update("INSERT INTO safeguard_hosted_hazard_assessment(assessment_id,payload) VALUES (?,?)", assessment.assessmentId(), mapper.writeValueAsString(assessment));
        } catch (Exception exception) {
            throw new IllegalStateException("保存 hosted 法规评估失败", exception);
        }
    }

    @Override public HostedAssessmentResponse find(String assessmentId) {
        return jdbc.query("SELECT payload FROM safeguard_hosted_hazard_assessment WHERE assessment_id=?", rs -> {
            if (!rs.next()) return null;
            try { return mapper.readValue(rs.getString(1), HostedAssessmentResponse.class); }
            catch (Exception exception) { throw new IllegalStateException("读取 hosted 法规评估失败", exception); }
        }, assessmentId);
    }
}
