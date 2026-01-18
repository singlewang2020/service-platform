package com.ruler.one.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruler.one.engine.DagEngine;
import com.ruler.one.engine.ExecutorRegistry;
import com.ruler.one.engine.JobBackedExecutor;
import com.ruler.one.model.DagDef;
import com.ruler.one.model.NodeDef;
import com.ruler.one.plugins.NodeExecutor;
import com.ruler.one.plugins.PrintNodeExecutor;
import com.ruler.one.storage.JdbcRunQueryRepository;
import com.ruler.one.storage.JdbcRunStorage;
import com.ruler.one.storage.RunQueryRepository;
import com.ruler.one.storage.RunStorage;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 一个最小可运行的 demo：
 * - 通过环境变量连接 Postgres
 * - 执行 db/database/1.DDL.SQL 建表
 * - 跑一条简单 DAG（print1 -> print2）
 *
 * 环境变量（不设置则使用默认值）：
 * - JOB_CHAIN_DAG_PG_HOST (default: localhost)
 * - JOB_CHAIN_DAG_PG_PORT (default: 5432)
 * - JOB_CHAIN_DAG_PG_DB   (default: job_chain)
 * - JOB_CHAIN_DAG_PG_USER (default: postgres)
 * - JOB_CHAIN_DAG_PG_PWD  (default: postgres)
 */
public class DemoRunner {

    public static void main(String[] args) {
        String host = env("JOB_CHAIN_DAG_PG_HOST", "localhost");
        int port = Integer.parseInt(env("JOB_CHAIN_DAG_PG_PORT", "5432"));
        String db = env("JOB_CHAIN_DAG_PG_DB", "job_chain");
        String user = env("JOB_CHAIN_DAG_PG_USER", "postgres");
        String pwd = env("JOB_CHAIN_DAG_PG_PWD", "postgres");

        DataSource ds = dataSource(host, port, db, user, pwd);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        ObjectMapper om = new ObjectMapper();

        applyDdl(jdbc);

        RunStorage storage = new JdbcRunStorage(jdbc, om);
        RunQueryRepository runQueryRepository = new JdbcRunQueryRepository(jdbc);

        // demo 环境不走 Spring 注入，这里手动装配一个 registry
        NodeExecutor print = new PrintNodeExecutor();
        ExecutorRegistry registry = new ExecutorRegistry(List.of(print));

        // B 方案：jobId 节点通过 JobBackedExecutor 解析（demo 这里不给 jobRepo，仍可跑 type/cfg 的 DAG）
        JobBackedExecutor jobBackedExecutor = new JobBackedExecutor(
                new com.ruler.one.storage.JdbcJobDefinitionRepository(jdbc),
                registry,
                om
        );

        DagEngine engine = new DagEngine(storage, runQueryRepository, registry, om, jobBackedExecutor);

        DagDef dag = new DagDef();
        dag.setJob("demo-job");

        NodeDef n1 = new NodeDef();
        n1.setId("print1");
        n1.setType("print");
        n1.setCfg(Map.of("msg", "hello", "ts", Instant.now().toString()));

        NodeDef n2 = new NodeDef();
        n2.setId("print2");
        n2.setType("print");
        n2.setDependsOn(List.of("print1"));
        n2.setCfg(Map.of("msg", "world", "from", "print1"));

        dag.setNodes(List.of(n1, n2));

        String runId = "run-" + Instant.now().toEpochMilli();
        System.out.println("Running DAG, runId=" + runId);

        engine.run(runId, dag);

        System.out.println("Done. You can query:\n" +
                "select * from job_run where run_id = '" + runId + "';\n" +
                "select * from job_run_node where run_id = '" + runId + "' order by node_id;");
    }

    private static DataSource dataSource(String host, int port, String db, String user, String pwd) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[]{host});
        ds.setPortNumbers(new int[]{port});
        ds.setDatabaseName(db);
        ds.setUser(user);
        ds.setPassword(pwd);
        // 连接参数按需扩展（ssl、socketTimeout 等）
        return ds;
    }

    private static void applyDdl(JdbcTemplate jdbc) {
        String resource = "/db/database/1.DDL.SQL";
        try (InputStream is = DemoRunner.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("DDL resource not found: " + resource);
            }
            String ddl = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // 简单按分号切分执行（DDL 文件结构简单，足够用）
            for (String stmt : ddl.split(";")) {
                String s = stmt.trim();
                if (s.isEmpty() || s.startsWith("--")) continue;
                jdbc.execute(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply DDL", e);
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
