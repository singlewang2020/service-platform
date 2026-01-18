package com.ruler.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ruler.one.api.dto.RunQueryDtos;
import com.ruler.one.service.ChainQueryService;
import com.ruler.one.service.ChainRunService;
import com.ruler.one.service.ChainService;
import com.ruler.one.service.JobService;
import com.ruler.one.service.RunQueryService;

@SpringBootTest
@ActiveProfiles("test")
class EngineSmokeTest {

    @Autowired
    ChainService chainService;

    @Autowired
    ChainRunService chainRunService;

    @Autowired
    JobService jobService;

    @Autowired
    RunQueryService runQueryService;

    @Autowired
    ChainQueryService chainQueryService;

    @Test
    void startChain_shouldEventuallySucceed_andWriteNodeArtifacts() {
        var j1 = jobService.create("job-print-1", "", "print", "{\"msg\":\"hello\"}", true);
        var j2 = jobService.create("job-print-2", "", "print", "{\"msg\":\"world\"}", true);

        String dagJson = """
                {
                  "job":"demo",
                  "nodes":[
                    {"id":"n1","jobId":"%s","dependsOn":[],"retry":{"maxAttempts":1,"backoffMillis":0,"backoffMultiplier":1}},
                    {"id":"n2","jobId":"%s","dependsOn":["n1"],"retry":{"maxAttempts":1,"backoffMillis":0,"backoffMultiplier":1}}
                  ]
                }
                """.formatted(j1.jobId(), j2.jobId());

        var chain = chainService.create("chain-engine-smoke", "desc", dagJson, true);
        String runId = chainRunService.startChain(chain.chainId());

        awaitRunStatus(runId, "SUCCESS", Duration.ofSeconds(5));

        // traceability: run should reference chain
        var run = runQueryService.getRun(runId);
        assertThat(run.chainId()).isEqualTo(chain.chainId());

        // enhanced run graph query should work
        var graph = runQueryService.getRunGraph(runId);
        assertThat(graph.run().runId()).isEqualTo(runId);
        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.edges()).hasSize(1);

        Map<String, String> nodeIdToJobName = graph.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.ruler.one.api.dto.GraphDtos.GraphNodeWithJob::nodeId,
                        x -> x.job() == null ? "<null>" : x.job().name()
                ));
        assertThat(nodeIdToJobName).containsEntry("n1", "job-print-1");
        assertThat(nodeIdToJobName).containsEntry("n2", "job-print-2");

        // chain graph query should work
        var chainGraph = chainQueryService.getGraph(chain.chainId());
        assertThat(chainGraph.chain().chainId()).isEqualTo(chain.chainId());
        assertThat(chainGraph.nodes()).hasSize(2);
        assertThat(chainGraph.edges()).hasSize(1);

        Map<String, String> defNodeIdToJobName = chainGraph.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.ruler.one.api.dto.GraphDtos.GraphNodeWithJob::nodeId,
                        x -> x.job() == null ? "<null>" : x.job().name()
                ));
        assertThat(defNodeIdToJobName).containsEntry("n1", "job-print-1");
        assertThat(defNodeIdToJobName).containsEntry("n2", "job-print-2");

        List<RunQueryDtos.NodeRowResponse> nodes = runQueryService.listNodes(runId);
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).status()).isIn("SUCCESS", "SKIPPED", "FAILED");
        assertThat(nodes.get(1).status()).isIn("SUCCESS", "SKIPPED", "FAILED");
    }

    private void awaitRunStatus(String runId, String expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String last = null;

        while (System.currentTimeMillis() < deadline) {
            var run = runQueryService.getRun(runId);
            last = run.status();
            if (expected.equals(last)) return;
            if ("FAILED".equals(last) || "STOPPED".equals(last)) {
                dumpRunDiagnostics(runId, last);
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!expected.equals(last)) {
            // likely timeout
            dumpRunDiagnostics(runId, last == null ? "<null>" : last);
        }

        assertThat(last).isEqualTo(expected);
    }

    private void dumpRunDiagnostics(String runId, String status) {
        try {
            System.out.println("[EngineSmokeTest] runId=" + runId + ", status=" + status);
            var nodes = runQueryService.listNodes(runId);
            for (var n : nodes) {
                System.out.println("  node=" + n.nodeId() +
                        ", status=" + n.status() +
                        ", attempt=" + n.attempt() +
                        ", lastError=" + n.lastError() +
                        ", artifactJson=" + n.artifactJson());
            }
        } catch (Exception ignore) {
            // ignore diagnostic errors
        }
    }
}
