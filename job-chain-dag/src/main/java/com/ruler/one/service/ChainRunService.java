package com.ruler.one.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.engine.DagEngine;
import com.ruler.one.model.DagDef;
import com.ruler.one.model.NodeDef;
import com.ruler.one.model.NodeState;
import com.ruler.one.model.JobChainDefinition;
import com.ruler.one.storage.JobChainDefinitionRepository;
import com.ruler.one.storage.RunStorage;

@Service
public class ChainRunService {

    private final JobChainDefinitionRepository chainRepo;
    private final RunStorage runStorage;
    private final ObjectMapper objectMapper;
    private final DagEngine dagEngine;
    private final EngineRunner engineRunner;

    public ChainRunService(
            JobChainDefinitionRepository chainRepo,
            RunStorage runStorage,
            ObjectMapper objectMapper,
            DagEngine dagEngine,
            EngineRunner engineRunner
    ) {
        this.chainRepo = chainRepo;
        this.runStorage = runStorage;
        this.objectMapper = objectMapper;
        this.dagEngine = dagEngine;
        this.engineRunner = engineRunner;
    }

    /**
     * 从 chain 定义创建一次 run，并初始化每个节点的 job_run_node 记录；然后异步启动 DAG 引擎执行。
     */
    @Transactional
    public String startChain(String chainId) {
        JobChainDefinition chain = chainRepo.findById(chainId)
                .orElseThrow(() -> new IllegalArgumentException("chain not found: " + chainId));

        if (!chain.enabled()) {
            throw new IllegalStateException("chain is disabled: " + chainId);
        }

        String runId = UUID.randomUUID().toString();

        DagDef dag = readDag(chain.dagJson());
        dag.setJob(chain.name());

        // 写入 run（PENDING）并保存 dag 快照（同时写入 chain_id 便于追踪）
        runStorage.createRunIfAbsent(runId, chain.chainId(), null, chain.name(), chain.dagJson());

        // 初始化节点状态为 PENDING
        if (dag.getNodes() != null) {
            for (NodeDef n : dag.getNodes()) {
                if (n.getId() == null || n.getId().isBlank()) continue;
                runStorage.upsertNodeState(runId, n.getId(), NodeState.PENDING, 0, null);
            }
        }

        // 异步执行：避免阻塞 API
        engineRunner.executor().execute(() -> dagEngine.run(runId, dag));

        return runId;
    }

    private DagDef readDag(String dagJson) {
        try {
            String s = dagJson;
            // 防御：如果 dag_json 被“双重编码”为 JSON 字符串（例如 "{...}"），先解一层
            if (s != null) {
                String t = s.trim();
                if (t.startsWith("\"") && t.endsWith("\"")) {
                    s = objectMapper.readValue(t, String.class);
                }
            }
            return objectMapper.readValue(s, DagDef.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid dag_json", e);
        }
    }
}
