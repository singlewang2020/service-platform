package com.ruler.one.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class JobDtos {
    private JobDtos() {}

    public record CreateJobRequest(
            @NotBlank String name,
            String description,
            @NotBlank String type,
            String configJson,
            Boolean enabled
    ) {}

    public record UpdateJobRequest(
            @NotBlank String name,
            String description,
            @NotBlank String type,
            String configJson,
            Boolean enabled
    ) {}

    public record JobResponse(
            String jobId,
            String name,
            String description,
            String type,
            boolean enabled,
            String configJson,
            String createdAt,
            String updatedAt
    ) {}

    public record PageResponse<T>(
            int page,
            int size,
            long total,
            java.util.List<T> items
    ) {}

    public record StartJobResponse(String runId) {}
}

