ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP;

DROP INDEX idx_outbox_pending_events;

CREATE INDEX idx_outbox_pending_events
    ON outbox_events (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';