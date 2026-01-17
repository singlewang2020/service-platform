-- 1) 一次 DAG run
create table if not exists job_run (
    run_id            varchar(64) primary key,
    job_name          varchar(128) not null,
    status            varchar(32) not null,          -- RUNNING/SUCCESS/FAILED
    dag_json          jsonb not null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

-- 2) 每个节点的状态
create table if not exists job_run_node (
    run_id            varchar(64) not null,
    node_id           varchar(128) not null,
    status            varchar(32) not null,          -- PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED
    attempt           int not null default 0,
    last_error        text,
    artifact_json     jsonb,
    started_at        timestamptz,
    ended_at          timestamptz,
    updated_at        timestamptz not null default now(),
    primary key (run_id, node_id)
);

-- 3) 断点（按节点存一份 JSON 就行）
create table if not exists job_run_checkpoint (
    run_id            varchar(64) not null,
    node_id           varchar(128) not null,
    checkpoint_json   jsonb not null,
    updated_at        timestamptz not null default now(),
    primary key (run_id, node_id)
);

