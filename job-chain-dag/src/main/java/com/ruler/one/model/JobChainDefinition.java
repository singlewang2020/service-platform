package com.ruler.one.model;

import java.time.OffsetDateTime;

/**
 * 任务链（DAG）定义（定义态）。
 */
public record JobChainDefinition(
        String chainId,
        String name,
        String description,
        boolean enabled,
        long version,
        String dagJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

