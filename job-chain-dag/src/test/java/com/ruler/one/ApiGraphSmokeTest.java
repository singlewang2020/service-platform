package com.ruler.one;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGraphSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    com.ruler.one.service.ChainService chainService;

    @Autowired
    com.ruler.one.service.ChainRunService chainRunService;

    @Autowired
    com.ruler.one.service.JobService jobService;

    @Test
    void graphEndpoints_shouldReturnNodesJobsAndEdges() {
        var j1 = jobService.create("job-api-1", "", "print", "{\"msg\":\"hello\"}", true);
        var j2 = jobService.create("job-api-2", "", "print", "{\"msg\":\"world\"}", true);

        String dagJson = """
                {
                  "job":"demo",
                  "nodes":[
                    {"id":"n1","jobId":"%s","dependsOn":[],"retry":{"maxAttempts":1,"backoffMillis":0,"backoffMultiplier":1}},
                    {"id":"n2","jobId":"%s","dependsOn":["n1"],"retry":{"maxAttempts":1,"backoffMillis":0,"backoffMultiplier":1}}
                  ]
                }
                """.formatted(j1.jobId(), j2.jobId());

        var chain = chainService.create("chain-api-graph", "desc", dagJson, true);
        String runId = chainRunService.startChain(chain.chainId());

        // chain graph
        var chainGraphResp = rest.getForEntity(url("/api/v1/chains/" + chain.chainId() + "/graph"), String.class);
        assertThat(chainGraphResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chainGraphResp.getBody()).contains("\"edges\"");
        assertThat(chainGraphResp.getBody()).contains("job-api-1");
        assertThat(chainGraphResp.getBody()).contains("job-api-2");

        // run graph
        var runGraphResp = rest.getForEntity(url("/api/v1/runs/" + runId + "/graph"), String.class);
        assertThat(runGraphResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(runGraphResp.getBody()).contains("\"edges\"");
        assertThat(runGraphResp.getBody()).contains("job-api-1");
        assertThat(runGraphResp.getBody()).contains("job-api-2");

        // run detail should include chainId traceability
        var runDetail = rest.getForEntity(url("/api/v1/runs/" + runId), String.class);
        assertThat(runDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(runDetail.getBody()).contains(chain.chainId());

        // NOTE: don't call /api/v1/jobs in this smoke test; keep it focused on graph endpoints.
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
