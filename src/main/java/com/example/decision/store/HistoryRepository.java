package com.example.decision.store;

import com.example.decision.model.RequestState;
import com.example.decision.model.StateHistoryEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class HistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TestFaultInjector faultInjector;

    public HistoryRepository(JdbcTemplate jdbcTemplate, TestFaultInjector faultInjector) {
        this.jdbcTemplate = jdbcTemplate;
        this.faultInjector = faultInjector;
    }

    private final RowMapper<StateHistoryEntry> rowMapper = new RowMapper<>() {
        @Override
        public StateHistoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            StateHistoryEntry entry = new StateHistoryEntry();
            entry.setId(rs.getLong("id"));
            entry.setRequestId(rs.getString("request_id"));
            String fromStateStr = rs.getString("from_state");
            if (fromStateStr != null) {
                entry.setFromState(RequestState.valueOf(fromStateStr));
            }
            entry.setToState(RequestState.valueOf(rs.getString("to_state")));
            entry.setReason(rs.getString("reason"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                entry.setCreatedAt(createdAt.toInstant());
            }
            return entry;
        }
    };

    public void save(StateHistoryEntry entry) {
        faultInjector.checkFault();

        String sql = """
            INSERT INTO request_state_history (request_id, from_state, to_state, reason, created_at)
            VALUES (?, ?, ?, ?, ?)
        """;

        Instant now = entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now();
        entry.setCreatedAt(now);

        jdbcTemplate.update(sql,
                entry.getRequestId(),
                entry.getFromState() != null ? entry.getFromState().name() : null,
                entry.getToState().name(),
                entry.getReason(),
                Timestamp.from(now)
        );
    }

    public List<StateHistoryEntry> findByRequestId(String requestId) {
        String sql = "SELECT * FROM request_state_history WHERE request_id = ? ORDER BY id ASC";
        return jdbcTemplate.query(sql, rowMapper, requestId);
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM request_state_history", Integer.class);
        return count != null ? count : 0;
    }
}

