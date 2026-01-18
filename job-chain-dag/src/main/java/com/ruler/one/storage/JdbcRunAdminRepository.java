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
        int c = jdbc.update("update job_run set status=?, updated_at=current_timestamp where run_id=?", status, runId);
        return c > 0;
    }

    @Override
    public Optional<NodeRow> findNode(String runId, String nodeId) {
        var list = jdbc.query(
                "select run_id, node_id, status, attempt, last_error, artifact_json as artifact_json from job_run_node where run_id=? and node_id=?",
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
        int updated = jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "update job_run_node set status=?, attempt=?, last_error=?, artifact_json=?, updated_at=current_timestamp where run_id=? and node_id=?"
            );
            ps.setString(1, status);
            ps.setInt(2, attempt);
            ps.setString(3, lastError);
            DbJson.setJsonbOrString(ps, 4, artifactJson);
            ps.setString(5, runId);
            ps.setString(6, nodeId);
            return ps;
        });

        if (updated <= 0) {
            // 2) fallback insert
            try {
                jdbc.update(con -> {
                    var ps = con.prepareStatement(
                            "insert into job_run_node (run_id, node_id, status, attempt, last_error, artifact_json, updated_at) values (?, ?, ?, ?, ?, ?, current_timestamp)"
                    );
                    ps.setString(1, runId);
                    ps.setString(2, nodeId);
                    ps.setString(3, status);
                    ps.setInt(4, attempt);
                    ps.setString(5, lastError);
                    DbJson.setJsonbOrString(ps, 6, artifactJson);
                    return ps;
                });
            } catch (Exception e) {
                // concurrent insert, ignore
            }
        }

        return findNode(runId, nodeId).orElseThrow();
    }
}
