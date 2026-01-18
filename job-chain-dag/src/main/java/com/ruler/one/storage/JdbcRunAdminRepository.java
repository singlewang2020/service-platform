package com.ruler.one.storage;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRunAdminRepository implements RunAdminRepository {

    private final JdbcTemplate jdbc;

    public JdbcRunAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findRunStatus(String runId) {
        var list = jdbc.query("select status from job_run where run_id=?", (rs, rowNum) -> rs.getString(1), runId);
        return list.stream().findFirst();
    }

    @Override
    public boolean updateRunStatus(String runId, String status) {
        int c = jdbc.update("update job_run set status=?, updated_at=now() where run_id=?", status, runId);
        return c > 0;
    }

    @Override
    public Optional<NodeRow> findNode(String runId, String nodeId) {
        var list = jdbc.query(
                "select run_id, node_id, status, attempt, last_error, artifact_json::jsonb as artifact_json from job_run_node where run_id=? and node_id=?",
                (rs, rowNum) -> new NodeRow(
                        rs.getString("run_id"),
                        rs.getString("node_id"),
                        rs.getString("status"),
                        rs.getInt("attempt"),
                        rs.getString("last_error"),
                        rs.getString("artifact_json")
                ),
                runId,
                nodeId);
        return list.stream().findFirst();
    }

    @Override
    public NodeRow upsertNode(String runId, String nodeId, String status, int attempt, String lastError, String artifactJson) {
        // 1) try update
        String updateSql = """
                update job_run_node
                set status=?,
                    attempt=?,
                    last_error=?,
                    artifact_json=?::jsonb,
                    updated_at=now()
                where run_id=? and node_id=?
                """;
        int updated = jdbc.update(updateSql, status, attempt, lastError, artifactJson, runId, nodeId);

        if (updated <= 0) {
            // 2) fallback insert
            String insertSql = """
                    insert into job_run_node (run_id, node_id, status, attempt, last_error, artifact_json, updated_at)
                    values (?, ?, ?, ?, ?, ?::jsonb, now())
                    """;
            try {
                jdbc.update(insertSql, runId, nodeId, status, attempt, lastError, artifactJson);
            } catch (Exception e) {
                // concurrent insert, ignore
            }
        }

        return findNode(runId, nodeId).orElseThrow();
    }
}
