package com.ruler.one.runtime;

import java.util.HashMap;
import java.util.Map;

public class NodeResult {
    private boolean success;
    private Map<String, Object> artifact = new HashMap<>();
    private Checkpoint checkpoint;

    public NodeResult() {}

    public static NodeResult ok() {
        NodeResult r = new NodeResult();
        r.success = true;
        return r;
    }

    public static NodeResult fail() {
        NodeResult r = new NodeResult();
        r.success = false;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Map<String, Object> getArtifact() { return artifact; }
    public void setArtifact(Map<String, Object> artifact) { this.artifact = artifact; }

    public Checkpoint getCheckpoint() { return checkpoint; }
    public void setCheckpoint(Checkpoint checkpoint) { this.checkpoint = checkpoint; }
}
