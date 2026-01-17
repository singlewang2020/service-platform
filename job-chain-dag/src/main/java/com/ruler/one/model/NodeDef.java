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
 *   <li>节点类型</li>
 *   <li>依赖的其他节点</li>
 *   <li>节点配置</li>
 *   <li>重试策略</li>
 * </ul>
 */
public class NodeDef {

    /**
     * 节点 ID
     * <p>
     * 唯一标识一个节点，供 DAG 引擎调度使用。
     * 由系统自动生成，用户无需手动设置。
     */
    private String id;

    /**
     * 节点类型
     * <p>
     * 指定节点的执行方式，类型需在注册中心注册。
     * 通过 {@link com.ruler.one.plugins.NodeExecutor#type()} 获取节点类型。
     * <p>
     * 示例值："print" 对应的执行器为 {@code PrintNodeExecutor}
     */
    private String type;

    /**
     * 依赖的节点列表
     * <p>
     * 指定当前节点依赖的其他节点，节点会按照依赖关系被调度执行。
     * 依赖的节点需在同一 DAG 中定义。
     * <p>
     * 示例：
     * <ul>
     *   <li>节点 A 依赖节点 B 和节点 C，则 A 的 dependsOn 列表为 [B, C]</li>
     *   <li>节点 B 和节点 C 执行成功后，节点 A 才会被调度执行</li>
     * </ul>
     */
    private List<String> dependsOn = new ArrayList<>();

    /**
     * 节点配置
     * <p>
     * 执行器执行节点时所需的配置，具体配置项根据节点类型的不同而不同。
     * 配置项需在注册中心的执行器中定义。
     * <p>
     * 示例：
     * <ul>
     *   <li>对于脚本执行器，可能需要配置脚本路径、执行参数等</li>
     *   <li>对于 HTTP 请求执行器，可能需要配置 URL、请求方法、请求头等</li>
     * </ul>
     */
    private Map<String, Object> cfg = new HashMap<>();

    /**
     * 重试策略
     * <p>
     * 节点执行失败时的重试策略，包括最大重试次数和每次重试之间的间隔时间。
     * 默认重试策略为 3 次重试，每次重试间隔 1000 毫秒。
     * <p>
     * 示例：
     * <ul>
     *   <li>maxAttempts: 5</li>
     *   <li>backoff: 2000</li>
     * </ul>
     */
    private RetryPolicy retry = new RetryPolicy(); // 默认值

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }

    public Map<String, Object> getCfg() { return cfg; }
    public void setCfg(Map<String, Object> cfg) { this.cfg = cfg; }

    public RetryPolicy getRetry() { return retry; }
    public void setRetry(RetryPolicy retry) { this.retry = retry; }
}