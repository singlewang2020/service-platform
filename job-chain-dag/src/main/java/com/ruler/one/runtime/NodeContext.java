package com.ruler.one.runtime;

import com.ruler.one.storage.RunStorage;

import java.util.Optional;

public class NodeContext {
    private RunContext runContext;
    private RunStorage storage;

    private String nodeId;
    private int attempt;

    public RunContext getRunContext() { return runContext; }
    public void setRunContext(RunContext runContext) { this.runContext = runContext; }

    public RunStorage getStorage() { return storage; }
    public void setStorage(RunStorage storage) { this.storage = storage; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }

    public Optional<Checkpoint> loadCheckpoint() {
        return storage.loadCheckpoint(runContext.getRunId(), nodeId);
    }

    public void saveCheckpoint(Checkpoint cp) {
        storage.saveCheckpoint(runContext.getRunId(), nodeId, cp);
    }

    public void saveArtifact(java.util.Map<String,Object> artifact) {
        storage.saveArtifact(runContext.getRunId(), nodeId, artifact);
        runContext.putArtifact(nodeId, artifact);
    }
}
