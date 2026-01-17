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
        // upsert
        String sql = """
            insert into job_run (run_id, job_name, status, dag_json, created_at, updated_at)
            values (?, ?, 'RUNNING', ?::jsonb, now(), now())
            on conflict (run_id) do nothing
        """;
        jdbcTemplate.update(sql, runId, jobName, dagJson);
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
        String sql = """
            insert into job_run_node
              (run_id, node_id, status, attempt, last_error, started_at, ended_at, updated_at)
            values
              (?, ?, ?, ?, ?,
               case when ? = 'RUNNING' then now() else null end,
               case when ? in ('SUCCESS','FAILED','SKIPPED') then now() else null end,
               now())
            on conflict (run_id, node_id) do update set
              status = excluded.status,
              attempt = excluded.attempt,
              last_error = excluded.last_error,
              started_at = coalesce(job_run_node.started_at, excluded.started_at),
              ended_at = excluded.ended_at,
              updated_at = now()
        """;
        String st = state.name();
        jdbcTemplate.update(sql, runId, nodeId, st, attempt, error, st, st);
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
        String sql = """
            insert into job_run_checkpoint (run_id, node_id, checkpoint_json, updated_at)
            values (?, ?, ?::jsonb, now())
            on conflict (run_id, node_id) do update set
              checkpoint_json = excluded.checkpoint_json,
              updated_at = now()
        """;
        jdbcTemplate.update(sql, runId, nodeId, json);
    }

    @Override
    public void saveArtifact(String runId, String nodeId, Map<String, Object> artifact) {
        String json = writeJson(artifact);
        String sql = """
            insert into job_run_node (run_id, node_id, status, attempt, artifact_json, updated_at)
            values (?, ?, 'RUNNING', 0, ?::jsonb, now())
            on conflict (run_id, node_id) do update set
              artifact_json = ?::jsonb,
              updated_at = now()
        """;
        // 注意：这里不改 status，只写 artifact；如果 node 还不存在就插入一个 RUNNING 占位
        jdbcTemplate.update(sql, runId, nodeId, json, json);
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize json", e);
        }
    }
}
