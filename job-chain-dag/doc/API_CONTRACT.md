# JobChain DAG - API Contract（v0.0 / A 方案）

> A 方案：**Workflow = Chain Definition（`job_chain_definition`）**。
> 
> - 本文档以对外口径统一使用 `Workflow` 命名。
> - 代码内部仍可能沿用 `Chain`（例如 `ChainController`）。
>
> 目标：把接口的**路径、入参/出参（JSON shape）、幂等、错误码、状态机与 Graph DTO**讲清楚，便于前后端协作与实现对齐。

---

## 0. 约定

### 0.1 Content-Type
- 请求：`Content-Type: application/json`
- 响应：`application/json`

### 0.2 ID 约定
- `jobId`：字符串（建议 UUID）
- `workflowId`：字符串（链路定义 id）
- `runId`：字符串（一次执行 id）
- `nodeId`：字符串（DAG 内节点 id，业务可读）

### 0.3 时间
- 统一使用 ISO-8601 字符串（后端内部可用 timestamptz）

### 0.4 通用分页
请求参数（query）：
- `page`（从 1 开始）
- `size`

通用响应：
```json
{
  "page": 1,
  "size": 20,
  "total": 123,
  "items": []
}
```

### 0.5 通用错误响应
```json
{
  "code": "WORKFLOW_NOT_FOUND",
  "message": "workflow not found",
  "details": {
    "workflowId": "..."
  }
}
```

---

## 1. 错误码（建议枚举）

> 为减少前端分支与排障成本，建议错误码稳定，message 可变化。

### 1.1 通用
- `VALIDATION_ERROR` (400)
- `JSON_PARSE_ERROR` (400)
- `INTERNAL_ERROR` (500)

### 1.2 Job
- `JOB_NOT_FOUND` (404)
- `JOB_NAME_CONFLICT` (409)
- `JOB_DISABLED` (400)

### 1.3 Workflow
- `WORKFLOW_NOT_FOUND` (404)
- `WORKFLOW_NAME_CONFLICT` (409)
- `WORKFLOW_DISABLED` (400)
- `WORKFLOW_DAG_INVALID` (400)
- `WORKFLOW_VERSION_CONFLICT` (409)（可选：启用乐观锁时）

### 1.4 Run / Node
- `RUN_NOT_FOUND` (404)
- `RUN_NOT_RUNNING` (409)
- `RUN_TERMINAL` (409)（run 已 SUCCESS/FAILED/CLOSED）
- `NODE_NOT_FOUND` (404)
- `NODE_NOT_RETRYABLE` (409)
- `NODE_NOT_COMPLETABLE` (409)

---

## 2. 状态机字段（对外口径）

### 2.1 RunStatus
- `PENDING`
- `RUNNING`
- `STOPPING`
- `SUCCESS`
- `FAILED`
- `CLOSED`

### 2.2 NodeStatus
- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `RETRYING`（或 `RETRY_WAITING`，建议统一一种）
- `SKIPPED`

---

## 3. Graph DTO（统一命名，前端可复用）

> 目标：RunGraph 与 WorkflowGraph 使用同一套 DTO 结构，前端画图逻辑复用。

### 3.1 GraphEdge
```json
{
  "from": "n1",
  "to": "n2",
  "type": "DEPENDS_ON",
  "index": 0
}
```

- `type`：边类型
  - `DEPENDS_ON`：依赖边（默认）
- `index`：可选，用于 UI 排序（比如同一节点多条入边/出边绘制顺序）

### 3.2 JobSummary（节点挂载的 job 信息）
```json
{
  "jobId": "uuid",
  "name": "demo-job",
  "type": "shell",
  "enabled": true,
  "description": "..."
}
```

### 3.3 GraphNodeWithJob
```json
{
  "nodeId": "n1",
  "job": {
    "jobId": "uuid",
    "name": "demo-job",
    "type": "shell",
    "enabled": true
  },
  "dependsOn": [],
  "retry": {
    "maxAttempts": 3,
    "backoffMillis": 1000,
    "backoffMultiplier": 1.0
  }
}
```

### 3.4 WorkflowGraphResponse
```json
{
  "workflowId": "...",
  "version": 1,
  "nodes": [/* GraphNodeWithJob */],
  "edges": [/* GraphEdge */]
}
```

### 3.5 RunGraphResponse
```json
{
  "runId": "...",
  "workflowId": "...",
  "status": "RUNNING",
  "nodes": [
    {
      "nodeId": "n1",
      "job": { "jobId": "...", "name": "...", "type": "shell", "enabled": true },
      "dependsOn": [],
      "retry": {"maxAttempts": 1, "backoffMillis": 0, "backoffMultiplier": 1.0},
      "state": {
        "status": "SUCCESS",
        "attempt": 1,
        "lastError": null,
        "startedAt": "2026-01-01T00:00:00Z",
        "endedAt": "2026-01-01T00:00:01Z"
      },
      "artifact": {},
      "checkpoint": {}
    }
  ],
  "edges": [/* GraphEdge */]
}
```

> 说明：
> - `artifact/checkpoint` 的 shape 保持开放（JSON），v0.0 不强约束 schema。
> - `state` 来自 `job_run_node` 运行态；job 来自 join `job_definition`。

---

## 4. API：Job

### 4.1 创建 Job
**POST** `/jobs`

Request:
```json
{
  "name": "demo-job",
  "description": "...",
  "type": "shell",
  "configJson": { "cmd": "echo hello" },
  "enabled": true
}
```

Response (201):
```json
{
  "jobId": "uuid",
  "name": "demo-job",
  "description": "...",
  "type": "shell",
  "configJson": { "cmd": "echo hello" },
  "enabled": true,
  "createdAt": "...",
  "updatedAt": "..."
}
```

Errors:
- 409 `JOB_NAME_CONFLICT`
- 400 `VALIDATION_ERROR`

### 4.2 更新 Job
**PUT** `/jobs/{jobId}`

Request:
```json
{
  "description": "...",
  "type": "shell",
  "configJson": { "cmd": "echo hello" },
  "enabled": true
}
```

Response (200): Job detail

Errors:
- 404 `JOB_NOT_FOUND`

### 4.3 分页查询 Job
**GET** `/jobs?page=1&size=20&keyword=demo&enabled=true&type=shell`

Response (200):
```json
{
  "page": 1,
  "size": 20,
  "total": 1,
  "items": [
    { "jobId": "uuid", "name": "demo-job", "type": "shell", "enabled": true, "updatedAt": "..." }
  ]
}
```

### 4.4 Job 详情
**GET** `/jobs/{jobId}`

Errors:
- 404 `JOB_NOT_FOUND`

### 4.5 启用/禁用
**POST** `/jobs/{jobId}:enable`

**POST** `/jobs/{jobId}:disable`

Errors:
- 404 `JOB_NOT_FOUND`

---

## 5. API：Workflow（= chain definition）

### 5.1 创建 Workflow
**POST** `/workflows`

Request:
```json
{
  "name": "demo-workflow",
  "description": "...",
  "enabled": true,
  "dag": {
    "job": "demo",
    "nodes": [
      {
        "id": "n1",
        "jobId": "job-uuid-1",
        "dependsOn": [],
        "retry": {"maxAttempts": 1, "backoffMillis": 0, "backoffMultiplier": 1.0}
      },
      {
        "id": "n2",
        "jobId": "job-uuid-2",
        "dependsOn": ["n1"],
        "retry": {"maxAttempts": 3, "backoffMillis": 1000, "backoffMultiplier": 1.0}
      }
    ]
  }
}
```

Response (201):
```json
{
  "workflowId": "uuid",
  "name": "demo-workflow",
  "description": "...",
  "enabled": true,
  "version": 1,
  "dag": { /* 原样返回 */ },
  "createdAt": "...",
  "updatedAt": "..."
}
```

Errors:
- 409 `WORKFLOW_NAME_CONFLICT`
- 400 `WORKFLOW_DAG_INVALID`
- 400 `JOB_NOT_FOUND` / `JOB_DISABLED`

### 5.2 更新 Workflow
**PUT** `/workflows/{workflowId}`

Request：同创建（允许部分字段）

Response (200)：workflow detail（`version` 自增）

Errors:
- 404 `WORKFLOW_NOT_FOUND`
- 409 `WORKFLOW_VERSION_CONFLICT`（可选）

### 5.3 分页查询 Workflow
**GET** `/workflows?page=1&size=20&keyword=demo&enabled=true`

Response (200):
```json
{
  "page": 1,
  "size": 20,
  "total": 1,
  "items": [
    { "workflowId": "uuid", "name": "demo-workflow", "enabled": true, "version": 1, "updatedAt": "..." }
  ]
}
```

### 5.4 Workflow 详情
**GET** `/workflows/{workflowId}`

Response (200)：包含 dag（或 dagJson 解析后的结构）

Errors:
- 404 `WORKFLOW_NOT_FOUND`

### 5.5 Workflow Graph
**GET** `/workflows/{workflowId}/graph`

Response (200): `WorkflowGraphResponse`

Errors:
- 404 `WORKFLOW_NOT_FOUND`

---

## 6. API：Run

### 6.1 启动运行
**POST** `/workflows/{workflowId}:start`

Request（可选字段）：
```json
{
  "runId": "optional-client-run-id",
  "idempotencyKey": "optional-key"
}
```

Response (200/201):
```json
{
  "runId": "...",
  "workflowId": "...",
  "status": "PENDING",
  "createdAt": "..."
}
```

幂等：
- 如果提供 `runId`：以 `runId` 为幂等键；重复调用返回同一个 run
- 如果仅提供 `idempotencyKey`：建议后续落库实现去重；v0.0 可先不实现，但文档先保留接口字段

Errors:
- 404 `WORKFLOW_NOT_FOUND`
- 400 `WORKFLOW_DISABLED`
- 400 `WORKFLOW_DAG_INVALID`

### 6.2 查询 run 列表
**GET** `/runs?page=1&size=20&workflowId=...&status=RUNNING`

Response：分页

### 6.3 run 详情
**GET** `/runs/{runId}`

Response (200) 建议结构：
```json
{
  "runId": "...",
  "workflowId": "...",
  "status": "RUNNING",
  "dag": { /* DAG 快照 */ },
  "nodes": [
    {
      "nodeId": "n1",
      "status": "SUCCESS",
      "attempt": 1,
      "lastError": null,
      "artifact": {},
      "checkpoint": {},
      "startedAt": "...",
      "endedAt": "...",
      "job": { "jobId": "...", "name": "...", "type": "shell", "enabled": true }
    }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

Errors:
- 404 `RUN_NOT_FOUND`

### 6.4 停止 run（软停止）
**POST** `/runs/{runId}:stop`

语义：
- 将 run 置为 `STOPPING`
- 不再调度新节点（PENDING/RETRYING 不再进入 RUNNING）

Errors:
- 404 `RUN_NOT_FOUND`
- 409 `RUN_NOT_RUNNING`

### 6.5 关闭 run（强制终态）
**POST** `/runs/{runId}:close`

语义：
- 将 run 标记为 `CLOSED`
- 后续不再推进状态

Errors:
- 404 `RUN_NOT_FOUND`

### 6.6 Run Graph（可选独立接口）
> 如果 `GET /runs/{runId}` 已包含图数据，可不单独提供。

**GET** `/runs/{runId}/graph`

Response：`RunGraphResponse`

---

## 7. API：NodeRun 控制

### 7.1 单节点重试
**POST** `/runs/{runId}/nodes/{nodeId}:retry`

语义：
- node 状态必须可重试（通常 `FAILED`）
- 变更为可调度状态（`PENDING` 或 `RETRYING`），并推进 attempt

Errors:
- 404 `RUN_NOT_FOUND`
- 404 `NODE_NOT_FOUND`
- 409 `NODE_NOT_RETRYABLE`
- 409 `RUN_TERMINAL`

### 7.2 单节点人工完成
**POST** `/runs/{runId}/nodes/{nodeId}:complete`

Request（可选）：
```json
{
  "artifact": {},
  "checkpoint": {},
  "reason": "manual override"
}
```

Errors:
- 404 `RUN_NOT_FOUND`
- 404 `NODE_NOT_FOUND`
- 409 `NODE_NOT_COMPLETABLE`
- 409 `RUN_TERMINAL`

---

## 8. 并发与幂等（v0.0建议）

- start：推荐使用 `runId` 幂等（主键去重）
- node 状态推进：更新时应带“期望前置状态”（CAS），避免重复调度
- stop/close：建议幂等（重复调用返回 200 或 409，需统一策略）

---

## 9. 兼容性策略（从 Chain 命名过渡到 Workflow 命名）

建议：
- 对外新增 `/workflows`，并保留 `/chains` 作为兼容别名（至少一个版本周期）
- DTO 字段统一使用 `workflowId` 对外返回；内部字段仍可用 `chainId`

