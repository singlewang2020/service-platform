package com.ruler.one.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruler.one.api.dto.RunDtos;
import com.ruler.one.api.dto.RunGraphDtos;
import com.ruler.one.api.dto.RunQueryDtos;
import com.ruler.one.service.RunQueryService;
import com.ruler.one.service.RunService;

@RestController
@RequestMapping("/api/v1")
public class RunController {

    private final RunService service;
    private final RunQueryService queryService;

    public RunController(RunService service, RunQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/runs/{runId}")
    public RunQueryDtos.RunDetailResponse getRun(@PathVariable String runId) {
        return queryService.getRun(runId);
    }

    @GetMapping("/runs/{runId}/graph")
    public RunGraphDtos.RunGraphResponse getRunGraph(@PathVariable String runId) {
        return queryService.getRunGraph(runId);
    }

    @GetMapping("/runs/{runId}/nodes")
    public java.util.List<RunQueryDtos.NodeRowResponse> listNodes(@PathVariable String runId) {
        return queryService.listNodes(runId);
    }

    @PostMapping("/runs/{runId}:stop")
    public RunDtos.StopRunResponse stop(@PathVariable String runId) {
        String status = service.stop(runId);
        return new RunDtos.StopRunResponse(runId, status);
    }

    @PostMapping("/runs/{runId}/nodes/{nodeId}:retry")
    public RunDtos.NodeActionResponse retryNode(
            @PathVariable String runId,
            @PathVariable String nodeId,
            @Valid @RequestBody(required = false) RunDtos.NodeActionRequest body
    ) {
        var r = service.retryNode(runId, nodeId,
                body == null ? null : body.artifactJson(),
                body == null ? null : body.reason());
        return new RunDtos.NodeActionResponse(r.runId(), r.nodeId(), r.status(), r.attempt());
    }

    @PostMapping("/runs/{runId}/nodes/{nodeId}:complete")
    public RunDtos.NodeActionResponse completeNode(
            @PathVariable String runId,
            @PathVariable String nodeId,
            @Valid @RequestBody(required = false) RunDtos.NodeActionRequest body
    ) {
        var r = service.completeNode(runId, nodeId,
                body == null ? null : body.artifactJson(),
                body == null ? null : body.reason());
        return new RunDtos.NodeActionResponse(r.runId(), r.nodeId(), r.status(), r.attempt());
    }
}
