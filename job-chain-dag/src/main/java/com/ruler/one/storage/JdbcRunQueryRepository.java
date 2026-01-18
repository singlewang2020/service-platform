package com.ruler.one.storage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRunQueryRepository implements RunQueryRepository {

    private final JdbcTemplate jdbc;

    public JdbcRunQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RunRow> findRun(String runId) {
        var list = jdbc.query(
                "select run_id, chain_id, job_id, job_name, status, dag_json as dag_json, created_at, updated_at from job_run where run_id=?",
                (rs, rowNum) -> new RunRow(
                        rs.getString("run_id"),
                        rs.getString("chain_id"),
                        rs.getString("job_id"),
                        rs.getString("job_name"),
                        rs.getString("status"),
                        rs.getString("dag_json"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                runId);
        return list.stream().findFirst();
    }

    @Override
    public List<NodeRow> listNodes(String runId) {
        return jdbc.query(
                "select run_id, node_id, status, attempt, last_error, artifact_json as artifact_json, started_at, ended_at, updated_at from job_run_node where run_id=? order by node_id",
                (rs, rowNum) -> new NodeRow(
                        rs.getString("run_id"),
                        rs.getString("node_id"),
                        rs.getString("status"),
                        rs.getInt("attempt"),
                        rs.getString("last_error"),
                        rs.getString("artifact_json"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("ended_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                runId);
    }
}
