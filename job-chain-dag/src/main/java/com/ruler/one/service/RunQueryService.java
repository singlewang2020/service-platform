package com.ruler.one.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.api.dto.JobDtos;
import com.ruler.one.api.dto.RunGraphDtos;
import com.ruler.one.api.dto.RunQueryDtos;
import com.ruler.one.model.DagDef;
import com.ruler.one.model.NodeDef;
import com.ruler.one.storage.JobDefinitionRepository;
import com.ruler.one.storage.RunQueryRepository;

@Service
public class RunQueryService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RunQueryRepository repo;
    private final JobDefinitionRepository jobRepo;
    private final ObjectMapper objectMapper;

    public RunQueryService(RunQueryRepository repo, JobDefinitionRepository jobRepo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.jobRepo = jobRepo;
        this.objectMapper = objectMapper;
    }

    public RunQueryDtos.RunDetailResponse getRun(String runId) {
        var r = repo.findRun(runId).orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));
        return new RunQueryDtos.RunDetailResponse(
                r.runId(),
                r.chainId(),
                r.jobId(),
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

    /**
     * Run enhanced view: node runtime rows joined with the referenced JobDefinition (if node.jobId exists in dag_json).
     */
    public RunGraphDtos.RunGraphResponse getRunGraph(String runId) {
        var run = getRun(runId);
        List<RunQueryDtos.NodeRowResponse> nodes = listNodes(runId);

        ParsedDagRefs refs = parseDagRefs(run.dagJson());
        Map<String, JobDtos.JobResponse> jobsById = loadJobs(refs.nodeIdToJobId());

        List<com.ruler.one.api.dto.GraphDtos.GraphNodeWithJob> items = new ArrayList<>();
        for (var n : nodes) {
            String nodeId = n.nodeId();
            String jobId = refs.nodeIdToJobId().get(nodeId);
            JobDtos.JobResponse job = jobId == null ? null : jobsById.get(jobId);
            items.add(new com.ruler.one.api.dto.GraphDtos.GraphNodeWithJob(
                    nodeId,
                    jobId,
                    job,
                    null,
                    n
            ));
        }

        return new RunGraphDtos.RunGraphResponse(run, items, refs.edges());
    }

    private record ParsedDagRefs(Map<String, String> nodeIdToJobId, List<com.ruler.one.api.dto.GraphDtos.GraphEdge> edges) {}

    private ParsedDagRefs parseDagRefs(String dagJson) {
        if (dagJson == null || dagJson.isBlank()) return new ParsedDagRefs(Map.of(), List.of());

        try {
            String s = dagJson;
            String t = s.trim();
            // defensive: handle double-encoded json string
            if (t.startsWith("\"") && t.endsWith("\"")) {
                s = objectMapper.readValue(t, String.class);
            }

            DagDef dag = objectMapper.readValue(s, DagDef.class);
            Map<String, String> map = new HashMap<>();
            List<com.ruler.one.api.dto.GraphDtos.GraphEdge> edges = new ArrayList<>();

            if (dag.getNodes() != null) {
                for (NodeDef n : dag.getNodes()) {
                    if (n.getId() == null || n.getId().isBlank()) continue;
                    if (n.getJobId() != null && !n.getJobId().isBlank()) {
                        map.put(n.getId(), n.getJobId());
                    }
                    if (n.getDependsOn() != null) {
                        for (String dep : n.getDependsOn()) {
                            if (dep == null || dep.isBlank()) continue;
                            edges.add(new com.ruler.one.api.dto.GraphDtos.GraphEdge(
                                    edges.size(),
                                    com.ruler.one.api.dto.GraphDtos.GraphEdgeType.DEPENDS_ON,
                                    dep,
                                    n.getId()
                            ));
                        }
                    }
                }
            }
            return new ParsedDagRefs(map, edges);
        } catch (Exception e) {
            // Don't fail run query if dag snapshot is corrupted; just omit job join.
            return new ParsedDagRefs(Map.of(), List.of());
        }
    }

    private Map<String, JobDtos.JobResponse> loadJobs(Map<String, String> nodeIdToJobId) {
        if (nodeIdToJobId.isEmpty()) return Map.of();

        Set<String> jobIds = new HashSet<>(nodeIdToJobId.values());
        Map<String, JobDtos.JobResponse> out = new HashMap<>();

        for (String jobId : jobIds) {
            jobRepo.findById(jobId).ifPresent(job -> out.put(jobId, new JobDtos.JobResponse(
                    job.jobId(),
                    job.name(),
                    job.description(),
                    job.type(),
                    job.enabled(),
                    job.configJson(),
                    job.createdAt() == null ? null : ISO.format(job.createdAt()),
                    job.updatedAt() == null ? null : ISO.format(job.updatedAt())
            )));
        }
        return out;
    }
}
