package com.ruler.one.engine;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.model.JobDefinition;
import com.ruler.one.plugins.NodeExecutor;
import com.ruler.one.runtime.NodeContext;
import com.ruler.one.runtime.NodeResult;
import com.ruler.one.storage.JobDefinitionRepository;

/**
 * B 方案配套：DAG 节点引用 jobId 时，通过 JobDefinition 决定实际执行器与配置。
 */
@Component
public class JobBackedExecutor {

    private final JobDefinitionRepository jobRepo;
    private final ExecutorRegistry registry;
    private final ObjectMapper objectMapper;

    public JobBackedExecutor(JobDefinitionRepository jobRepo, ExecutorRegistry registry, ObjectMapper objectMapper) {
        this.jobRepo = jobRepo;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public NodeResult executeJob(NodeContext ctx, String jobId) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));

        if (!job.enabled()) {
            throw new IllegalStateException("job is disabled: " + jobId);
        }

        String type = job.type();
        Map<String, Object> cfg = parseJsonMap(job.configJson());

        NodeExecutor ex = registry.get(type);
        try {
            return ex.execute(ctx, cfg);
        } catch (Exception e) {
            throw new RuntimeException("job execution failed: jobId=" + jobId + ", type=" + type, e);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid job.config_json", e);
        }
    }
}
