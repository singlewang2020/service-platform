package com.ruler.one.model;

import java.util.ArrayList;
import java.util.List;

public class DagDef {
    private String job;
    private List<NodeDef> nodes = new ArrayList<>();

    public String getJob() { return job; }
    public void setJob(String job) { this.job = job; }

    public List<NodeDef> getNodes() { return nodes; }
    public void setNodes(List<NodeDef> nodes) { this.nodes = nodes; }
}