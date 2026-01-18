package com.ruler.one.storage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.ruler.one.model.JobDefinition;

@Repository
public class JdbcJobDefinitionRepository implements JobDefinitionRepository {

    private final JdbcTemplate jdbc;

    public JdbcJobDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<JobDefinition> MAPPER = new RowMapper<>() {
        @Override
        public JobDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new JobDefinition(
                    rs.getString("job_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("type"),
                    rs.getBoolean("enabled"),
                    rs.getString("config_json"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)
            );
        }
    };

    @Override
    public JobDefinition insert(JobDefinition job) {
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "insert into job_definition(job_id, name, description, type, enabled, config_json, created_at, updated_at) " +
                            "values (?,?,?,?,?, ?, current_timestamp, current_timestamp)");
            ps.setString(1, job.jobId());
            ps.setString(2, job.name());
            ps.setString(3, job.description());
            ps.setString(4, job.type());
            ps.setBoolean(5, job.enabled());
            DbJson.setJsonbOrString(ps, 6, job.configJson());
            return ps;
        });
        return findById(job.jobId()).orElseThrow();
    }

    @Override
    public JobDefinition update(JobDefinition job) {
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "update job_definition set name=?, description=?, type=?, enabled=?, config_json=?, updated_at=current_timestamp where job_id=?");
            ps.setString(1, job.name());
            ps.setString(2, job.description());
            ps.setString(3, job.type());
            ps.setBoolean(4, job.enabled());
            DbJson.setJsonbOrString(ps, 5, job.configJson());
            ps.setString(6, job.jobId());
            return ps;
        });
        return findById(job.jobId()).orElseThrow();
    }

    @Override
    public boolean deleteById(String jobId) {
        return jdbc.update("delete from job_definition where job_id=?", jobId) > 0;
    }

    @Override
    public Optional<JobDefinition> findById(String jobId) {
        var list = jdbc.query("select job_id, name, description, type, enabled, config_json as config_json, created_at, updated_at from job_definition where job_id=?",
                MAPPER,
                jobId);
        return list.stream().findFirst();
    }

    @Override
    public Optional<JobDefinition> findByName(String name) {
        var list = jdbc.query("select job_id, name, description, type, enabled, config_json as config_json, created_at, updated_at from job_definition where name=?",
                MAPPER,
                name);
        return list.stream().findFirst();
    }

    @Override
    public List<JobDefinition> page(int offset, int limit, String keyword, Boolean enabled) {
        StringBuilder sql = new StringBuilder(
                "select job_id, name, description, type, enabled, config_json as config_json, created_at, updated_at from job_definition where 1=1");
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and name ilike ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (enabled != null) {
            sql.append(" and enabled = ?");
            args.add(enabled);
        }

        sql.append(" order by updated_at desc, created_at desc offset ? limit ?");
        args.add(offset);
        args.add(limit);

        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public long count(String keyword, Boolean enabled) {
        StringBuilder sql = new StringBuilder("select count(1) from job_definition where 1=1");
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and name ilike ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (enabled != null) {
            sql.append(" and enabled = ?");
            args.add(enabled);
        }

        Long v = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return v == null ? 0L : v;
    }
}
