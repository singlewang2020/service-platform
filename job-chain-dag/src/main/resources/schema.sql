-- Auto-applied by Spring Boot on startup (when spring.sql.init.mode=always)

-- =========================
-- Definition state
-- =========================
create table if not exists job_definition (
    job_id           varchar(64) primary key,
    name             varchar(128) not null unique,
    description      text,
    type             varchar(64) not null,
    enabled          boolean not null default true,
    config_json      jsonb not null default '{}'::jsonb,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create table if not exists job_chain_definition (
    chain_id         varchar(64) primary key,
    name             varchar(128) not null unique,
    description      text,
    enabled          boolean not null default true,
    version          bigint not null default 1,
    dag_json         jsonb not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

-- =========================
-- Runtime state
-- =========================
create table if not exists job_run (
    run_id            varchar(64) primary key,
    chain_id          varchar(64),
    job_id            varchar(64),
    job_name          varchar(128) not null,
    status            varchar(32) not null,          -- PENDING/RUNNING/SUCCESS/FAILED/STOPPING/STOPPED
    dag_json          jsonb not null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index if not exists idx_job_run_chain_id on job_run(chain_id);
create index if not exists idx_job_run_job_id on job_run(job_id);

create table if not exists job_run_node (
    run_id            varchar(64) not null,
    node_id           varchar(128) not null,
    status            varchar(32) not null,          -- PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED/STOPPED
    attempt           int not null default 0,
    last_error        text,
    artifact_json     jsonb,
    started_at        timestamptz,
    ended_at          timestamptz,
    updated_at        timestamptz not null default now(),
    primary key (run_id, node_id)
);

create table if not exists job_run_checkpoint (
    run_id            varchar(64) not null,
    node_id           varchar(128) not null,
    checkpoint_json   jsonb not null,
    updated_at        timestamptz not null default now(),
    primary key (run_id, node_id)
);
