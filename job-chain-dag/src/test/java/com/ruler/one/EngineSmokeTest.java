package com.ruler.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ruler.one.api.dto.ChainDtos;
import com.ruler.one.api.dto.RunQueryDtos;
import com.ruler.one.service.ChainService;
import com.ruler.one.service.ChainRunService;
import com.ruler.one.service.RunQueryService;

@SpringBootTest
@ActiveProfiles("test")
class EngineSmokeTest {

    @Autowired
    ChainService chainService;

    @Autowired
    ChainRunService chainRunService;

    @Autowired
    RunQueryService runQueryService;

    @Test
    void startChain_shouldEventuallySucceed_andWriteNodeArtifacts() {
        String dagJson = """
                {
                  "job":"demo",
                  "nodes":[
                    {"id":"n1","type":"print","dependsOn":[],"cfg":{"k":"v"},"retry":{"maxAttempts":1,"backoff":0}},
                    {"id":"n2","type":"print","dependsOn":["n1"],"cfg":{"a":1},"retry":{"maxAttempts":1,"backoff":0}}
                  ]
                }
                """;

        var chain = chainService.create("chain-engine-smoke", "desc", dagJson, true);
        String runId = chainRunService.startChain(chain.chainId());

        awaitRunStatus(runId, "SUCCESS", Duration.ofSeconds(5));

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
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(last).isEqualTo(expected);
    }
}

