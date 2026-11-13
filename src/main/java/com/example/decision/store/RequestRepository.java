package com.example.decision.store;

import com.example.decision.model.RequestRecord;
import com.example.decision.model.RequestState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Repository
public class RequestRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TestFaultInjector faultInjector;

    public RequestRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TestFaultInjector faultInjector) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.faultInjector = faultInjector;
    }

    private final RowMapper<RequestRecord> rowMapper = new RowMapper<>() {
        @Override
        public RequestRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            RequestRecord record = new RequestRecord();
            record.setId(rs.getString("id"));
            record.setWorkflow(rs.getString("workflow"));
            record.setIdempotencyKey(rs.getString("idempotency_key"));
            record.setPayloadHash(rs.getString("payload_hash"));
            String payloadJson = rs.getString("payload");
            record.setPayloadJson(payloadJson);
            try {
                if (payloadJson != null) {
                    record.setPayload(objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {}));
                }
            } catch (Exception e) {
                // fallback
            }
            record.setState(RequestState.valueOf(rs.getString("state")));
            record.setRetryCount(rs.getInt("retry_count"));
            record.setConfigVersion(rs.getString("config_version"));
            record.setRulesSnapshot(rs.getString("rules_snapshot"));
            record.setResultSummary(rs.getString("result_summary"));
            record.setReviewer(rs.getString("reviewer"));
            record.setReviewNote(rs.getString("review_note"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) record.setCreatedAt(createdAt.toInstant());
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) record.setUpdatedAt(updatedAt.toInstant());
            return record;
        }
    };

    public void save(RequestRecord record) {
        faultInjector.checkFault();

        String sql = """
            INSERT INTO requests (
                id, workflow, idempotency_key, payload_hash, payload, state,
                retry_count, config_version, rules_snapshot, result_summary,
                reviewer, review_note, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String jsonPayload = record.getPayloadJson();
        if (jsonPayload == null && record.getPayload() != null) {
            try {
                jsonPayload = objectMapper.writeValueAsString(record.getPayload());
            } catch (Exception e) {
                jsonPayload = "{}";
            }
        }

        Instant now = Instant.now();
        if (record.getCreatedAt() == null) record.setCreatedAt(now);
        if (record.getUpdatedAt() == null) record.setUpdatedAt(now);

        jdbcTemplate.update(sql,
                record.getId(),
                record.getWorkflow(),
                record.getIdempotencyKey(),
                record.getPayloadHash(),
                jsonPayload,
                record.getState().name(),
                record.getRetryCount(),
                record.getConfigVersion(),
                record.getRulesSnapshot(),
                record.getResultSummary(),
                record.getReviewer(),
                record.getReviewNote(),
                Timestamp.from(record.getCreatedAt()),
                Timestamp.from(record.getUpdatedAt())
        );
    }

    public void update(RequestRecord record) {
        faultInjector.checkFault();

        String sql = """
            UPDATE requests SET
                workflow = ?,
                payload_hash = ?,
                payload = ?,
                state = ?,
                retry_count = ?,
                config_version = ?,
                rules_snapshot = ?,
                result_summary = ?,
                reviewer = ?,
                review_note = ?,
                updated_at = ?
            WHERE id = ?
        """;

        String jsonPayload = record.getPayloadJson();
        if (jsonPayload == null && record.getPayload() != null) {
            try {
                jsonPayload = objectMapper.writeValueAsString(record.getPayload());
            } catch (Exception e) {
                jsonPayload = "{}";
            }
        }

        record.setUpdatedAt(Instant.now());

        jdbcTemplate.update(sql,
                record.getWorkflow(),
                record.getPayloadHash(),
                jsonPayload,
                record.getState().name(),
                record.getRetryCount(),
                record.getConfigVersion(),
                record.getRulesSnapshot(),
                record.getResultSummary(),
                record.getReviewer(),
                record.getReviewNote(),
                Timestamp.from(record.getUpdatedAt()),
                record.getId()
        );
    }

    public Optional<RequestRecord> findById(String id) {
        String sql = "SELECT * FROM requests WHERE id = ?";
        try {
            RequestRecord record = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<RequestRecord> findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT * FROM requests WHERE idempotency_key = ?";
        try {
            RequestRecord record = jdbcTemplate.queryForObject(sql, rowMapper, idempotencyKey);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM requests", Integer.class);
        return count != null ? count : 0;
    }
}

