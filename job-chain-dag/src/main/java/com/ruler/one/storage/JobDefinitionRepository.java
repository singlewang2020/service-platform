package com.ruler.one.storage;

import java.util.List;
import java.util.Optional;

import com.ruler.one.model.JobDefinition;

public interface JobDefinitionRepository {
    JobDefinition insert(JobDefinition job);

    JobDefinition update(JobDefinition job);

    boolean deleteById(String jobId);

    Optional<JobDefinition> findById(String jobId);

    Optional<JobDefinition> findByName(String name);

    List<JobDefinition> page(int offset, int limit, String keyword, Boolean enabled);

    long count(String keyword, Boolean enabled);
}

