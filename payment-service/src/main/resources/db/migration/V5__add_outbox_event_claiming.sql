ALTER TABLE outbox_events DROP CONSTRAINT chk_outbox_status;
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED'));
ALTER TABLE outbox_events ADD COLUMN claim_token UUID;
ALTER TABLE outbox_events ADD COLUMN lease_expires_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_claim_state CHECK (
    (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
    OR (status IN ('PENDING', 'PUBLISHED') AND claim_token IS NULL AND lease_expires_at IS NULL)
);
CREATE INDEX idx_outbox_expired_leases ON outbox_events (lease_expires_at, id)
    WHERE status = 'PROCESSING';
