package com.ruler.one.service;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.ruler.one.api.dto.RunQueryDtos;
import com.ruler.one.storage.RunQueryRepository;

@Service
public class RunQueryService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RunQueryRepository repo;

    public RunQueryService(RunQueryRepository repo) {
        this.repo = repo;
    }

    public RunQueryDtos.RunDetailResponse getRun(String runId) {
        var r = repo.findRun(runId).orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        return new RunQueryDtos.RunDetailResponse(
                r.runId(),
                r.jobName(),
                r.status(),
                r.dagJson(),
                r.createdAt() == null ? null : ISO.format(r.createdAt()),
                r.updatedAt() == null ? null : ISO.format(r.updatedAt())
        );
    }

    public java.util.List<RunQueryDtos.NodeRowResponse> listNodes(String runId) {
        return repo.listNodes(runId).stream().map(n -> new RunQueryDtos.NodeRowResponse(
                n.runId(),
                n.nodeId(),
                n.status(),
                n.attempt(),
                n.lastError(),
                n.artifactJson(),
                n.startedAt() == null ? null : ISO.format(n.startedAt()),
                n.endedAt() == null ? null : ISO.format(n.endedAt()),
                n.updatedAt() == null ? null : ISO.format(n.updatedAt())
        )).toList();
    }
}

