--liquibase formatted sql

--changeset jobzi:014-add-broadcast-schedule-fields
--comment: Add schedule fields to broadcast_campaigns for periodic sending

ALTER TABLE broadcast_campaigns
    ADD COLUMN schedule_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN schedule_type VARCHAR(20) DEFAULT 'ONCE',
    ADD COLUMN schedule_interval_hours INTEGER,
    ADD COLUMN scheduled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN next_send_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN broadcast_campaigns.schedule_enabled IS 'Whether the campaign should be sent periodically';
COMMENT ON COLUMN broadcast_campaigns.schedule_type IS 'ONCE, DAILY, WEEKLY, CUSTOM';
COMMENT ON COLUMN broadcast_campaigns.schedule_interval_hours IS 'For CUSTOM type: interval in hours';
COMMENT ON COLUMN broadcast_campaigns.scheduled_at IS 'When to send (for ONCE type)';
COMMENT ON COLUMN broadcast_campaigns.last_sent_at IS 'Last time the campaign was sent';
COMMENT ON COLUMN broadcast_campaigns.next_send_at IS 'Next scheduled send time';

CREATE INDEX idx_broadcast_campaigns_next_send ON broadcast_campaigns(next_send_at) WHERE schedule_enabled = TRUE;

--rollback ALTER TABLE broadcast_campaigns DROP COLUMN schedule_enabled, DROP COLUMN schedule_type, DROP COLUMN schedule_interval_hours, DROP COLUMN scheduled_at, DROP COLUMN last_sent_at, DROP COLUMN next_send_at;
--rollback DROP INDEX idx_broadcast_campaigns_next_send;