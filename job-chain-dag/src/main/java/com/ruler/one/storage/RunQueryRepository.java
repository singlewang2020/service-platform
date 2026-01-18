package com.ruler.one.storage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RunQueryRepository {

    Optional<RunRow> findRun(String runId);

    List<NodeRow> listNodes(String runId);

    record RunRow(
            String runId,
            String jobName,
            String status,
            String dagJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    record NodeRow(
            String runId,
            String nodeId,
            String status,
            int attempt,
            String lastError,
            String artifactJson,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            OffsetDateTime updatedAt
    ) {}
}

