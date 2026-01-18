<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

type Job = {
  jobId: string
  name: string
  description: string
  type: string
  enabled: boolean
  configJson: string
  createdAt: string
  updatedAt: string
}

type Chain = {
  chainId: string
  name: string
  description: string
  enabled: boolean
  version: number
  dagJson: string
  createdAt: string
  updatedAt: string
}

type NodeRow = {
  runId: string
  nodeId: string
  status: string
  attempt: number
  lastError: string
  artifactJson: string
  startedAt: string
  endedAt: string
  updatedAt: string
}

type Run = {
  runId: string
  jobName: string
  status: string
  dagJson: string
  createdAt: string
  updatedAt: string
  nodes: NodeRow[]
}

type Toast = {
  message: string
  tone: 'success' | 'warning' | 'danger'
}

const storedAuth = localStorage.getItem('jc_auth')
const isAuthenticated = ref(storedAuth === '1')
const currentView = ref<'dashboard' | 'jobs' | 'chains' | 'runs'>('dashboard')
const toast = ref<Toast | null>(null)

const loginForm = reactive({
  username: '',
  password: ''
})
const loginError = ref('')

const jobFilters = reactive({
  keyword: '',
  enabled: 'all'
})

const chainFilters = reactive({
  keyword: '',
  enabled: 'all'
})

const runFilters = reactive({
  keyword: ''
})

const jobForm = reactive({
  name: '',
  description: '',
  type: 'shell',
  configJson: '{"cmd": "echo hello"}',
  enabled: true
})

const chainForm = reactive({
  name: '',
  description: '',
  dagJson: '{"nodes":[{"id":"node-a"},{"id":"node-b"}],"edges":[["node-a","node-b"]]}',
  enabled: true
})

const nowIso = () => new Date().toISOString()

const jobs = ref<Job[]>([
  {
    jobId: 'job-1001',
    name: 'daily-settlement',
    description: 'run settlement batch',
    type: 'spark',
    enabled: true,
    configJson: '{"class":"SettlementJob","args":["--region=cn"]}',
    createdAt: '2026-01-10T08:12:20Z',
    updatedAt: '2026-01-16T12:30:00Z'
  },
  {
    jobId: 'job-1002',
    name: 'data-quality',
    description: 'DQ pipeline checks',
    type: 'python',
    enabled: false,
    configJson: '{"module":"dq.entry","retries":2}',
    createdAt: '2026-01-12T03:30:20Z',
    updatedAt: '2026-01-13T07:55:00Z'
  },
  {
    jobId: 'job-1003',
    name: 'report-export',
    description: 'export BI reports',
    type: 'http',
    enabled: true,
    configJson: '{"url":"https://example/report","method":"POST"}',
    createdAt: '2026-01-15T13:10:05Z',
    updatedAt: '2026-01-15T13:10:05Z'
  }
])

const chains = ref<Chain[]>([
  {
    chainId: 'chain-2001',
    name: 'billing-dag',
    description: 'invoice -> settlement -> notify',
    enabled: true,
    version: 3,
    dagJson: '{"nodes":[{"id":"invoice"},{"id":"settlement"},{"id":"notify"}],"edges":[["invoice","settlement"],["settlement","notify"]]}',
    createdAt: '2026-01-11T09:20:00Z',
    updatedAt: '2026-01-17T11:05:00Z'
  },
  {
    chainId: 'chain-2002',
    name: 'risk-audit',
    description: 'collect -> score -> alert',
    enabled: false,
    version: 1,
    dagJson: '{"nodes":[{"id":"collect"},{"id":"score"},{"id":"alert"}],"edges":[["collect","score"],["score","alert"]]}',
    createdAt: '2026-01-09T06:40:00Z',
    updatedAt: '2026-01-14T18:00:00Z'
  }
])

const runs = ref<Run[]>([
  {
    runId: 'run-3001',
    jobName: 'billing-dag',
    status: 'RUNNING',
    dagJson: chains.value[0].dagJson,
    createdAt: '2026-01-18T02:10:00Z',
    updatedAt: '2026-01-18T02:25:00Z',
    nodes: [
      {
        runId: 'run-3001',
        nodeId: 'invoice',
        status: 'SUCCESS',
        attempt: 1,
        lastError: '',
        artifactJson: '{"rows":1200}',
        startedAt: '2026-01-18T02:10:00Z',
        endedAt: '2026-01-18T02:12:00Z',
        updatedAt: '2026-01-18T02:12:00Z'
      },
      {
        runId: 'run-3001',
        nodeId: 'settlement',
        status: 'RUNNING',
        attempt: 1,
        lastError: '',
        artifactJson: '',
        startedAt: '2026-01-18T02:12:10Z',
        endedAt: '',
        updatedAt: '2026-01-18T02:25:00Z'
      },
      {
        runId: 'run-3001',
        nodeId: 'notify',
        status: 'WAITING',
        attempt: 0,
        lastError: '',
        artifactJson: '',
        startedAt: '',
        endedAt: '',
        updatedAt: '2026-01-18T02:10:00Z'
      }
    ]
  },
  {
    runId: 'run-3002',
    jobName: 'daily-settlement',
    status: 'FAILED',
    dagJson: '{"nodes":[{"id":"extract"},{"id":"load"}],"edges":[["extract","load"]]}',
    createdAt: '2026-01-17T05:20:00Z',
    updatedAt: '2026-01-17T05:35:00Z',
    nodes: [
      {
        runId: 'run-3002',
        nodeId: 'extract',
        status: 'SUCCESS',
        attempt: 1,
        lastError: '',
        artifactJson: '{"files":12}',
        startedAt: '2026-01-17T05:20:00Z',
        endedAt: '2026-01-17T05:24:00Z',
        updatedAt: '2026-01-17T05:24:00Z'
      },
      {
        runId: 'run-3002',
        nodeId: 'load',
        status: 'FAILED',
        attempt: 2,
        lastError: 'timeout on downstream warehouse',
        artifactJson: '',
        startedAt: '2026-01-17T05:24:10Z',
        endedAt: '2026-01-17T05:35:00Z',
        updatedAt: '2026-01-17T05:35:00Z'
      }
    ]
  }
])

const selectedRunId = ref(runs.value[0]?.runId ?? '')
const selectedRun = computed(() => runs.value.find((run) => run.runId === selectedRunId.value) ?? null)

const filteredJobs = computed(() => {
  return jobs.value.filter((job) => {
    const matchesKeyword = jobFilters.keyword.trim() === '' || job.name.includes(jobFilters.keyword) || job.description.includes(jobFilters.keyword)
    const matchesEnabled =
      jobFilters.enabled === 'all' ||
      (jobFilters.enabled === 'enabled' && job.enabled) ||
      (jobFilters.enabled === 'disabled' && !job.enabled)
    return matchesKeyword && matchesEnabled
  })
})

const filteredChains = computed(() => {
  return chains.value.filter((chain) => {
    const matchesKeyword =
      chainFilters.keyword.trim() === '' ||
      chain.name.includes(chainFilters.keyword) ||
      chain.description.includes(chainFilters.keyword)
    const matchesEnabled =
      chainFilters.enabled === 'all' ||
      (chainFilters.enabled === 'enabled' && chain.enabled) ||
      (chainFilters.enabled === 'disabled' && !chain.enabled)
    return matchesKeyword && matchesEnabled
  })
})

const filteredRuns = computed(() => {
  return runs.value.filter((run) => {
    const matchesKeyword = runFilters.keyword.trim() === '' || run.runId.includes(runFilters.keyword) || run.jobName.includes(runFilters.keyword)
    return matchesKeyword
  })
})

const navItems = [
  { id: 'dashboard', label: '控制台' },
  { id: 'jobs', label: 'Jobs' },
  { id: 'chains', label: 'Chains' },
  { id: 'runs', label: 'Runs' }
] as const

const summaryCards = computed(() => [
  { label: 'Jobs 总数', value: jobs.value.length, accent: 'teal' },
  { label: '启用 Job', value: jobs.value.filter((job) => job.enabled).length, accent: 'lime' },
  { label: 'Chains 总数', value: chains.value.length, accent: 'gold' },
  { label: '运行中', value: runs.value.filter((run) => run.status === 'RUNNING').length, accent: 'rose' }
])

const notify = (message: string, tone: Toast['tone']) => {
  toast.value = { message, tone }
  window.setTimeout(() => {
    toast.value = null
  }, 2400)
}

const handleLogin = () => {
  loginError.value = ''
  const username = loginForm.username.trim()
  const password = loginForm.password.trim()
  if (username === 'admin' && password === '123456') {
    isAuthenticated.value = true
    localStorage.setItem('jc_auth', '1')
    notify('欢迎回来，管理员', 'success')
    loginForm.username = ''
    loginForm.password = ''
    return
  }
  loginError.value = '账号或密码错误'
}

const handleLogout = () => {
  isAuthenticated.value = false
  localStorage.removeItem('jc_auth')
  currentView.value = 'dashboard'
}

const createJob = () => {
  if (!jobForm.name.trim() || !jobForm.type.trim()) {
    notify('请填写 Job 名称与类型', 'warning')
    return
  }
  const now = nowIso()
  jobs.value.unshift({
    jobId: `job-${Date.now()}`,
    name: jobForm.name,
    description: jobForm.description,
    type: jobForm.type,
    enabled: jobForm.enabled,
    configJson: jobForm.configJson,
    createdAt: now,
    updatedAt: now
  })
  jobForm.name = ''
  jobForm.description = ''
  jobForm.type = 'shell'
  jobForm.configJson = '{"cmd": "echo hello"}'
  jobForm.enabled = true
  notify('已创建 Job', 'success')
}

const toggleJob = (job: Job) => {
  job.enabled = !job.enabled
  job.updatedAt = nowIso()
  notify(job.enabled ? 'Job 已启用' : 'Job 已停用', 'success')
}

const removeJob = (job: Job) => {
  jobs.value = jobs.value.filter((item) => item.jobId !== job.jobId)
  notify('Job 已删除', 'danger')
}

const startJob = (job: Job) => {
  const now = nowIso()
  const runId = `run-${Date.now()}`
  runs.value.unshift({
    runId,
    jobName: job.name,
    status: 'RUNNING',
    dagJson: '{"nodes":[{"id":"task-a"},{"id":"task-b"}],"edges":[["task-a","task-b"]]}',
    createdAt: now,
    updatedAt: now,
    nodes: [
      {
        runId,
        nodeId: 'task-a',
        status: 'RUNNING',
        attempt: 1,
        lastError: '',
        artifactJson: '',
        startedAt: now,
        endedAt: '',
        updatedAt: now
      },
      {
        runId,
        nodeId: 'task-b',
        status: 'WAITING',
        attempt: 0,
        lastError: '',
        artifactJson: '',
        startedAt: '',
        endedAt: '',
        updatedAt: now
      }
    ]
  })
  selectedRunId.value = runId
  notify('已触发运行', 'success')
}

const createChain = () => {
  if (!chainForm.name.trim() || !chainForm.dagJson.trim()) {
    notify('请填写 Chain 名称与 DAG', 'warning')
    return
  }
  const now = nowIso()
  chains.value.unshift({
    chainId: `chain-${Date.now()}`,
    name: chainForm.name,
    description: chainForm.description,
    enabled: chainForm.enabled,
    version: 1,
    dagJson: chainForm.dagJson,
    createdAt: now,
    updatedAt: now
  })
  chainForm.name = ''
  chainForm.description = ''
  chainForm.dagJson = '{"nodes":[{"id":"node-a"},{"id":"node-b"}],"edges":[["node-a","node-b"]]}'
  chainForm.enabled = true
  notify('已创建 Chain', 'success')
}

const toggleChain = (chain: Chain) => {
  chain.enabled = !chain.enabled
  chain.updatedAt = nowIso()
  notify(chain.enabled ? 'Chain 已启用' : 'Chain 已停用', 'success')
}

const startChain = (chain: Chain) => {
  const now = nowIso()
  const runId = `run-${Date.now()}`
  runs.value.unshift({
    runId,
    jobName: chain.name,
    status: 'RUNNING',
    dagJson: chain.dagJson,
    createdAt: now,
    updatedAt: now,
    nodes: [
      {
        runId,
        nodeId: 'node-a',
        status: 'RUNNING',
        attempt: 1,
        lastError: '',
        artifactJson: '',
        startedAt: now,
        endedAt: '',
        updatedAt: now
      },
      {
        runId,
        nodeId: 'node-b',
        status: 'WAITING',
        attempt: 0,
        lastError: '',
        artifactJson: '',
        startedAt: '',
        endedAt: '',
        updatedAt: now
      }
    ]
  })
  selectedRunId.value = runId
  notify('Chain 运行已启动', 'success')
}

const stopRun = (run: Run) => {
  run.status = 'STOPPED'
  run.updatedAt = nowIso()
  notify('运行已停止', 'warning')
}

const retryNode = (node: NodeRow) => {
  node.status = 'RUNNING'
  node.attempt += 1
  node.lastError = ''
  node.updatedAt = nowIso()
  notify('节点重试中', 'success')
}

const completeNode = (node: NodeRow) => {
  node.status = 'SUCCESS'
  node.updatedAt = nowIso()
  notify('节点已完成', 'success')
}

const formatStatusTone = (status: string) => {
  switch (status) {
    case 'RUNNING':
      return 'chip chip-running'
    case 'SUCCESS':
      return 'chip chip-success'
    case 'FAILED':
      return 'chip chip-failed'
    case 'STOPPED':
      return 'chip chip-muted'
    case 'WAITING':
      return 'chip chip-waiting'
    default:
      return 'chip chip-muted'
  }
}
</script>

<template>
  <div class="app">
    <transition name="fade">
      <div v-if="toast" class="toast" :class="toast.tone">
        {{ toast.message }}
      </div>
    </transition>

    <section v-if="!isAuthenticated" class="login">
      <div class="login-card">
        <div class="login-brand">
          <span class="brand-dot"></span>
          Job Chain Control
        </div>
        <h1>欢迎回来</h1>
        <p class="sub">统一管理任务编排、运行与节点状态</p>
        <form class="login-form" @submit.prevent="handleLogin">
          <label>
            <span>账号</span>
            <input v-model="loginForm.username" type="text" placeholder="admin" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" placeholder="123456" />
          </label>
          <button class="primary" type="submit">登录</button>
        </form>
        <p v-if="loginError" class="error">{{ loginError }}</p>
        <div class="hint">测试账号：admin / 123456</div>
      </div>
      <div class="login-visual">
        <div class="orb orb-a"></div>
        <div class="orb orb-b"></div>
        <div class="orb orb-c"></div>
        <div class="signal">
          <div class="signal-line" v-for="i in 5" :key="i" :style="`--i:${i}`"></div>
        </div>
      </div>
    </section>

    <section v-else class="workspace">
      <aside class="side">
        <div class="brand">
          <span class="brand-dot"></span>
          Job Chain
        </div>
        <nav class="nav">
          <button
            v-for="item in navItems"
            :key="item.id"
            class="nav-item"
            :class="{ active: currentView === item.id }"
            @click="currentView = item.id"
          >
            <span class="nav-dot"></span>
            {{ item.label }}
          </button>
        </nav>
        <div class="side-footer">
          <div class="profile">
            <div class="avatar">AD</div>
            <div>
              <div class="name">Admin</div>
              <div class="role">Platform Owner</div>
            </div>
          </div>
          <button class="ghost" @click="handleLogout">退出登录</button>
        </div>
      </aside>

      <main class="main">
        <header class="top">
          <div>
            <h2>{{ currentView === 'dashboard' ? '控制台概览' : currentView === 'jobs' ? 'Job 管理' : currentView === 'chains' ? 'Chain 管理' : '运行管理' }}</h2>
            <p class="sub">连接 job-chain-dag OpenAPI 的前端控制台</p>
          </div>
          <div class="top-actions">
            <div class="pill">环境: DEV</div>
            <div class="pill">/api/v1</div>
          </div>
        </header>

        <section v-if="currentView === 'dashboard'" class="content">
          <div class="grid four">
            <div v-for="(card, idx) in summaryCards" :key="card.label" class="stat" :style="`--i:${idx}`">
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-accent" :class="card.accent"></div>
            </div>
          </div>
          <div class="grid two">
            <div class="panel">
              <div class="panel-head">
                <h3>运行中的 Chains</h3>
                <span class="badge">{{ runs.filter((r) => r.status === 'RUNNING').length }}</span>
              </div>
              <div class="panel-body">
                <div
                  v-for="(run, idx) in runs.filter((r) => r.status === 'RUNNING')"
                  :key="run.runId"
                  class="run-row"
                  :style="`--i:${idx}`"
                >
                  <div>
                    <div class="row-title">{{ run.jobName }}</div>
                    <div class="row-sub">{{ run.runId }}</div>
                  </div>
                  <span :class="formatStatusTone(run.status)">{{ run.status }}</span>
                </div>
              </div>
            </div>
            <div class="panel">
              <div class="panel-head">
                <h3>近期变更</h3>
                <span class="badge">{{ jobs.length + chains.length }}</span>
              </div>
              <div class="panel-body">
                <div class="activity" v-for="(job, idx) in jobs.slice(0, 4)" :key="job.jobId" :style="`--i:${idx}`">
                  <div class="dot"></div>
                  <div>
                    <div class="row-title">{{ job.name }}</div>
                    <div class="row-sub">{{ job.updatedAt }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="currentView === 'jobs'" class="content">
          <div class="grid two">
            <div class="panel">
              <div class="panel-head">
                <h3>新建 Job</h3>
                <span class="badge">/api/v1/jobs</span>
              </div>
              <div class="panel-body form">
                <label>
                  <span>名称</span>
                  <input v-model="jobForm.name" type="text" placeholder="job name" />
                </label>
                <label>
                  <span>描述</span>
                  <input v-model="jobForm.description" type="text" placeholder="short description" />
                </label>
                <label>
                  <span>类型</span>
                  <input v-model="jobForm.type" type="text" placeholder="spark/python/http" />
                </label>
                <label>
                  <span>配置 JSON</span>
                  <textarea v-model="jobForm.configJson" rows="4"></textarea>
                </label>
                <label class="inline">
                  <input v-model="jobForm.enabled" type="checkbox" />
                  <span>启用</span>
                </label>
                <button class="primary" @click="createJob">创建</button>
              </div>
            </div>

            <div class="panel">
              <div class="panel-head">
                <h3>过滤</h3>
              </div>
              <div class="panel-body form">
                <label>
                  <span>关键词</span>
                  <input v-model="jobFilters.keyword" type="text" placeholder="name / description" />
                </label>
                <label>
                  <span>状态</span>
                  <select v-model="jobFilters.enabled">
                    <option value="all">全部</option>
                    <option value="enabled">启用</option>
                    <option value="disabled">停用</option>
                  </select>
                </label>
                <div class="muted">共 {{ filteredJobs.length }} 条</div>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <h3>Job 列表</h3>
              <span class="badge">{{ filteredJobs.length }}</span>
            </div>
            <div class="panel-body table">
              <div class="table-row table-head">
                <span>名称</span>
                <span>类型</span>
                <span>状态</span>
                <span>更新</span>
                <span>动作</span>
              </div>
              <div class="table-row" v-for="(job, idx) in filteredJobs" :key="job.jobId" :style="`--i:${idx}`">
                <span>
                  <strong>{{ job.name }}</strong>
                  <small>{{ job.description }}</small>
                </span>
                <span>{{ job.type }}</span>
                <span :class="job.enabled ? 'chip chip-success' : 'chip chip-muted'">{{ job.enabled ? '启用' : '停用' }}</span>
                <span>{{ job.updatedAt }}</span>
                <span class="row-actions">
                  <button class="ghost" @click="toggleJob(job)">{{ job.enabled ? '停用' : '启用' }}</button>
                  <button class="ghost" @click="startJob(job)">运行</button>
                  <button class="ghost danger" @click="removeJob(job)">删除</button>
                </span>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="currentView === 'chains'" class="content">
          <div class="grid two">
            <div class="panel">
              <div class="panel-head">
                <h3>新建 Chain</h3>
                <span class="badge">/api/v1/chains</span>
              </div>
              <div class="panel-body form">
                <label>
                  <span>名称</span>
                  <input v-model="chainForm.name" type="text" placeholder="chain name" />
                </label>
                <label>
                  <span>描述</span>
                  <input v-model="chainForm.description" type="text" placeholder="short description" />
                </label>
                <label>
                  <span>DAG JSON</span>
                  <textarea v-model="chainForm.dagJson" rows="5"></textarea>
                </label>
                <label class="inline">
                  <input v-model="chainForm.enabled" type="checkbox" />
                  <span>启用</span>
                </label>
                <button class="primary" @click="createChain">创建</button>
              </div>
            </div>

            <div class="panel">
              <div class="panel-head">
                <h3>过滤</h3>
              </div>
              <div class="panel-body form">
                <label>
                  <span>关键词</span>
                  <input v-model="chainFilters.keyword" type="text" placeholder="name / description" />
                </label>
                <label>
                  <span>状态</span>
                  <select v-model="chainFilters.enabled">
                    <option value="all">全部</option>
                    <option value="enabled">启用</option>
                    <option value="disabled">停用</option>
                  </select>
                </label>
                <div class="muted">共 {{ filteredChains.length }} 条</div>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <h3>Chain 列表</h3>
              <span class="badge">{{ filteredChains.length }}</span>
            </div>
            <div class="panel-body table">
              <div class="table-row table-head">
                <span>名称</span>
                <span>版本</span>
                <span>状态</span>
                <span>更新</span>
                <span>动作</span>
              </div>
              <div class="table-row" v-for="(chain, idx) in filteredChains" :key="chain.chainId" :style="`--i:${idx}`">
                <span>
                  <strong>{{ chain.name }}</strong>
                  <small>{{ chain.description }}</small>
                </span>
                <span>v{{ chain.version }}</span>
                <span :class="chain.enabled ? 'chip chip-success' : 'chip chip-muted'">{{ chain.enabled ? '启用' : '停用' }}</span>
                <span>{{ chain.updatedAt }}</span>
                <span class="row-actions">
                  <button class="ghost" @click="toggleChain(chain)">{{ chain.enabled ? '停用' : '启用' }}</button>
                  <button class="ghost" @click="startChain(chain)">运行</button>
                </span>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <h3>DAG 预览</h3>
              <span class="badge">JSON</span>
            </div>
            <div class="panel-body">
              <div class="grid two">
                <div v-for="chain in filteredChains" :key="chain.chainId" class="dag-card">
                  <div class="row-title">{{ chain.name }}</div>
                  <pre>{{ chain.dagJson }}</pre>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section v-else class="content">
          <div class="grid two">
            <div class="panel">
              <div class="panel-head">
                <h3>运行筛选</h3>
              </div>
              <div class="panel-body form">
                <label>
                  <span>关键词</span>
                  <input v-model="runFilters.keyword" type="text" placeholder="runId / jobName" />
                </label>
                <div class="muted">共 {{ filteredRuns.length }} 条</div>
              </div>
            </div>
            <div class="panel">
              <div class="panel-head">
                <h3>运行详情</h3>
                <span class="badge">/api/v1/runs</span>
              </div>
              <div v-if="selectedRun" class="panel-body">
                <div class="detail-line"><span>Run ID</span><strong>{{ selectedRun.runId }}</strong></div>
                <div class="detail-line"><span>Job/Chain</span><strong>{{ selectedRun.jobName }}</strong></div>
                <div class="detail-line"><span>状态</span><span :class="formatStatusTone(selectedRun.status)">{{ selectedRun.status }}</span></div>
                <div class="detail-line"><span>创建</span><strong>{{ selectedRun.createdAt }}</strong></div>
                <div class="detail-line"><span>更新</span><strong>{{ selectedRun.updatedAt }}</strong></div>
                <button class="ghost" @click="stopRun(selectedRun)">停止运行</button>
              </div>
              <div v-else class="panel-body muted">请选择运行记录</div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <h3>运行列表</h3>
              <span class="badge">{{ filteredRuns.length }}</span>
            </div>
            <div class="panel-body table">
              <div class="table-row table-head">
                <span>Run ID</span>
                <span>名称</span>
                <span>状态</span>
                <span>更新</span>
                <span>动作</span>
              </div>
              <div class="table-row" v-for="(run, idx) in filteredRuns" :key="run.runId" :style="`--i:${idx}`">
                <span>{{ run.runId }}</span>
                <span>{{ run.jobName }}</span>
                <span :class="formatStatusTone(run.status)">{{ run.status }}</span>
                <span>{{ run.updatedAt }}</span>
                <span class="row-actions">
                  <button class="ghost" @click="selectedRunId = run.runId">详情</button>
                </span>
              </div>
            </div>
          </div>

          <div class="panel" v-if="selectedRun">
            <div class="panel-head">
              <h3>节点执行状态</h3>
              <span class="badge">/api/v1/runs/{{ selectedRun.runId }}/nodes</span>
            </div>
            <div class="panel-body table">
              <div class="table-row table-head">
                <span>节点</span>
                <span>状态</span>
                <span>尝试</span>
                <span>错误</span>
                <span>动作</span>
              </div>
              <div class="table-row" v-for="(node, idx) in selectedRun.nodes" :key="node.nodeId" :style="`--i:${idx}`">
                <span>{{ node.nodeId }}</span>
                <span :class="formatStatusTone(node.status)">{{ node.status }}</span>
                <span>{{ node.attempt }}</span>
                <span>{{ node.lastError || '-' }}</span>
                <span class="row-actions">
                  <button class="ghost" @click="retryNode(node)">重试</button>
                  <button class="ghost" @click="completeNode(node)">完成</button>
                </span>
              </div>
            </div>
          </div>
        </section>
      </main>
    </section>
  </div>
</template>

