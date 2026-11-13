CREATE TABLE IF NOT EXISTS requests (
    id VARCHAR(64) PRIMARY KEY,
    workflow VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    payload CLOB NOT NULL,
    state VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    config_version VARCHAR(32),
    rules_snapshot CLOB,
    result_summary CLOB,
    reviewer VARCHAR(64),
    review_note CLOB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS request_state_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    reason CLOB,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    step VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    details CLOB,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_requests_idempotency ON requests(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_history_request ON request_state_history(request_id);
CREATE INDEX IF NOT EXISTS idx_audit_request ON audit_log(request_id);

