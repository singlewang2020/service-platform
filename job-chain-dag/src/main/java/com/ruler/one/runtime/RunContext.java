package com.ruler.one.runtime;

import java.util.HashMap;
import java.util.Map;

public class RunContext {
    private String runId;
    private String jobName;

    // 用于跨节点传递 artifact（也可以每次从 storage 取，这里先内存版）
    private Map<String, Map<String, Object>> nodeArtifacts = new HashMap<>();

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public void putArtifact(String nodeId, Map<String, Object> artifact) {
        nodeArtifacts.put(nodeId, artifact);
    }

    public Map<String, Object> getArtifact(String nodeId) {
        return nodeArtifacts.get(nodeId);
    }
}
