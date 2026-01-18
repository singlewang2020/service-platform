package com.ruler.one.api.dto;

import java.util.List;

/**
 * Chain definition graph view:
 * - chain definition detail
 * - nodes with job detail (if node references jobId)
 * - edges (dependsOn)
 */
public final class ChainGraphDtos {
    private ChainGraphDtos() {}

    public record ChainGraphResponse(
            ChainDtos.ChainDetailResponse chain,
            List<GraphDtos.GraphNodeWithJob> nodes,
            List<GraphDtos.GraphEdge> edges
    ) {}
}
