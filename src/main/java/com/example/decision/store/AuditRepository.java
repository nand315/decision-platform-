package com.example.decision.store;

import com.example.decision.model.AuditEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TestFaultInjector faultInjector;

    public AuditRepository(JdbcTemplate jdbcTemplate, TestFaultInjector faultInjector) {
        this.jdbcTemplate = jdbcTemplate;
        this.faultInjector = faultInjector;
    }

    private final RowMapper<AuditEntry> rowMapper = new RowMapper<>() {
        @Override
        public AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            AuditEntry entry = new AuditEntry();
            entry.setId(rs.getLong("id"));
            entry.setRequestId(rs.getString("request_id"));
            entry.setStep(rs.getString("step"));
            entry.setAction(rs.getString("action"));
            entry.setDetails(rs.getString("details"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                entry.setCreatedAt(createdAt.toInstant());
            }
            return entry;
        }
    };

    public void save(AuditEntry entry) {
        faultInjector.checkFault();

        String sql = """
            INSERT INTO audit_log (request_id, step, action, details, created_at)
            VALUES (?, ?, ?, ?, ?)
        """;

        Instant now = entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now();
        entry.setCreatedAt(now);

        jdbcTemplate.update(sql,
                entry.getRequestId(),
                entry.getStep(),
                entry.getAction(),
                entry.getDetails(),
                Timestamp.from(now)
        );
    }

    public List<AuditEntry> findByRequestId(String requestId) {
        String sql = "SELECT * FROM audit_log WHERE request_id = ? ORDER BY id ASC";
        return jdbcTemplate.query(sql, rowMapper, requestId);
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
        return count != null ? count : 0;
    }
}

