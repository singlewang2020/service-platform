package com.ruler.one.api.dto;

public final class RunQueryDtos {
    private RunQueryDtos() {}

    public record RunDetailResponse(
            String runId,
            String jobName,
            String status,
            String dagJson,
            String createdAt,
            String updatedAt
    ) {}

    public record NodeRowResponse(
            String runId,
            String nodeId,
            String status,
            int attempt,
            String lastError,
            String artifactJson,
            String startedAt,
            String endedAt,
            String updatedAt
    ) {}
}

