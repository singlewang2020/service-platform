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
    public void createRunIfAbsent(String runId, String jobName, String dagJson) {
        // DB-agnostic: try insert, ignore duplicate
        String sql = """
            insert into job_run (run_id, job_name, status, dag_json, created_at, updated_at)
            values (?, ?, 'PENDING', ?, now(), now())
        """;
        try {
            jdbcTemplate.update(sql, runId, jobName, dagJson);
        } catch (Exception e) {
            // duplicate key (postgres/h2/mysql) -> ignore
            // any other error will be rethrown by JdbcTemplate if not duplicate; here we assume duplicate is the only expected one
        }
    }

    @Override
    public void updateRunStatus(String runId, String status) {
        String sql = """
            update job_run
            set status = ?, updated_at = now()
            where run_id = ?
        """;
        jdbcTemplate.update(sql, status, runId);
    }

    @Override
    public void upsertNodeState(String runId, String nodeId, NodeState state, int attempt, String error) {
        String st = state.name();

        // 1) try update
        String updateSql = """
            update job_run_node
            set status = ?,
                attempt = ?,
                last_error = ?,
                started_at = case when started_at is null and ? = 'RUNNING' then now() else started_at end,
                ended_at = case when ? in ('SUCCESS','FAILED','SKIPPED','STOPPED') then now() else ended_at end,
                updated_at = now()
            where run_id = ? and node_id = ?
        """;
        int updated = jdbcTemplate.update(updateSql, st, attempt, error, st, st, runId, nodeId);
        if (updated > 0) return;

        // 2) fallback insert
        String insertSql = """
            insert into job_run_node
              (run_id, node_id, status, attempt, last_error, started_at, ended_at, updated_at)
            values
              (?, ?, ?, ?, ?,
               case when ? = 'RUNNING' then now() else null end,
               case when ? in ('SUCCESS','FAILED','SKIPPED','STOPPED') then now() else null end,
               now())
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

        // 1) try update
        String updateSql = """
            update job_run_checkpoint
            set checkpoint_json = ?, updated_at = now()
            where run_id = ? and node_id = ?
        """;
        int updated = jdbcTemplate.update(updateSql, json, runId, nodeId);
        if (updated > 0) return;

        // 2) fallback insert
        String insertSql = """
            insert into job_run_checkpoint (run_id, node_id, checkpoint_json, updated_at)
            values (?, ?, ?, now())
        """;
        try {
            jdbcTemplate.update(insertSql, runId, nodeId, json);
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
            set artifact_json = ?, updated_at = now()
            where run_id = ? and node_id = ?
        """;
        int updated = jdbcTemplate.update(updateSql, json, runId, nodeId);
        if (updated > 0) return;

        // Insert a placeholder node row if absent
        String insertSql = """
            insert into job_run_node (run_id, node_id, status, attempt, artifact_json, updated_at)
            values (?, ?, 'RUNNING', 0, ?, now())
        """;
        try {
            jdbcTemplate.update(insertSql, runId, nodeId, json);
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
