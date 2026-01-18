package com.ruler.one.engine;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.model.DagDef;
import com.ruler.one.model.NodeDef;
import com.ruler.one.model.NodeState;
import com.ruler.one.model.RetryPolicy;
import com.ruler.one.plugins.NodeExecutor;
import com.ruler.one.runtime.NodeContext;
import com.ruler.one.runtime.RunContext;
import com.ruler.one.storage.RunQueryRepository;
import com.ruler.one.storage.RunStorage;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DagEngine {

    private final RunStorage storage;
    private final RunQueryRepository runQueryRepository;
    private final ExecutorRegistry registry;
    private final ObjectMapper objectMapper;

    public DagEngine(RunStorage storage, RunQueryRepository runQueryRepository, ExecutorRegistry registry, ObjectMapper objectMapper) {
        this.storage = storage;
        this.runQueryRepository = runQueryRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public void run(String runId, DagDef dag) {
        String dagJson = writeJson(dag);
        storage.createRunIfAbsent(runId, dag.getJob(), dagJson);

        RunContext runContext = new RunContext();
        runContext.setRunId(runId);
        runContext.setJobName(dag.getJob());

        List<NodeDef> ordered = topoSort(dag);

        // 记录每个节点最终状态（也可每次从 DB 查，先内存版）
        Map<String, NodeState> done = new ConcurrentHashMap<>();
        for (NodeDef n : ordered) done.put(n.getId(), NodeState.PENDING);

        try {
            // run 开始执行
            storage.updateRunStatus(runId, "RUNNING");

            for (NodeDef node : ordered) {
                if (isStopped(runId)) {
                    storage.updateRunStatus(runId, "STOPPED");
                    storage.upsertNodeState(runId, node.getId(), NodeState.STOPPED, 0, "Run stopped");
                    return;
                }

                // 依赖未成功则跳过/或失败（这里选择跳过）
                if (!depsAllSuccess(node, done)) {
                    storage.upsertNodeState(runId, node.getId(), NodeState.SKIPPED, 0, "Dependency not SUCCESS");
                    done.put(node.getId(), NodeState.SKIPPED);
                    continue;
                }

                executeWithRetry(runContext, node, done);
                if (done.get(node.getId()) != NodeState.SUCCESS) {
                    // 严格模式：任一节点失败 -> 整个 run FAILED
                    storage.updateRunStatus(runId, "FAILED");
                    return;
                }
            }
            storage.updateRunStatus(runId, "SUCCESS");
        } catch (Exception e) {
            storage.updateRunStatus(runId, "FAILED");
            throw new RuntimeException("DAG run failed: " + runId, e);
        }
    }

    private void executeWithRetry(RunContext runContext, NodeDef node, Map<String, NodeState> done) {
        RetryPolicy rp = node.getRetry() != null ? node.getRetry() : new RetryPolicy();
        int max = Math.max(1, rp.getMaxAttempts());

        for (int attempt = 1; attempt <= max; attempt++) {
            if (isStopped(runContext.getRunId())) {
                storage.upsertNodeState(runContext.getRunId(), node.getId(), NodeState.STOPPED, attempt, "Run stopped");
                done.put(node.getId(), NodeState.STOPPED);
                return;
            }

            NodeContext ctx = new NodeContext();
            ctx.setRunContext(runContext);
            ctx.setStorage(storage);
            ctx.setNodeId(node.getId());
            ctx.setAttempt(attempt);

            storage.upsertNodeState(runContext.getRunId(), node.getId(), NodeState.RUNNING, attempt, null);

            try {
                NodeExecutor ex = registry.get(node.getType());
                var result = ex.execute(ctx, node.getCfg());

                if (result != null && result.getArtifact() != null && !result.getArtifact().isEmpty()) {
                    ctx.saveArtifact(result.getArtifact());
                }
                if (result != null && result.getCheckpoint() != null) {
                    ctx.saveCheckpoint(result.getCheckpoint());
                }

                storage.upsertNodeState(runContext.getRunId(), node.getId(), NodeState.SUCCESS, attempt, null);
                done.put(node.getId(), NodeState.SUCCESS);
                return;

            } catch (Exception ex) {
                String err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                boolean last = (attempt == max);

                storage.upsertNodeState(runContext.getRunId(), node.getId(),
                        last ? NodeState.FAILED : NodeState.RETRYING,
                        attempt, err);

                if (last) {
                    done.put(node.getId(), NodeState.FAILED);
                    return;
                }

                sleepQuietly(rp.backoffForAttempt(attempt));
            }
        }
    }

    private boolean isStopped(String runId) {
        // STOPPING/STOPPED 视为需要停止
        return runQueryRepository.findRun(runId)
                .map(r -> {
                    String st = r.status();
                    return "STOPPING".equals(st) || "STOPPED".equals(st);
                })
                .orElse(false);
    }

    private boolean depsAllSuccess(NodeDef node, Map<String, NodeState> done) {
        if (node.getDependsOn() == null || node.getDependsOn().isEmpty()) return true;
        for (String dep : node.getDependsOn()) {
            if (done.get(dep) != NodeState.SUCCESS) return false;
        }
        return true;
    }

    private List<NodeDef> topoSort(DagDef dag) {
        Map<String, NodeDef> map = new HashMap<>();
        for (NodeDef n : dag.getNodes()) map.put(n.getId(), n);

        Map<String, Integer> indeg = new HashMap<>();
        Map<String, List<String>> out = new HashMap<>();
        for (NodeDef n : dag.getNodes()) {
            indeg.put(n.getId(), 0);
            out.put(n.getId(), new ArrayList<>());
        }

        for (NodeDef n : dag.getNodes()) {
            for (String dep : n.getDependsOn()) {
                if (!map.containsKey(dep)) {
                    throw new IllegalArgumentException("Node " + n.getId() + " depends on missing node " + dep);
                }
                indeg.put(n.getId(), indeg.get(n.getId()) + 1);
                out.get(dep).add(n.getId());
            }
        }

        Deque<String> q = new ArrayDeque<>();
        for (var e : indeg.entrySet()) if (e.getValue() == 0) q.add(e.getKey());

        List<NodeDef> ordered = new ArrayList<>();
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            ordered.add(map.get(id));
            for (String nxt : out.get(id)) {
                indeg.put(nxt, indeg.get(nxt) - 1);
                if (indeg.get(nxt) == 0) q.addLast(nxt);
            }
        }

        if (ordered.size() != dag.getNodes().size()) {
            throw new IllegalArgumentException("DAG has cycle or disconnected graph");
        }
        return ordered;
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(Math.max(0, ms)); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { throw new IllegalStateException("Failed to serialize dag", e); }
    }
}
