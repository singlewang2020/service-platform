# DAG 与 topoSort（拓扑排序）说明

本文档解释 `job-chain-dag` 模块里 `DagEngine#topoSort(DagDef dag)` 的目的、实现思路，以及其背后的数据结构：DAG（有向无环图）。

> 关联代码：`job-chain-dag/src/main/java/com/ruler/one/engine/DagEngine.java`

---

## 0. 图论是什么？（工程化理解）

**图论（Graph Theory）**是研究“点（节点）与边（关系）”的数据结构与算法领域。

在软件工程里，它经常用来描述和解决“关系/依赖/路径”类问题，例如：

- 任务依赖与调度（工作流、作业编排）
- 构建依赖（Gradle/Maven）
- 编译/模块依赖（决定编译顺序、检测循环依赖）
- 路径与最短路（地图导航、网络路由）

一张图通常包含：

- **节点（Node/Vertex）**：对象（任务、模块、城市、用户……）
- **边（Edge）**：关系（依赖、连接、关注、路线……）
- **权重（Weight，可选）**：代价（距离、耗时、成本……）

---

## 1. topoSort 是用来“构建 DAG”的吗？

不是。

- **DAG 的“定义/构建”**：来自 `DagDef`（`dag.getNodes()`）以及每个 `NodeDef` 的 `dependsOn` 字段。
- **`topoSort(dag)` 的作用**：对已经定义好的依赖关系做 **拓扑排序（Topological Sort）**，得到一个“满足依赖约束的执行顺序”。

`DagEngine#run()` 里会先 `topoSort(dag)`：

- 得到一个列表 `ordered`（节点的执行顺序）
- 然后按该顺序遍历执行
- 运行时再用 `depsAllSuccess()` 来保证依赖成功才运行当前节点

---

## 2. 什么是 DAG（有向无环图）？

### 2.1 Graph（图）的基本元素

- **节点（Node/Vertex）**：例如一个任务/步骤/作业节点（本项目里就是 `NodeDef`）
- **边（Edge）**：节点之间的关系/依赖

### 2.2 Directed（有向）

边是带方向的。

在依赖编排里通常表示“先后关系”：

- 若 B 依赖 A，则表示 A 必须先完成，才能执行 B。

因此在图论表示上，通常用一条 **有向边** 表示：

> A  ->  B

### 2.3 Acyclic（无环）

无环意味着：不存在一条路径让你从某个节点出发，沿着有向边走，最终又回到自己。

例如：

- A 依赖 B
- B 依赖 A

这就形成一个环（cycle），会导致**死锁**：A 想等 B，B 想等 A，谁也无法开始。

所以工作流/任务编排系统通常要求依赖关系必须是 **DAG**。

---

## 3. 这算基础算法吗？

算。

- **DAG** 是基础数据结构/建模方式
- **拓扑排序** 是图论里的经典基础算法

工程里常见出现位置：

- 工作流/作业编排（先做谁、后做谁、能否并行）
- 构建系统（依赖顺序、增量构建）
- 模块依赖分析（检测循环依赖）

---

## 4. topoSort 的实现：Kahn 算法（基于入度 indegree）

`DagEngine#topoSort()` 使用的是经典的 **Kahn 拓扑排序算法**。

### 4.1 核心概念：入度 indegree

- `indeg[x]` 表示节点 x 还有多少个“前置依赖”没有满足
- 在图论里，入度就是“指向该节点的边的数量”

对于依赖关系：

- 如果 `n.dependsOn` 包含 `dep`
- 表示 `dep -> n`

那么：

- `indeg[n] += 1`

### 4.2 topoSort 做了什么（按代码结构拆解）

#### (1) 建索引：id -> NodeDef

```java
Map<String, NodeDef> map = new HashMap<>();
for (NodeDef n : dag.getNodes()) map.put(n.getId(), n);
```

后面能通过 id 快速定位节点。

#### (2) 初始化入度表 indeg 和出边表 out

```java
Map<String, Integer> indeg = new HashMap<>();
Map<String, List<String>> out = new HashMap<>();
for (NodeDef n : dag.getNodes()) {
    indeg.put(n.getId(), 0);
    out.put(n.getId(), new ArrayList<>());
}
```

- `indeg`：每个节点先建好入度计数
- `out`：记录“某节点指向哪些后继节点”（也就是执行完它后，哪些节点可能变得可执行）

#### (3) 扫描 dependsOn：建立边、填入度

```java
for (NodeDef n : dag.getNodes()) {
    for (String dep : n.getDependsOn()) {
        if (!map.containsKey(dep)) {
            throw new IllegalArgumentException("Node " + n.getId() + " depends on missing node " + dep);
        }
        indeg.put(n.getId(), indeg.get(n.getId()) + 1);
        out.get(dep).add(n.getId());
    }
}
```

这段暗含的边方向是：

- `dep -> n`

因此：

- `indeg[n]++`
- `out[dep].add(n)`

另外还会做一个重要校验：

- 若 `dep` 不在节点集合中，直接抛异常（依赖了不存在的节点）

#### (4) 找到所有入度为 0 的节点，加入队列

```java
Deque<String> q = new ArrayDeque<>();
for (var e : indeg.entrySet()) if (e.getValue() == 0) q.add(e.getKey());
```

入度为 0 表示：

- 没有任何前置依赖
- 可以最先执行

#### (5) 逐个“出队 + 放入结果 + 降低后继入度”

```java
while (!q.isEmpty()) {
    String id = q.removeFirst();
    ordered.add(map.get(id));
    for (String nxt : out.get(id)) {
        indeg.put(nxt, indeg.get(nxt) - 1);
        if (indeg.get(nxt) == 0) q.addLast(nxt);
    }
}
```

语义是：

- 从队列拿到一个当前可执行节点 `id`
- 把它加入 `ordered`
- 认为它已被“处理/执行”
- 对它的每个后继 `nxt`：
  - `indeg[nxt]--`（少了一个前置依赖）
  - 若 `indeg[nxt]` 变为 0，则 `nxt` 变得可执行，入队

#### (6) 检测环：如果结果数量不足，说明存在 cycle

```java
if (ordered.size() != dag.getNodes().size()) {
    throw new IllegalArgumentException("DAG has cycle or disconnected graph");
}
```

关键点：

- 当存在环时，环上的节点入度永远不会降到 0
- 因此它们永远不会进入队列 `q`
- 最终 `ordered.size()` 会小于节点总数

> 备注：这里的错误信息写了 “or disconnected graph”。
> 实际上只要无环，不连通（多个独立子图）也能拓扑排序成功。
> 真正会导致 `ordered.size() < total` 的主要原因是 **存在环**。

---

## 5. 另一种常见实现：DFS 拓扑排序（了解即可）

除 Kahn 算法外，拓扑排序也常用 DFS 来实现：

- DFS 遍历时在“回溯阶段（后序）”把节点加入列表
- 最终把结果反转就是拓扑序
- 判环通常用三色标记/访问状态：
  - 0：未访问
  - 1：访问中（递归栈中）
  - 2：已完成
- 若访问到状态为 1 的节点，说明存在环

> 在工程调度里：Kahn（入度）更直观，也更方便做“当前可执行节点队列”。

---

## 6. 示例：一个典型 DAG 的依赖与拓扑序

假设有 4 个节点：

- A：无依赖
- B：依赖 A
- C：依赖 A
- D：依赖 B 和 C

图的边是：

- A -> B
- A -> C
- B -> D
- C -> D

拓扑序可以是：

- `A, B, C, D`

也可以是：

- `A, C, B, D`

只要保证：

- A 在 B/C 前
- B/C 在 D 前

---

## 7. topoSort 与运行时依赖检查的关系

- `topoSort()` 解决的是：
  - **静态层面**：把节点排序成一个“依赖满足的顺序”
  - 并检测明显的结构问题（缺失依赖节点、存在环）

- `depsAllSuccess()` 解决的是：
  - **运行时层面**：即使排序满足“先后顺序”，也要确保依赖节点真的执行 **SUCCESS**
  - 若依赖未成功，当前节点被标记 `SKIPPED`（当前实现是“严格/保守”策略）

---

## 8. 常见边界情况（你看代码时可以对照）

1. **dependsOn 引用了不存在的节点**
   - topoSort 会抛 `IllegalArgumentException`

2. **存在环（cycle）**
   - topoSort 返回前会检查数量，不够则抛异常

3. **多个起点 / 多个独立子图**（不连通，但无环）
   - topoSort 仍然能工作，会把所有 indeg=0 的点都入队

4. **dependsOn 为 null**
   - `depsAllSuccess()` 已处理（直接 true）
   - 但 topoSort 当前实现里直接 `for (String dep : n.getDependsOn())` 可能 NPE
   - 若你的模型允许 null，建议在 topoSort 里也做 null/empty 保护
