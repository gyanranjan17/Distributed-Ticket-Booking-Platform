CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,

                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id BIGINT NOT NULL,

                               event_type VARCHAR(100) NOT NULL,
                               topic VARCHAR(100) NOT NULL,
                               partition_key VARCHAR(100) NOT NULL,

                               payload TEXT NOT NULL,

                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

                               retry_count INTEGER NOT NULL DEFAULT 0,

                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               published_at TIMESTAMP WITH TIME ZONE,

                               CONSTRAINT chk_outbox_status
                                   CHECK (status IN ('PENDING', 'PUBLISHED')),

                               CONSTRAINT chk_outbox_retry_count
                                   CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_pending_events
    ON outbox_events (created_at, id)
    WHERE status = 'PENDING';