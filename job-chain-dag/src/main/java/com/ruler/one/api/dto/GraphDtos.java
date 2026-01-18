package com.ruler.one.api.dto;

/**
 * Shared graph DTOs used by both ChainGraph and RunGraph.
 * <p>
 * Goal: keep edge/node semantics consistent so front-end can reuse rendering logic.
 */
public final class GraphDtos {
    private GraphDtos() {}

    public enum GraphEdgeType {
        DEPENDS_ON
    }

    /**
     * Generic directed edge.
     * @param index stable order within a response
     * @param type semantic type for styling / future evolution
     * @param from source nodeId
     * @param to target nodeId
     */
    public record GraphEdge(
            int index,
            GraphEdgeType type,
            String from,
            String to
    ) {}

    /**
     * Shared node item for graph views.
     * <p>
     * We keep this superset so both ChainGraph and RunGraph can reuse the same type:
     * - ChainGraph uses: nodeId/jobId/job/dependsOn
     * - RunGraph uses: nodeId/jobId/job/node(runtime)
     */
    public record GraphNodeWithJob(
            String nodeId,
            String jobId,
            JobDtos.JobResponse job,
            java.util.List<String> dependsOn,
            RunQueryDtos.NodeRowResponse node
    ) {}
}
