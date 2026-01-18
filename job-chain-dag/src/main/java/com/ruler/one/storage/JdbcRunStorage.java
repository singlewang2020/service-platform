package com.ruler.one.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.model.NodeState;
import com.ruler.one.runtime.Checkpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcRunStorage implements RunStorage {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRunStorage(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createRunIfAbsent(String runId, String chainId, String jobId, String jobName, String dagJson) {
        try {
            jdbcTemplate.update(con -> {
                var ps = con.prepareStatement(
                        "insert into job_run (run_id, chain_id, job_id, job_name, status, dag_json, created_at, updated_at) " +
                                "values (?, ?, ?, ?, 'PENDING', ?, current_timestamp, current_timestamp)"
                );
                ps.setString(1, runId);
                ps.setString(2, chainId);
                ps.setString(3, jobId);
                ps.setString(4, jobName);
                DbJson.setJsonbOrString(ps, 5, dagJson);
                return ps;
            });
        } catch (Exception e) {
            // duplicate key -> ignore
        }
    }

    @Override
    public void updateRunStatus(String runId, String status) {
        String sql = """
            update job_run
            set status = ?, updated_at = current_timestamp
            where run_id = ?
        """;
        jdbcTemplate.update(sql, status, runId);
    }

    @Override
    public void upsertNodeState(String runId, String nodeId, NodeState state, int attempt, String error) {
        String st = state.name();

        String updateSql = """
            update job_run_node
            set status = ?,
                attempt = ?,
                last_error = ?,
                started_at = case when started_at is null and ? = 'RUNNING' then current_timestamp else started_at end,
                ended_at = case when ? in ('SUCCESS','FAILED','SKIPPED','STOPPED') then current_timestamp else ended_at end,
                updated_at = current_timestamp
            where run_id = ? and node_id = ?
        """;
        int updated = jdbcTemplate.update(updateSql, st, attempt, error, st, st, runId, nodeId);
        if (updated > 0) return;

        String insertSql = """
            insert into job_run_node
              (run_id, node_id, status, attempt, last_error, started_at, ended_at, updated_at)
            values
              (?, ?, ?, ?, ?,
               case when ? = 'RUNNING' then current_timestamp else null end,
               case when ? in ('SUCCESS','FAILED','SKIPPED','STOPPED') then current_timestamp else null end,
               current_timestamp)
        """;
        try {
            jdbcTemplate.update(insertSql, runId, nodeId, st, attempt, error, st, st);
        } catch (Exception e) {
            // concurrent insert: ignore
        }
    }

    @Override
    public Optional<Checkpoint> loadCheckpoint(String runId, String nodeId) {
        String sql = """
            select checkpoint_json
            from job_run_checkpoint
            where run_id = ? and node_id = ?
        """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) return Optional.empty();
            String json = rs.getString(1);
            try {
                Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                Checkpoint cp = new Checkpoint();
                cp.setData(map);
                return Optional.of(cp);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize checkpoint json", e);
            }
        }, runId, nodeId);
    }

    @Override
    public void saveCheckpoint(String runId, String nodeId, Checkpoint checkpoint) {
        String json = writeJson(checkpoint.getData());

        String updateSql = """
            update job_run_checkpoint
            set checkpoint_json = ?, updated_at = current_timestamp
            where run_id = ? and node_id = ?
        """;
        int updated = jdbcTemplate.update(updateSql, json, runId, nodeId);
        if (updated > 0) return;

        try {
            jdbcTemplate.update(con -> {
                var ps = con.prepareStatement(
                        "insert into job_run_checkpoint (run_id, node_id, checkpoint_json, updated_at) values (?, ?, ?, current_timestamp)"
                );
                ps.setString(1, runId);
                ps.setString(2, nodeId);
                DbJson.setJsonbOrString(ps, 3, json);
                return ps;
            });
        } catch (Exception e) {
            // concurrent insert: ignore
        }
    }

    @Override
    public void saveArtifact(String runId, String nodeId, Map<String, Object> artifact) {
        String json = writeJson(artifact);

        // Update artifact if node exists
        String updateSql = """
            update job_run_node
            set artifact_json = ?, updated_at = current_timestamp
            where run_id = ? and node_id = ?
        """;
        int updated = jdbcTemplate.update(updateSql, json, runId, nodeId);
        if (updated > 0) return;

        // Insert a placeholder node row if absent
        try {
            jdbcTemplate.update(con -> {
                var ps = con.prepareStatement(
                        "insert into job_run_node (run_id, node_id, status, attempt, artifact_json, updated_at) " +
                                "values (?, ?, 'RUNNING', 0, ?, current_timestamp)"
                );
                ps.setString(1, runId);
                ps.setString(2, nodeId);
                DbJson.setJsonbOrString(ps, 3, json);
                return ps;
            });
        } catch (Exception e) {
            // concurrent insert: ignore
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize json", e);
        }
    }
}
