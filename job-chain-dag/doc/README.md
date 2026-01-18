# job-chain-dag 模块说明

> 目标：梳理本模块的代码结构、核心概念、运行主流程，以及与数据库表结构（DDL）的映射关系。

- 数据库表结构 / ER 图：见 [ERD.md](ERD.md)

## 1. 这个模块是干什么的？

`job-chain-dag` 是一个轻量的 **DAG（有向无环图）任务编排/执行引擎**模块雏形。

它围绕“一次作业运行（run）”展开：

- 用 `DagDef`/`NodeDef` 定义一张 DAG 图
- 用 `NodeExecutor` 插件机制执行不同类型的节点
- 用 `DagEngine` 负责编排调度（满足依赖才运行、失败处理/重试等）
- 用 `RunStorage` 将 run 和节点执行过程中的关键数据持久化到 DB，以支持：
  - 可观测（执行状态、耗时、错误）
  - 可恢复（checkpoint 断点恢复）
  - 可追溯（DAG 定义、节点产物 artifact）

## 2. 包结构与职责分层

模块主代码位于：`src/main/java/com/ruler/one/`。

### 2.1 `model/`：定义与状态（“是什么”）

- `DagDef`：DAG 定义（包含节点集合与依赖关系等）
- `NodeDef`：节点定义（nodeId、类型、参数、依赖等）
- `NodeState`：节点状态（如 PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED）
- `RetryPolicy`：重试策略
- `RunId`：一次运行的标识

> 这层尽量保持为纯模型，不关心存储细节，也不关心具体怎么执行。

### 2.2 `plugins/`：执行器扩展点（“怎么执行一个节点”）

- `NodeExecutor`：节点执行器接口

典型职责：
- 读取 `NodeDef` 与运行时上下文（`NodeContext`）
- 执行业务逻辑
- 产出 `NodeResult`（成功/失败、artifact、checkpoint、错误信息等）

> 这层是插件点：不同节点类型对应不同 executor。

### 2.3 `engine/`：引擎编排与调度（“怎么跑完整张 DAG”）

- `ExecutorRegistry`：执行器注册与路由；根据节点定义找到合适的 `NodeExecutor`
- `DagEngine`：核心引擎；负责节点调度、状态推进、错误处理、重试、持久化等

> 这层是核心控制流所在。

### 2.4 `runtime/`：运行时对象（“run 过程中传递什么数据”）

- `RunContext`：一次 run 的上下文（runId、dag、storage、全局环境等）
- `NodeContext`：节点执行上下文（节点 id、attempt、checkpoint、上游信息等）
- `NodeResult`：节点执行结果（状态、artifact、checkpoint 更新、错误等）
- `Checkpoint`：断点数据载体（通常是一个可 JSON 序列化的 map 结构）

> 这层用于把执行过程中的数据结构化、可传递、可持久化。

### 2.5 `storage/`：持久化与恢复（“怎么存、怎么读”）

- `RunStorage`：存储抽象接口（创建 run、更新状态、保存节点状态、checkpoint、artifact 等）
- `JdbcRunStorage`：基于 JDBC 的落库实现

> 如果未来要支持别的存储实现（比如 Redis、Mongo、云服务），一般只需要新增 `RunStorage` 实现。

## 3. 运行主流程（逻辑调用链）

下面是一条典型执行链（方法名可能略有差异，但分层职责基本一致）：

1. **启动一次 run**
   - 引擎入口（通常在 `DagEngine`）接收 `DagDef` + `RunId`
   - `RunStorage.createRunIfAbsent(runId, jobName, dagJson)` 初始化 `job_run`（幂等）

2. **调度可执行节点**
   - 引擎不断寻找“依赖满足”的节点
   - 对节点状态做推进：PENDING → RUNNING → (SUCCESS/FAILED/SKIPPED/RETRYING)

3. **执行节点**
   - `ExecutorRegistry` 根据 `NodeDef` 找到对应 `NodeExecutor`
   - 运行前：可从 `RunStorage.loadCheckpoint(runId, nodeId)` 读取断点
   - 执行：`NodeExecutor` 返回 `NodeResult`

4. **持久化执行结果**
   - 运行过程：`RunStorage.upsertNodeState(...)` 记录节点状态、attempt、错误、started_at/ended_at
   - 需要恢复时：`RunStorage.saveCheckpoint(...)`
   - 需要追溯产物：`RunStorage.saveArtifact(...)`

5. **结束 run**
   - 全部节点成功：`RunStorage.updateRunStatus(runId, SUCCESS)`
   - 不可恢复失败：`RunStorage.updateRunStatus(runId, FAILED)`

## 4. 数据库表结构与代码映射（基于 `db/database/1.DDL.SQL`）

DDL 文件：`db/database/1.DDL.SQL`

### 4.1 `job_run`：一次 DAG run 的总体信息

字段要点：

- `run_id`：主键
- `job_name`：作业名
- `status`：run 状态（RUNNING/SUCCESS/FAILED）
- `dag_json`：DAG 定义快照（jsonb）
- `created_at/updated_at`

对应代码行为：

- 初始化：`RunStorage.createRunIfAbsent(...)` / `JdbcRunStorage.createRunIfAbsent(...)`
- 更新：`RunStorage.updateRunStatus(...)` / `JdbcRunStorage.updateRunStatus(...)`

### 4.2 `job_run_node`：run 中每个节点的状态

字段要点：

- `(run_id, node_id)` 作为联合主键
- `status`：节点状态（PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED）
- `attempt`：重试次数
- `last_error`：最后错误
- `artifact_json`：节点产物（jsonb）
- `started_at/ended_at/updated_at`

对应代码行为：

- 状态推进：`RunStorage.upsertNodeState(...)`
- 保存产物：`RunStorage.saveArtifact(...)`（写入 `artifact_json`）

> `JdbcRunStorage` 中的 started_at/ended_at 逻辑一般是：
> - 状态首次变为 RUNNING 时写 started_at
> - 状态进入 SUCCESS/FAILED/SKIPPED 时写 ended_at

### 4.3 `job_run_checkpoint`：节点断点（checkpoint）

字段要点：

- `(run_id, node_id)` 联合主键
- `checkpoint_json`：断点（jsonb）

对应代码行为：

- 保存断点：`RunStorage.saveCheckpoint(...)`
- 读取断点：`RunStorage.loadCheckpoint(...)`

## 5. 建议阅读顺序（快速看懂）

1. `engine/DagEngine.java`（主流程控制）
2. `plugins/NodeExecutor.java`（执行器输入输出契约）
3. `engine/ExecutorRegistry.java`（路由到执行器）
4. `runtime/NodeContext.java` + `runtime/NodeResult.java`（运行时数据结构）
5. `storage/RunStorage.java`（持久化抽象能力）
6. `storage/JdbcRunStorage.java`（落库细节）
7. `model/DagDef.java` + `model/NodeDef.java`（DAG/节点定义）
8. `db/database/1.DDL.SQL`（表结构）

## 6. 约定与注意点

- `artifact_json` 与 `checkpoint_json` 都是 JSON 结构。建议保证：
  - 字段命名稳定
  - 数据可向后兼容（新增字段不破坏旧读取）
- 如果引擎支持恢复执行，恢复时需要明确：
  - 哪些节点状态视为“已完成且不再重跑”（通常 SUCCESS/SKIPPED）
  - FAILED/RETRYING 的重试边界与 RetryPolicy 行为

---

如果你希望把本文档更“贴代码”一点（把 `DagEngine` 的实际方法名、状态机细节、重试策略字段逐一对齐），我可以继续把 `DagEngine.java` / `RunStorage.java` / `NodeExecutor.java` 打开后补充一版更精准的时序与契约说明。
