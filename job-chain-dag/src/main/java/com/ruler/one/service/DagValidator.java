package com.ruler.one.service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.model.DagDef;
import com.ruler.one.model.NodeDef;

@Component
public class DagValidator {

    private final ObjectMapper objectMapper;

    public DagValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 dag_json：可反序列化、node.id 非空且唯一、dependsOn 引用存在、无环。
     * 失败时抛 IllegalArgumentException。
     */
    public DagDef validate(String dagJson) {
        DagDef dag = readDag(dagJson);

        if (dag.getNodes() == null || dag.getNodes().isEmpty()) {
            throw new IllegalArgumentException("dag must have nodes");
        }

        Map<String, NodeDef> nodesById = new HashMap<>();
        for (NodeDef n : dag.getNodes()) {
            if (n.getId() == null || n.getId().isBlank()) {
                throw new IllegalArgumentException("node.id is required");
            }
            if (nodesById.put(n.getId(), n) != null) {
                throw new IllegalArgumentException("duplicate node.id: " + n.getId());
            }
        }

        // validate dependsOn references + build indegree
        Map<String, Integer> indeg = new HashMap<>();
        Map<String, List<String>> outs = new HashMap<>();
        for (String id : nodesById.keySet()) {
            indeg.put(id, 0);
            outs.put(id, new java.util.ArrayList<>());
        }

        for (NodeDef n : dag.getNodes()) {
            List<String> deps = n.getDependsOn();
            if (deps == null) continue;
            Set<String> seen = new HashSet<>();
            for (String dep : deps) {
                if (dep == null || dep.isBlank()) {
                    throw new IllegalArgumentException("node " + n.getId() + " has blank dependency");
                }
                if (!nodesById.containsKey(dep)) {
                    throw new IllegalArgumentException("node " + n.getId() + " depends on missing node " + dep);
                }
                if (!seen.add(dep)) {
                    throw new IllegalArgumentException("node " + n.getId() + " has duplicate dependency " + dep);
                }

                indeg.put(n.getId(), indeg.get(n.getId()) + 1);
                outs.get(dep).add(n.getId());
            }
        }

        // topo sort to detect cycle
        ArrayDeque<String> q = new ArrayDeque<>();
        for (var e : indeg.entrySet()) {
            if (e.getValue() == 0) q.add(e.getKey());
        }

        int visited = 0;
        while (!q.isEmpty()) {
            String id = q.removeFirst();
            visited++;
            for (String nxt : outs.get(id)) {
                indeg.put(nxt, indeg.get(nxt) - 1);
                if (indeg.get(nxt) == 0) q.addLast(nxt);
            }
        }
        if (visited != nodesById.size()) {
            throw new IllegalArgumentException("dag has cycle");
        }

        return dag;
    }

    private DagDef readDag(String dagJson) {
        try {
            return objectMapper.readValue(dagJson, DagDef.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid dag_json", e);
        }
    }
}

