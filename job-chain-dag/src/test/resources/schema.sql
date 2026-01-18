-- Auto-applied by Spring Boot on startup (when spring.sql.init.mode=always)

-- =========================
-- Definition state
-- =========================
create table if not exists job_definition (
    job_id           varchar(64) primary key,
    name             varchar(128) not null unique,
    description      clob,
    type             varchar(64) not null,
    enabled          boolean not null default true,
    config_json      clob not null,
    created_at       timestamp not null default current_timestamp,
    updated_at       timestamp not null default current_timestamp
);

create table if not exists job_chain_definition (
    chain_id         varchar(64) primary key,
    name             varchar(128) not null unique,
    description      clob,
    enabled          boolean not null default true,
    version          bigint not null default 1,
    dag_json         clob not null,
    created_at       timestamp not null default current_timestamp,
    updated_at       timestamp not null default current_timestamp
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
    dag_json          clob not null,
    created_at        timestamp not null default current_timestamp,
    updated_at        timestamp not null default current_timestamp
);

create index if not exists idx_job_run_chain_id on job_run(chain_id);
create index if not exists idx_job_run_job_id on job_run(job_id);

create table if not exists job_run_node (
    run_id            varchar(64) not null,
    node_id           varchar(128) not null,
    status            varchar(32) not null,          -- PENDING/RUNNING/SUCCESS/FAILED/RETRYING/SKIPPED/STOPPED
    attempt           int not null default 0,
    last_error        clob,
    artifact_json     clob,
    started_at        timestamp,
    ended_at          timestamp,
    updated_at        timestamp not null default current_timestamp,
    primary key (run_id, node_id)
);

create table if not exists job_run_checkpoint (
    run_id            varchar(64) not null,
    node_id           varchar(128) not null,
    checkpoint_json   clob not null,
    updated_at        timestamp not null default current_timestamp,
    primary key (run_id, node_id)
);
