package com.ruler.one.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class RunDtos {
    private RunDtos() {}

    public record StopRunResponse(String runId, String status) {}

    public record NodeActionRequest(
            String artifactJson,
            String reason
    ) {}

    public record NodeActionResponse(
            @NotBlank String runId,
            @NotBlank String nodeId,
            @NotBlank String status,
            int attempt
    ) {}
}

