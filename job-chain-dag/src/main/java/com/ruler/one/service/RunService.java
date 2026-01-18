package com.ruler.one.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruler.one.storage.RunAdminRepository;

@Service
public class RunService {

    private final RunAdminRepository repo;

    public RunService(RunAdminRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public String stop(String runId) {
        String status = repo.findRunStatus(runId).orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));

        if (Objects.equals(status, "SUCCESS") || Objects.equals(status, "FAILED")) {
            throw new IllegalStateException("run already finished: " + status);
        }

        // 引擎会识别 STOPPING/STOPPED 并尽快结束
        boolean ok = repo.updateRunStatus(runId, "STOPPING");
        if (!ok) throw new IllegalArgumentException("run not found: " + runId);
        return "STOPPING";
    }

    @Transactional
    public RunAdminRepository.NodeRow retryNode(String runId, String nodeId, String artifactJson, String reason) {
        RunAdminRepository.NodeRow cur = repo.findNode(runId, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("node not found: runId=" + runId + ", nodeId=" + nodeId));

        if (!("FAILED".equals(cur.status()) || "STOPPED".equals(cur.status()))) {
            throw new IllegalStateException("node status not retryable: " + cur.status());
        }

        int nextAttempt = cur.attempt() + 1;
        String err = reason;
        String art = artifactJson == null ? cur.artifactJson() : artifactJson;
        return repo.upsertNode(runId, nodeId, "RETRYING", nextAttempt, err, art);
    }

    @Transactional
    public RunAdminRepository.NodeRow completeNode(String runId, String nodeId, String artifactJson, String reason) {
        RunAdminRepository.NodeRow cur = repo.findNode(runId, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("node not found: runId=" + runId + ", nodeId=" + nodeId));

        if (!("FAILED".equals(cur.status()) || "STOPPED".equals(cur.status()) || "RUNNING".equals(cur.status()))) {
            throw new IllegalStateException("node status not completable: " + cur.status());
        }

        String err = reason;
        String art = artifactJson == null ? cur.artifactJson() : artifactJson;
        return repo.upsertNode(runId, nodeId, "SUCCESS", cur.attempt(), err, art);
    }
}
