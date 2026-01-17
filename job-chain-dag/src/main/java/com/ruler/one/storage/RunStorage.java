package com.ruler.one.storage;

import com.ruler.one.model.NodeState;
import com.ruler.one.runtime.Checkpoint;

import java.util.Map;
import java.util.Optional;

public interface RunStorage {
    void createRunIfAbsent(String runId, String jobName, String dagJson);
    void updateRunStatus(String runId, String status);

    void upsertNodeState(String runId, String nodeId, NodeState state, int attempt, String error);

    Optional<Checkpoint> loadCheckpoint(String runId, String nodeId);
    void saveCheckpoint(String runId, String nodeId, Checkpoint checkpoint);

    void saveArtifact(String runId, String nodeId, Map<String, Object> artifact);
}
