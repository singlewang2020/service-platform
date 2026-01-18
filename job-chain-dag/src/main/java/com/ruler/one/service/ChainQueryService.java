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
import com.ruler.one.api.dto.ChainDtos;
import com.ruler.one.api.dto.ChainGraphDtos;
import com.ruler.one.api.dto.JobDtos;
import com.ruler.one.model.DagDef;
import com.ruler.one.model.NodeDef;
import com.ruler.one.model.JobChainDefinition;
import com.ruler.one.storage.JobChainDefinitionRepository;
import com.ruler.one.storage.JobDefinitionRepository;

@Service
public class ChainQueryService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JobChainDefinitionRepository chainRepo;
    private final JobDefinitionRepository jobRepo;
    private final ObjectMapper objectMapper;

    public ChainQueryService(JobChainDefinitionRepository chainRepo, JobDefinitionRepository jobRepo, ObjectMapper objectMapper) {
        this.chainRepo = chainRepo;
        this.jobRepo = jobRepo;
        this.objectMapper = objectMapper;
    }

    public ChainGraphDtos.ChainGraphResponse getGraph(String chainId) {
        JobChainDefinition c = chainRepo.findById(chainId)
                .orElseThrow(() -> new IllegalArgumentException("chain not found: " + chainId));

        ChainDtos.ChainDetailResponse chain = new ChainDtos.ChainDetailResponse(
                c.chainId(),
                c.name(),
                c.description(),
                c.enabled(),
                c.version(),
                c.dagJson(),
                c.createdAt() == null ? null : ISO.format(c.createdAt()),
                c.updatedAt() == null ? null : ISO.format(c.updatedAt())
        );

        DagDef dag = readDag(c.dagJson());

        Map<String, String> nodeIdToJobId = new HashMap<>();
        List<com.ruler.one.api.dto.GraphDtos.GraphEdge> edges = new ArrayList<>();

        if (dag.getNodes() != null) {
            for (NodeDef n : dag.getNodes()) {
                if (n.getId() == null || n.getId().isBlank()) continue;
                if (n.getJobId() != null && !n.getJobId().isBlank()) {
                    nodeIdToJobId.put(n.getId(), n.getJobId());
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

        Map<String, JobDtos.JobResponse> jobsById = loadJobs(nodeIdToJobId);

        List<com.ruler.one.api.dto.GraphDtos.GraphNodeWithJob> nodes = new ArrayList<>();
        if (dag.getNodes() != null) {
            for (NodeDef n : dag.getNodes()) {
                if (n.getId() == null || n.getId().isBlank()) continue;
                String jobId = nodeIdToJobId.get(n.getId());
                JobDtos.JobResponse job = jobId == null ? null : jobsById.get(jobId);
                nodes.add(new com.ruler.one.api.dto.GraphDtos.GraphNodeWithJob(
                        n.getId(),
                        jobId,
                        job,
                        n.getDependsOn() == null ? List.of() : n.getDependsOn(),
                        null
                ));
            }
        }

        return new ChainGraphDtos.ChainGraphResponse(chain, nodes, edges);
    }

    private DagDef readDag(String dagJson) {
        try {
            String s = dagJson;
            if (s != null) {
                String t = s.trim();
                if (t.startsWith("\"") && t.endsWith("\"")) {
                    s = objectMapper.readValue(t, String.class);
                }
            }
            return objectMapper.readValue(s, DagDef.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid dag_json", e);
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
