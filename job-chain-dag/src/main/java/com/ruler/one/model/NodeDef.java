package com.ruler.one.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 节点定义
 * <p>
 * 节点在 DAG 引擎中的定义，包括节点的基本信息及其执行配置。
 * <ul>
 *   <li>节点 ID</li>
 *   <li>
 *       节点执行定义：
 *       <ul>
 *         <li>直接指定 {@code type}+{@code cfg}（内联节点）</li>
 *         <li>或引用一个 Job（{@code jobId}），由 JobDefinition 决定实际执行类型与配置</li>
 *       </ul>
 *   </li>
 *   <li>依赖的其他节点</li>
 *   <li>重试策略</li>
 * </ul>
 */
public class NodeDef {

    private String id;

    /**
     * 关联 JobDefinition 的 ID。
     * <p>
     * B 方案：DAG 节点可直接引用 job_definition。
     * 当 jobId 存在时，建议 type/cfg 为空（由 job.type/job.config_json 驱动执行）。
     */
    private String jobId;

    /**
     * 节点类型（内联节点类型）。
     * 当 jobId 为空时生效。
     */
    private String type;

    private List<String> dependsOn = new ArrayList<>();

    /**
     * 节点配置（内联节点配置）。
     * 当 jobId 为空时生效。
     */
    private Map<String, Object> cfg = new HashMap<>();

    private RetryPolicy retry = new RetryPolicy();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }

    public Map<String, Object> getCfg() { return cfg; }
    public void setCfg(Map<String, Object> cfg) { this.cfg = cfg; }

    public RetryPolicy getRetry() { return retry; }
    public void setRetry(RetryPolicy retry) { this.retry = retry; }
}