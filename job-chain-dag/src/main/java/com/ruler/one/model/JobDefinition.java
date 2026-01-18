package com.ruler.one.model;

import java.time.OffsetDateTime;

/**
 * 任务定义（定义态）。
 */
public record JobDefinition(
        String jobId,
        String name,
        String description,
        String type,
        boolean enabled,
        String configJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

