package com.ruler.one.api.dto;

import java.util.List;

/**
 * Enhanced run detail:
 * - run basic fields
 * - nodes runtime state
 * - nodes' referenced job definitions (if any)
 * - edges (dependsOn)
 */
public final class RunGraphDtos {
    private RunGraphDtos() {}

    public record RunGraphResponse(
            RunQueryDtos.RunDetailResponse run,
            List<GraphDtos.GraphNodeWithJob> nodes,
            List<GraphDtos.GraphEdge> edges
    ) {}
}
