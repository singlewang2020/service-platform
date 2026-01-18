package com.ruler.one.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruler.one.model.JobDefinition;
import com.ruler.one.storage.JobDefinitionRepository;
import com.ruler.one.storage.RunStorage;

@Service
public class JobService {

    private final JobDefinitionRepository repo;
    private final RunStorage runStorage;

    public JobService(JobDefinitionRepository repo, RunStorage runStorage) {
        this.repo = repo;
        this.runStorage = runStorage;
    }

    @Transactional
    public JobDefinition create(String name, String description, String type, String configJson, Boolean enabled) {
        String jobId = UUID.randomUUID().toString();
        boolean en = enabled == null || enabled;
        String cfg = (configJson == null || configJson.isBlank()) ? "{}" : configJson;
        var job = new JobDefinition(jobId, name, description, type, en, cfg, null, null);
        return repo.insert(job);
    }

    @Transactional
    public JobDefinition update(String jobId, String name, String description, String type, String configJson, Boolean enabled) {
        JobDefinition cur = repo.findById(jobId).orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));
        boolean en = enabled == null ? cur.enabled() : enabled;
        String cfg = (configJson == null || configJson.isBlank()) ? cur.configJson() : configJson;
        var job = new JobDefinition(jobId, name, description, type, en, cfg, cur.createdAt(), null);
        return repo.update(job);
    }

    @Transactional
    public boolean delete(String jobId) {
        return repo.deleteById(jobId);
    }

    public JobDefinition get(String jobId) {
        return repo.findById(jobId).orElseThrow(() -> new IllegalArgumentException("job not found: " + jobId));
    }

    public com.ruler.one.api.dto.JobDtos.PageResponse<JobDefinition> page(int page, int size, String keyword, Boolean enabled) {
        int p = Math.max(page, 1);
        int s = Math.max(Math.min(size, 200), 1);
        int offset = (p - 1) * s;
        var items = repo.page(offset, s, keyword, enabled);
        long total = repo.count(keyword, enabled);
        return new com.ruler.one.api.dto.JobDtos.PageResponse<>(p, s, total, items);
    }

    @Transactional
    public JobDefinition enable(String jobId) {
        JobDefinition cur = get(jobId);
        if (cur.enabled()) return cur;
        return repo.update(new JobDefinition(cur.jobId(), cur.name(), cur.description(), cur.type(), true, cur.configJson(), cur.createdAt(), null));
    }

    @Transactional
    public JobDefinition disable(String jobId) {
        JobDefinition cur = get(jobId);
        if (!cur.enabled()) return cur;
        return repo.update(new JobDefinition(cur.jobId(), cur.name(), cur.description(), cur.type(), false, cur.configJson(), cur.createdAt(), null));
    }

    /**
     * v0.0: 创建一条 job_run 记录（status=RUNNING），后续再接入真正的 engine 执行。
     */
    @Transactional
    public String start(String jobId) {
        JobDefinition job = get(jobId);
        if (!job.enabled()) {
            throw new IllegalStateException("job is disabled: " + jobId);
        }

        String runId = UUID.randomUUID().toString();
        // 目前还没有 chain 的运行快照，这里先写一个空 dag_json，保证 stop/retry/complete 有 run 可关联。
        runStorage.createRunIfAbsent(runId, job.name(), "{}");
        return runId;
    }
}
