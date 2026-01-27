# job-chain-dag 模块说明

> 目标：梳理本模块的代码结构、核心概念、运行主流程，以及与数据库表结构（DDL）的映射关系。

- 数据库表结构 / ER 图：见 [ERD.md](ERD.md)
- API 契约（请求/响应/错误码/幂等/Graph DTO）：见 [API_CONTRACT.md](API_CONTRACT.md)

## 1. 这个模块是干什么的？

`job-chain-dag` 是一个轻量的 **Workflow（工作流）/DAG 任务编排与执行引擎**模块雏形。

### 1.1 A 方案闭环口径（最小改动）

为形成产品闭环，本模块对外采用以下心智模型：

- **Job（任务模板）**：定义“一个节点怎么执行”（type + configJson），可复用
- **Workflow（工作流，顶层可执行对象）**：用 DAG 把多个 Job 串起来（编排）
- **Run（一次运行）**：一次 Workflow 启动后的执行实例（含 DAG 快照）
- **NodeRun / Checkpoint**：Run 内节点级状态与断点/产物

> 重要：A 方案下 **Workflow = 现有的 Chain Definition（`job_chain_definition`）**。
> 
> - 代码/DB 内部仍沿用 `Chain` 相关命名（例如 `ChainController/JobChainDefinitionRepository`）
> - 文档与对外 API 口径推荐使用 `Workflow`（可保留 `/chains` 作为别名）

### 1.2 这个模块围绕什么展开？

它围绕“一次工作流运行（run）”展开，并通过持久化实现：

- **可观测**：执行状态、耗时、错误原因
- **可恢复**：checkpoint 断点恢复 / 重试
- **可追溯**：DAG 定义快照、节点产物 artifact

---

## 2. 对外 API 设计（文档口径）

> 本节是“对外契约”。当前代码中可能仍以 `Chain` 命名实现。后续若要在 Swagger 中体现 Workflow 心智，可在不改核心逻辑的前提下新增 `/workflows` 路由别名或重命名 Controller。

### 2.1 Job（任务模板）

- `POST /jobs`：创建 Job
- `PUT /jobs/{jobId}`：更新 Job
- `GET /jobs`：分页查询 Job
- `GET /jobs/{jobId}`：Job 详情
- `POST /jobs/{jobId}:enable` / `:disable`：启用/禁用

约束：
- `job_definition.name` 唯一
- Job 被 Workflow 引用时，建议在创建/更新 Workflow 时校验 `jobId` 存在且 `enabled=true`

### 2.2 Workflow（工作流，= chain definition）

- `POST /workflows`：创建 Workflow（写入 `job_chain_definition`）
- `PUT /workflows/{workflowId}`：更新（DAG/描述/启用状态），并 `version++`
- `GET /workflows`：分页查询
- `GET /workflows/{workflowId}`：详情（包含 dag）
- `GET /workflows/{workflowId}/graph`：返回适合画图的 nodes + edges（建议 nodes 直接 join job detail）

DAG 节点最小字段建议：
- `id`（nodeId）
- `jobId`（引用 Job）
- `dependsOn[]`
- `retry{maxAttempts, backoffMillis, backoffMultiplier}`（可选）

DAG 校验建议：
- nodeId 唯一
- dependsOn 引用必须存在
- DAG 无环
- jobId 必须存在且 enabled

### 2.3 Run（运行态）

- `POST /workflows/{workflowId}:start`：立即执行，返回 `runId`
  - 幂等建议：允许客户端传 `runId` 或 `idempotencyKey`（v0.0 可先不落库 idempotencyKey，但文档先定义）
- `GET /runs`：分页查询 run（支持按 workflowId/status/time 过滤）
- `GET /runs/{runId}`：run 详情
  - 建议包含：run 基本信息 + dag 快照解析结构 + node 列表
  - 建议一次性 join：根据 dag 中的 `jobId` 补全对应 job detail（前端渲染更舒服）
- `POST /runs/{runId}:stop`：停止（软停止：不再调度新节点）
- `POST /runs/{runId}:close`：关闭（强制终态：不再推进状态）

### 2.4 NodeRun 控制

- `POST /runs/{runId}/nodes/{nodeId}:retry`：单节点重试
- `POST /runs/{runId}/nodes/{nodeId}:complete`：单节点人工完成

---

## 3. 状态机（Run/Node）

> 本节用于规范状态含义，避免接口与引擎行为分裂。

### 3.1 Run 状态（建议统一口径）

- `PENDING`：已创建未开始调度
- `RUNNING`：调度执行中
- `STOPPING`：收到 stop 请求（软停止中）
- `SUCCESS`：全部节点成功/人工完成
- `FAILED`：出现不可恢复失败
- `CLOSED`：强制关闭（终态）

> 约束：
> - `STOPPING/CLOSED` 后不再调度任何新节点（PENDING/RETRYING 不再进入 RUNNING）

### 3.2 Node 状态（贴近现有注释）

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `RETRYING`（或 RETRY_WAITING：二选一，建议全项目统一一种命名）
- `SKIPPED`

节点控制约束：
- `retry` 仅允许在 `FAILED`（或 RETRYING）时触发
- `complete` 仅允许在 `FAILED/RUNNING` 时触发（并记录操作人/原因；v0.0 可先走日志/产物字段）

---

## 4. 包结构与职责分层

模块主代码位于：`src/main/java/com/ruler/one/`。

### 4.1 `model/`：定义与状态（“是什么”）

- `DagDef`：DAG 定义（包含节点集合与依赖关系等）
- `NodeDef`：节点定义（nodeId、类型、参数、依赖等）
- `NodeState`：节点状态（如 PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED）
- `RetryPolicy`：重试策略
- `RunId`：一次运行的标识

> 这层尽量保持为纯模型，不关心存储细节，也不关心具体怎么执行。

### 4.2 `plugins/`：执行器扩展点（“怎么执行一个节点”）

- `NodeExecutor`：节点执行器接口

典型职责：
- 读取 `NodeDef` 与运行时上下文（`NodeContext`）
- 执行业务逻辑
- 产出 `NodeResult`（成功/失败、artifact、checkpoint、错误信息等）

> 这层是插件点：不同节点类型对应不同 executor。

### 4.3 `engine/`：引擎编排与调度（“怎么跑完整张 DAG”）

- `ExecutorRegistry`：执行器注册与路由；根据节点定义找到合适的 `NodeExecutor`
- `DagEngine`：核心引擎；负责节点调度、状态推进、错误处理、重试、持久化等

> 这层是核心控制流所在。

### 4.4 `runtime/`：运行时对象（“run 过程中传递什么数据”）

- `RunContext`：一次 run 的上下文（runId、dag、storage、全局环境等）
- `NodeContext`：节点执行上下文（节点 id、attempt、checkpoint、上游信息等）
- `NodeResult`：节点执行结果（状态、artifact、checkpoint 更新、错误等）
- `Checkpoint`：断点数据载体（通常是一个可 JSON 序列化的 map 结构）

> 这层用于把执行过程中的数据结构化、可传递、可持久化。

### 4.5 `storage/`：持久化与恢复（“怎么存、怎么读”）

- `RunStorage`：存储抽象接口（创建 run、更新状态、保存节点状态、checkpoint、artifact 等）
- `JdbcRunStorage`：基于 JDBC 的落库实现

> 如果未来要支持别的存储实现（比如 Redis、Mongo、云服务），一般只需要新增 `RunStorage` 实现。

---

## 5. 运行主流程（逻辑调用链）

下面是一条典型执行链（方法名可能略有差异，但分层职责基本一致）：

1. **启动一次 run（从 Workflow 启动）**
   - API 层接收 `workflowId`（内部可能仍叫 chainId）
   - 从 `job_chain_definition` 读取 `dag_json` 并校验
   - 引擎入口（通常在 `DagEngine` / `EngineRunner`）接收 `DagDef` + `RunId`
   - `RunStorage.createRunIfAbsent(runId, jobName, dagJson)` 初始化 `job_run`（幂等），并固化 DAG 快照

2. **初始化节点状态**
   - 为 DAG 内每个 node 初始化 `job_run_node`（PENDING）

3. **调度可执行节点**
   - 引擎不断寻找“依赖满足”的节点
   - 对节点状态做推进：PENDING → RUNNING → (SUCCESS/FAILED/SKIPPED/RETRYING)

4. **执行节点**
   - `ExecutorRegistry` 根据 `NodeDef` 找到对应 `NodeExecutor`
   - 运行前：可从 `RunStorage.loadCheckpoint(runId, nodeId)` 读取断点
   - 执行：`NodeExecutor` 返回 `NodeResult`

5. **持久化执行结果**
   - 运行过程：`RunStorage.upsertNodeState(...)` 记录节点状态、attempt、错误、started_at/ended_at
   - 需要恢复时：`RunStorage.saveCheckpoint(...)`
   - 需要追溯产物：`RunStorage.saveArtifact(...)`

6. **结束 run**
   - 全部节点成功：`RunStorage.updateRunStatus(runId, SUCCESS)`
   - 不可恢复失败：`RunStorage.updateRunStatus(runId, FAILED)`
   - stop/close：按 Run 状态机语义处理（stop 不再调度新节点；close 直接终态）

---

## 6. 数据库表结构与代码映射（基于 `db/database/1.DDL.SQL`）

DDL 文件：`db/database/1.DDL.SQL`

> 注意：`db/database/1.DDL.SQL` 当前主要覆盖运行态（run/node/checkpoint）。
> Definition 态（Job/Workflow）表结构详见 [ERD.md](ERD.md)（来源于 `src/main/resources/schema.sql`）。

### 6.1 `job_run`：一次 DAG run 的总体信息

字段要点：

- `run_id`：主键
- `job_name`：作业名（展示字段）
- `status`：run 状态
- `dag_json`：DAG 定义快照（jsonb）
- `created_at/updated_at`

对应代码行为：

- 初始化：`RunStorage.createRunIfAbsent(...)` / `JdbcRunStorage.createRunIfAbsent(...)`
- 更新：`RunStorage.updateRunStatus(...)` / `JdbcRunStorage.updateRunStatus(...)`

> A 方案闭环建议：run 逻辑上应能追溯启动它的 Workflow（= chain）。
> 具体实现可以通过 `job_run.chain_id`（或 workflowId 字段）关联到 `job_chain_definition.chain_id`。

### 6.2 `job_run_node`：run 中每个节点的状态

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

### 6.3 `job_run_checkpoint`：节点断点（checkpoint）

字段要点：

- `(run_id, node_id)` 联合主键
- `checkpoint_json`：断点（jsonb）

对应代码行为：

- 保存断点：`RunStorage.saveCheckpoint(...)`
- 读取断点：`RunStorage.loadCheckpoint(...)`

---

## 7. 建议阅读顺序（快速看懂）

1. `engine/DagEngine.java`（主流程控制）
2. `service/ChainRunService.java` / `service/EngineRunner.java`（Workflow/Run 启动入口）
3. `plugins/NodeExecutor.java`（执行器输入输出契约）
4. `engine/ExecutorRegistry.java`（路由到执行器）
5. `runtime/NodeContext.java` + `runtime/NodeResult.java`（运行时数据结构）
6. `storage/RunStorage.java`（持久化抽象能力）
7. `storage/JdbcRunStorage.java`（落库细节）
8. `model/DagDef.java` + `model/NodeDef.java`（DAG/节点定义）
9. `db/database/1.DDL.SQL`（运行态表结构）
10. [ERD.md](ERD.md)（全量表结构与逻辑 ER）

## 8. 约定与注意点

- `artifact_json` 与 `checkpoint_json` 都是 JSON 结构。建议保证：
  - 字段命名稳定
  - 数据可向后兼容（新增字段不破坏旧读取）
- 如果引擎支持恢复执行，恢复时需要明确：
  - 哪些节点状态视为“已完成且不再重跑”（通常 SUCCESS/SKIPPED）
  - FAILED/RETRYING 的重试边界与 RetryPolicy 行为
- A 方案当前不引入独立的 workflow/version 表；未来若要做发布/回滚，可演进为：`workflow` + `workflow_version` 两表（本模块现有 chain definition 可平滑迁移为 workflow_version）。
