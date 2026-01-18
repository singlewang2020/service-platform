package com.ruler.one.storage;

import java.util.Optional;

public interface RunAdminRepository {

    Optional<String> findRunStatus(String runId);

    boolean updateRunStatus(String runId, String status);

    Optional<NodeRow> findNode(String runId, String nodeId);

    NodeRow upsertNode(String runId, String nodeId, String status, int attempt, String lastError, String artifactJson);

    record NodeRow(
            String runId,
            String nodeId,
            String status,
            int attempt,
            String lastError,
            String artifactJson
    ) {}
}

