package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists hosted visual context so assessment is safe across process restarts. */
@Repository
public class JdbcHostedVisualRunRepository implements HostedVisualRunRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcHostedVisualRunRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        jdbc.execute("CREATE TABLE IF NOT EXISTS safeguard_hosted_visual_run (run_id varchar(64) primary key, payload text not null, created_time timestamp with time zone not null)");
    }

    @Override public void save(HostedVisualRun run) {
        try {
            jdbc.update("INSERT INTO safeguard_hosted_visual_run(run_id,payload,created_time) VALUES (?,?,?)", run.runId(), mapper.writeValueAsString(run), Timestamp.from(run.createdAt()));
        } catch (Exception exception) {
            throw new IllegalStateException("保存 hosted 视觉研判失败", exception);
        }
    }

    @Override public HostedVisualRun find(String runId) {
        return jdbc.query("SELECT payload FROM safeguard_hosted_visual_run WHERE run_id=?", rs -> {
            if (!rs.next()) return null;
            try { return mapper.readValue(rs.getString(1), HostedVisualRun.class); }
            catch (Exception exception) { throw new IllegalStateException("读取 hosted 视觉研判失败", exception); }
        }, runId);
    }
}
