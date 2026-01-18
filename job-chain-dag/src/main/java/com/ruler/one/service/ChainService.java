package com.ruler.one.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruler.one.api.dto.JobDtos;
import com.ruler.one.model.JobChainDefinition;
import com.ruler.one.storage.JobChainDefinitionRepository;

@Service
public class ChainService {

    private final JobChainDefinitionRepository repo;
    private final DagValidator dagValidator;

    public ChainService(JobChainDefinitionRepository repo, DagValidator dagValidator) {
        this.repo = repo;
        this.dagValidator = dagValidator;
    }

    public JobChainDefinition get(String chainId) {
        return repo.findById(chainId).orElseThrow(() -> new IllegalArgumentException("chain not found: " + chainId));
    }

    public JobDtos.PageResponse<JobChainDefinition> page(int page, int size, String keyword, Boolean enabled) {
        int p = Math.max(page, 1);
        int s = Math.max(Math.min(size, 200), 1);
        int offset = (p - 1) * s;
        var items = repo.page(offset, s, keyword, enabled);
        long total = repo.count(keyword, enabled);
        return new JobDtos.PageResponse<>(p, s, total, items);
    }

    @Transactional
    public JobChainDefinition create(String name, String description, String dagJson, Boolean enabled) {
        dagValidator.validate(dagJson);

        repo.findByName(name).ifPresent(x -> {
            throw new IllegalArgumentException("chain name already exists: " + name);
        });

        String chainId = UUID.randomUUID().toString();
        boolean en = enabled == null || enabled;
        long version = 1;
        return repo.insert(new JobChainDefinition(chainId, name, description, en, version, dagJson, null, null));
    }

    /**
     * 乐观锁更新：客户端必须带上当前 version。
     */
    @Transactional
    public JobChainDefinition update(String chainId, String name, String description, String dagJson, long expectedVersion) {
        dagValidator.validate(dagJson);

        // name 唯一：允许更新为自己同名，但不允许占用别人
        repo.findByName(name).ifPresent(existing -> {
            if (!existing.chainId().equals(chainId)) {
                throw new IllegalArgumentException("chain name already exists: " + name);
            }
        });

        JobChainDefinition cur = get(chainId);
        var next = new JobChainDefinition(chainId, name, description, cur.enabled(), cur.version(), dagJson, cur.createdAt(), null);

        return repo.updateWithVersion(next, expectedVersion)
                .orElseThrow(() -> new IllegalStateException("chain version conflict or not found"));
    }

    @Transactional
    public JobChainDefinition enable(String chainId) {
        return repo.setEnabled(chainId, true)
                .orElseThrow(() -> new IllegalArgumentException("chain not found: " + chainId));
    }

    @Transactional
    public JobChainDefinition disable(String chainId) {
        return repo.setEnabled(chainId, false)
                .orElseThrow(() -> new IllegalArgumentException("chain not found: " + chainId));
    }
}
