CREATE TABLE audit_logs (
    id          UUID           PRIMARY KEY,
    actor_id    UUID,
    actor_email VARCHAR(255),
    action      VARCHAR(40)    NOT NULL,
    result      VARCHAR(10)    NOT NULL,
    target_type VARCHAR(30),
    target_id   VARCHAR(64),
    amount      NUMERIC(19, 2),
    detail      VARCHAR(500),
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    trace_id    VARCHAR(64),
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor      ON audit_logs (actor_id);
CREATE INDEX idx_audit_action     ON audit_logs (action);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at DESC);