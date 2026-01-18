# JobChain 后端技术说明（job-chain-dag / v0.0）

> 适用模块：`job-chain-dag`
>
> 代码风格：Spring Boot + Spring Web + Spring JDBC（无 ORM）+ springdoc-openapi
>
> 本文内容尽量贴近当前实际代码与数据库字段：
> - API：`com.ruler.one.api.*`
> - Service：`com.ruler.one.service.*`
> - Engine：`com.ruler.one.engine.*`
> - Storage：`com.ruler.one.storage.*`
> - Schema：`job-chain-dag/src/main/resources/schema.sql`
> - topoSort 原理：见 `doc/DAG_TOPO_SORT.md`

---

## A. 一句话定位 + 目标/范围/不做什么

### A1. 一句话定位
**JobChain 是一个 DAG 任务链编排与运行记录服务**：管理任务链定义（DAG JSON），支持立即执行生成一次运行（run），记录节点运行态，并提供 stop / 节点 retry / 节点 complete 等运行管理能力。

### A2. 目标（v0.0 范围：以当前代码为准）
- 链定义：
  - `job_chain_definition` 的创建/更新（带 `version` 乐观锁）/启用/禁用
  - page / detail 查询
  - DAG JSON 校验（反序列化、nodeId 唯一、依赖引用存在、无环）
- 运行态：
  - `chain:start` 创建一次 run、初始化 node 状态，并异步触发 `DagEngine` 执行
  - run / node 查询
  - run stop（设置 run 为 `STOPPING`，引擎协作式停止）
  - 单节点 retry/complete（管理写库）

### A3. 明确不做什么（v0.0 不包含）
- 不做定时调度（cron / delay / DAG 事件触发）
- 不做分布式 worker 体系（心跳、抢占、分片）
- 不做权限鉴权（目前无 Spring Security）
- 不做“状态驱动的重调度器”（人工 retry/complete 不会自动推进 DAG 依赖计算）

---

## B. API 清单（接口、入参出参、幂等、错误码、权限点）

### B0. 错误返回与错误码
当前项目没有统一的 `errorCode` 枚举，统一由 `GlobalExceptionHandler` 映射：

- `IllegalArgumentException` -> **400 Bad Request**
- `IllegalStateException` -> **409 Conflict**

响应体格式：

```json
{ "error": "<message>" }
```

对应代码：`com.ruler.one.api.GlobalExceptionHandler`。

> 后续如果要对接前端更稳定，建议引入 `errorCode`（例如 `CHAIN_NOT_FOUND` / `VERSION_CONFLICT`），并保留 `message`。

---

### B1. Chain APIs（`/api/v1/chains`）

#### 1) 创建链
- **POST** `/api/v1/chains`
- Request：`ChainDtos.CreateChainRequest`
  - `name`：必填（`@NotBlank`）
  - `description`：可选
  - `dagJson`：必填（`@NotBlank`）
  - `enabled`：可选（null 表示 true）
- Response：`ChainDtos.ChainDetailResponse`
- 幂等：否（重复 name 会失败）
- 错误：
  - 400：name 已存在；或 `dagJson` 不合法（见 `DagValidator`）
- 权限点：TBD（建议 `chain:write`）

实现入口：`com.ruler.one.api.ChainController#create` -> `com.ruler.one.service.ChainService#create`。

#### 2) 更新链（乐观锁）
- **PUT** `/api/v1/chains/{chainId}`
- Request：`ChainDtos.UpdateChainRequest`
  - `name`：必填
  - `description`：可选
  - `dagJson`：必填
  - `version`：必填（`@Min(1)`）
- Response：`ChainDtos.ChainDetailResponse`
- 幂等：
  - 语义上“不是幂等”：更新成功会 `version+1`，重复提交旧 version 必然冲突。
- 错误：
  - 400：chain 不存在；name 被其它 chain 占用；dagJson 校验失败
  - 409：version 冲突（`chain version conflict or not found`）
- 权限点：TBD（建议 `chain:write`）

实现入口：`ChainController#update` -> `ChainService#update` -> `JobChainDefinitionRepository#updateWithVersion`。

#### 3) 启用/禁用
- **POST** `/api/v1/chains/{chainId}:enable`
- **POST** `/api/v1/chains/{chainId}:disable`
- Response：`ChainDtos.ChainDetailResponse`
- 幂等：是（重复 enable/disable 结果一致）
- 错误：400 chain 不存在
- 权限点：TBD（建议 `chain:write`）

实现入口：`ChainService#enable/#disable`。

#### 4) 分页查询
- **GET** `/api/v1/chains?page=1&size=20&keyword=&enabled=`
- Response：`JobDtos.PageResponse<ChainDtos.ChainSummaryResponse>`
- 幂等：是
- 权限点：TBD（建议 `chain:read`）

#### 5) 详情查询
- **GET** `/api/v1/chains/{chainId}`
- Response：`ChainDtos.ChainDetailResponse`（包含 `dagJson`）
- 幂等：是
- 错误：400 chain 不存在
- 权限点：TBD（建议 `chain:read`）

#### 6) 立即执行链（start）
- **POST** `/api/v1/chains/{chainId}:start`
- Response：`JobDtos.StartJobResponse { runId }`
- 幂等：否（每次生成新的 `runId`）
- 错误：
  - 400：chain 不存在；dag_json 无法反序列化
  - 409：chain disabled
- 权限点：TBD（建议 `run:write`）

实现入口：`ChainController#start` -> `ChainRunService#startChain`。

---

### B2. Run APIs（`/api/v1`）

#### 1) 查询 run
- **GET** `/api/v1/runs/{runId}`
- Response：`RunQueryDtos.RunDetailResponse`
- 幂等：是
- 错误：400 run 不存在
- 权限点：TBD（建议 `run:read`）

#### 2) 查询 run 下的 node 列表
- **GET** `/api/v1/runs/{runId}/nodes`
- Response：`List<RunQueryDtos.NodeRowResponse>`
- 幂等：是
- 权限点：TBD（建议 `run:read`）

#### 3) stop run
- **POST** `/api/v1/runs/{runId}:stop`
- Response：`RunDtos.StopRunResponse { runId, status }`
  - 当前实现返回 `STOPPING`
- 幂等：近似幂等（重复 stop 会保持 STOPPING；对 SUCCESS/FAILED 会 409）
- 错误：
  - 400：run 不存在
  - 409：run 已结束（SUCCESS/FAILED）
- 权限点：TBD（建议 `run:control`）

实现入口：`RunController#stop` -> `RunService#stop`。

#### 4) 单节点 retry（人工）
- **POST** `/api/v1/runs/{runId}/nodes/{nodeId}:retry`
- Body（可选）：`RunDtos.NodeActionRequest { artifactJson, reason }`
- Response：`RunDtos.NodeActionResponse`
- 幂等：否（每次会 `attempt+1`）
- 限制：仅允许当前状态为 `FAILED` 或 `STOPPED`
- 错误：
  - 400：node 不存在
  - 409：node 状态不可 retry
- 权限点：TBD（建议 `run:control`）

#### 5) 单节点 complete（人工）
- **POST** `/api/v1/runs/{runId}/nodes/{nodeId}:complete`
- Body（可选）：`RunDtos.NodeActionRequest { artifactJson, reason }`
- Response：`RunDtos.NodeActionResponse`
- 幂等：近似幂等（多次写 SUCCESS，attempt 不变）
- 限制：允许 `FAILED`/`STOPPED`/`RUNNING`
- 错误：
  - 400：node 不存在
  - 409：node 状态不可 complete
- 权限点：TBD（建议 `run:control`）

---

## C. 数据模型（表结构、主键外键、唯一约束、索引）

> 以 `job-chain-dag/src/main/resources/schema.sql` 为准。

### C1. 链定义：`job_chain_definition`
- 主键：`chain_id`
- 唯一：`name unique`
- 字段：
  - `chain_id varchar(64)`
  - `name varchar(128) not null unique`
  - `description text`
  - `enabled boolean not null default true`
  - `version bigint not null default 1`
  - `dag_json jsonb not null`
  - `created_at timestamptz not null default now()`
  - `updated_at timestamptz not null default now()`

### C2. 一次运行：`job_run`
- 主键：`run_id`
- 索引：
  - `idx_job_run_chain_id(chain_id)`
  - `idx_job_run_job_id(job_id)`
- 字段：
  - `run_id varchar(64)`
  - `chain_id varchar(64)`（v0.0 的 `chain:start` 目前未写入）
  - `job_id varchar(64)`
  - `job_name varchar(128) not null`
  - `status varchar(32) not null`（见状态机）
  - `dag_json jsonb not null`（运行快照）
  - `created_at/updated_at timestamptz`

### C3. 节点运行态：`job_run_node`
- 主键：`(run_id, node_id)`
- 字段：
  - `status varchar(32)`：PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED/STOPPED
  - `attempt int default 0`
  - `last_error text`
  - `artifact_json jsonb`
  - `started_at/ended_at timestamptz`

### C4. 节点 checkpoint：`job_run_checkpoint`
- 主键：`(run_id, node_id)`
- 字段：
  - `checkpoint_json jsonb not null`

> 外键：当前 schema 未显式声明外键约束（v0.0 简化）。

---

## D. 状态机（链路/节点状态、流转表、约束条件）

### D1. Run 状态（`job_run.status`）
- `PENDING`：刚创建
- `RUNNING`：引擎开始执行
- `SUCCESS`：所有节点执行成功
- `FAILED`：任意节点失败且达到最大重试次数 / 或异常终止
- `STOPPING`：API stop 后进入，表示“请求停止”
- `STOPPED`：引擎识别 STOPPING 并停止后设置

关键实现：
- `RunService#stop` 只负责把状态改为 `STOPPING`
- `DagEngine#isStopped` 将 `STOPPING/STOPPED` 视为需要停止

### D2. Node 状态（`job_run_node.status`）
对应 enum：`com.ruler.one.model.NodeState`

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `RETRYING`
- `SKIPPED`
- `STOPPED`

流转（引擎内）：
- 初始化：PENDING
- 执行：RUNNING -> SUCCESS
- 异常：RUNNING -> RETRYING -> ... -> FAILED
- 依赖失败：SKIPPED
- 停止：STOPPED

人工操作（管理写库）：
- retry：FAILED/STOPPED -> RETRYING（attempt+1）
- complete：FAILED/STOPPED/RUNNING -> SUCCESS

> v0.0 注意：人工 retry/complete 不会自动触发调度推进，属于后续“状态驱动调度器”演进点。

---

## E. 三条关键时序：立即执行 / 失败重试 / 停止关闭

### E1. 立即执行（chain:start）
1. `POST /api/v1/chains/{chainId}:start`
2. `ChainRunService#startChain`：
   - 校验 chain 存在且 enabled
   - 生成 `runId`
   - 写入 `job_run`（PENDING）
   - 初始化所有节点 `job_run_node`（PENDING）
   - 异步提交 `DagEngine.run(runId, dag)`
3. `DagEngine.run`：
   - `job_run.status=RUNNING`
   - topoSort（Kahn 算法：见 `doc/DAG_TOPO_SORT.md`）
   - 逐节点执行并写状态/产物
   - 结束写 `SUCCESS` 或 `FAILED`

### E2. 失败重试
- 自动重试：`DagEngine.executeWithRetry` 根据 `NodeDef.retry.maxAttempts/backoff` 执行。
- 人工重试：`POST /api/v1/runs/{runId}/nodes/{nodeId}:retry` 把节点置为 `RETRYING` 并 `attempt+1`。

### E3. 停止关闭
1. `POST /api/v1/runs/{runId}:stop` -> `RunService.stop`：把 run 更新为 `STOPPING`
2. `DagEngine` 在节点执行前/重试循环中调用 `isStopped(runId)`（查 `job_run.status`）
3. 若 STOPPING/STOPPED：
   - `job_run.status=STOPPED`
   - 当前节点标记 `STOPPED`
   - 退出执行

---

## F. 并发与幂等治理（去重key、锁、冲突处理）

### F1. 链定义更新：version 乐观锁
- 字段：`job_chain_definition.version`
- 更新语义：`update ... where chain_id=? and version=?; version=version+1`
- 冲突处理：更新条数为 0 -> 抛 `IllegalStateException` -> 409

### F2. runtime 写入的“可移植 upsert”（避免硬编码 Postgres 方言）
为适配不同数据库，运行态落库采用“update-then-insert”的两段式逻辑（避免 `ON CONFLICT` / `::jsonb`）：
- `RunStorage.createRunIfAbsent`：insert，重复键异常忽略
- `RunStorage.upsertNodeState`：先 update；update=0 再 insert；并发 insert 异常忽略
- `RunStorage.saveCheckpoint/saveArtifact`：同上
- `RunAdminRepository.upsertNode`：同上

> 这能减少未来切 MySQL 的阻力，但仍建议在后续引入 migration（Flyway/Liquibase）与更明确的方言适配层。

### F3. 幂等 key（v0.0 未实现）
- 当前 `chain:start` 每次创建新 `runId`，无 request-id 去重。
- 建议后续：引入 `idempotency_key`（例如 header）并落库唯一约束。

---

## G. 日志与指标（executionId 串联、排障路径）

### G1. 串联 ID
- 推荐以 `runId` 作为 executionId。
- 引擎日志建议打印：`runId/nodeId/attempt`。

### G2. 排障路径（最短）
1. 查 run：`GET /api/v1/runs/{runId}`
2. 查 node：`GET /api/v1/runs/{runId}/nodes`
3. DB 快速定位：
   - `select * from job_run where run_id=?;`
   - `select * from job_run_node where run_id=? order by node_id;`
   - `select * from job_run_checkpoint where run_id=? and node_id=?;`

### G3. 指标（v0.0 未实现，建议）
- run 级：started/success/failed/stopped counter
- node 级：duration timer（started_at/ended_at）
- 队列/线程池：inflight gauge

---

## H. 扩展点（节点类型扩展、参数传递、未来演进）

### H1. 节点类型扩展
- 扩展接口：`com.ruler.one.plugins.NodeExecutor`
  - `String type()`
  - `NodeResult execute(NodeContext ctx, Map<String,Object> cfg)`
- 注册方式：Spring 自动注入 `List<NodeExecutor>` 到 `ExecutorRegistry`
- 新增节点类型：新增一个 `@Component` 实现类，返回新的 `type()`。

### H2. 参数/产物/检查点
- 参数：来自 `NodeDef.cfg`（DAG JSON）
- 产物：`NodeResult.artifact` -> `NodeContext.saveArtifact` -> `job_run_node.artifact_json`
- checkpoint：`NodeResult.checkpoint` -> `job_run_checkpoint.checkpoint_json`

### H3. 未来演进建议
- 调度器：从“一次性 topo 顺序执行”演进到“状态驱动调度”（消费 PENDING/RETRYING，推进依赖）
- 更强 stop：支持 interrupt/timeout、更细粒度的取消语义
- 数据库迁移：引入 Flyway/Liquibase，让 json 类型在不同 DB 上按 schema 演化

---

## I. 安全与权限（如有）

当前：无鉴权。

建议的权限模型（后续引入 Spring Security）：
- `chain:read` / `chain:write`
- `run:read` / `run:control`（stop/retry/complete）

---

## J. 验收用例（>=12条）

> 建议用 Swagger + DB 查询联合验证。

### 链定义（1~6）
1. 创建链成功：POST `/api/v1/chains`，返回 `chainId`，`version=1`。
2. 创建链失败（name 重复）：同名再创建，返回 400。
3. 创建链失败（dagJson 无 nodes）：返回 400（`dag must have nodes`）。
4. 更新链成功（version 正确）：PUT `/api/v1/chains/{id}`，version+1。
5. 更新链失败（version 冲突）：用旧 version 更新，返回 409。
6. disable 后 start：返回 409（chain is disabled）。

### 查询（7~8）
7. page：GET `/api/v1/chains` 支持 keyword/enabled 过滤。
8. detail：GET `/api/v1/chains/{id}` 返回 `dagJson`。

### 运行（9~14）
9. start 创建 run：POST `/api/v1/chains/{id}:start` 返回 `runId`。
10. run 查询：GET `/api/v1/runs/{runId}` 看到状态流转（PENDING->RUNNING->SUCCESS/FAILED）。
11. node 初始化：GET `/api/v1/runs/{runId}/nodes` 至少包含所有 `nodeId`。
12. stop：run RUNNING 时 POST `/api/v1/runs/{runId}:stop`，返回 STOPPING，最终 run STOPPED。
13. node retry 限制：SUCCESS 状态 retry 返回 409；FAILED/STOPPED retry 成功（attempt+1）。
14. node complete 限制：PENDING complete 返回 409；FAILED/STOPPED/RUNNING complete 成功置 SUCCESS。

---

## 参考文档
- DAG topoSort 原理：`doc/DAG_TOPO_SORT.md`

