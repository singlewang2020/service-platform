package com.ruler.one.storage;

import java.util.List;
import java.util.Optional;

import com.ruler.one.model.JobChainDefinition;

public interface JobChainDefinitionRepository {

    JobChainDefinition insert(JobChainDefinition chain);

    /**
     * 乐观锁更新：仅当 version 匹配时更新并 version+1。
     */
    Optional<JobChainDefinition> updateWithVersion(JobChainDefinition chain, long expectedVersion);

    Optional<JobChainDefinition> setEnabled(String chainId, boolean enabled);

    Optional<JobChainDefinition> findById(String chainId);

    Optional<JobChainDefinition> findByName(String name);

    List<JobChainDefinition> page(int offset, int limit, String keyword, Boolean enabled);

    long count(String keyword, Boolean enabled);
}
