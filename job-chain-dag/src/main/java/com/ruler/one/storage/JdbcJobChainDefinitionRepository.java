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

import com.ruler.one.model.JobChainDefinition;

@Repository
public class JdbcJobChainDefinitionRepository implements JobChainDefinitionRepository {

    private final JdbcTemplate jdbc;

    public JdbcJobChainDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<JobChainDefinition> MAPPER = (rs, rowNum) -> new JobChainDefinition(
            rs.getString("chain_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("enabled"),
            rs.getLong("version"),
            rs.getString("dag_json"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    @Override
    public JobChainDefinition insert(JobChainDefinition chain) {
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "insert into job_chain_definition(chain_id, name, description, enabled, version, dag_json, created_at, updated_at) " +
                            "values (?,?,?,?,?, ?, current_timestamp, current_timestamp)"
            );
            ps.setString(1, chain.chainId());
            ps.setString(2, chain.name());
            ps.setString(3, chain.description());
            ps.setBoolean(4, chain.enabled());
            ps.setLong(5, chain.version());
            DbJson.setJsonbOrString(ps, 6, chain.dagJson());
            return ps;
        });
        return findById(chain.chainId()).orElseThrow();
    }

    @Override
    public Optional<JobChainDefinition> updateWithVersion(JobChainDefinition chain, long expectedVersion) {
        int updated = jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "update job_chain_definition set name=?, description=?, dag_json=?, version=version+1, updated_at=current_timestamp " +
                            "where chain_id=? and version=?"
            );
            ps.setString(1, chain.name());
            ps.setString(2, chain.description());
            DbJson.setJsonbOrString(ps, 3, chain.dagJson());
            ps.setString(4, chain.chainId());
            ps.setLong(5, expectedVersion);
            return ps;
        });
        if (updated <= 0) return Optional.empty();
        return findById(chain.chainId());
    }

    @Override
    public Optional<JobChainDefinition> setEnabled(String chainId, boolean enabled) {
        int updated = jdbc.update(
                "update job_chain_definition set enabled=?, updated_at=current_timestamp where chain_id=?",
                enabled,
                chainId
        );
        if (updated <= 0) return Optional.empty();
        return findById(chainId);
    }

    @Override
    public Optional<JobChainDefinition> findById(String chainId) {
        var list = jdbc.query(
                "select chain_id, name, description, enabled, version, dag_json as dag_json, created_at, updated_at from job_chain_definition where chain_id=?",
                MAPPER,
                chainId);
        return list.stream().findFirst();
    }

    @Override
    public Optional<JobChainDefinition> findByName(String name) {
        var list = jdbc.query(
                "select chain_id, name, description, enabled, version, dag_json as dag_json, created_at, updated_at from job_chain_definition where name=?",
                MAPPER,
                name);
        return list.stream().findFirst();
    }

    @Override
    public List<JobChainDefinition> page(int offset, int limit, String keyword, Boolean enabled) {
        StringBuilder sql = new StringBuilder(
                "select chain_id, name, description, enabled, version, dag_json as dag_json, created_at, updated_at from job_chain_definition where 1=1");
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
        StringBuilder sql = new StringBuilder("select count(1) from job_chain_definition where 1=1");
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and name ilike ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (enabled != null) {
            sql.append(" and enabled = ?");
            args.add(enabled);
        }

        return jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    }
}
