--liquibase formatted sql

--changeset jobzi:012-create-broadcast-channels-table
--comment: Create broadcast_channels table for storing Telegram channels where business can send ads

CREATE TABLE broadcast_channels (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    channel_id VARCHAR(100) NOT NULL,
    channel_name VARCHAR(200),
    channel_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    is_active BOOLEAN DEFAULT TRUE,
    is_bot_admin BOOLEAN DEFAULT FALSE,
    last_validation_at TIMESTAMP WITH TIME ZONE,
    validation_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_business_channel UNIQUE (business_id, channel_id)
);

CREATE INDEX idx_broadcast_channels_business_id ON broadcast_channels(business_id);
CREATE INDEX idx_broadcast_channels_is_active ON broadcast_channels(is_active);

COMMENT ON TABLE broadcast_channels IS 'Telegram channels for business advertising broadcasts';
COMMENT ON COLUMN broadcast_channels.channel_id IS 'Telegram channel ID (@channel_name or numeric -100...)';
COMMENT ON COLUMN broadcast_channels.channel_type IS 'PUBLIC or PRIVATE';
COMMENT ON COLUMN broadcast_channels.is_bot_admin IS 'Whether bot has admin rights in this channel';
COMMENT ON COLUMN broadcast_channels.last_validation_at IS 'Last time bot permissions were validated';
COMMENT ON COLUMN broadcast_channels.validation_error IS 'Last validation error if any';

--rollback DROP TABLE broadcast_channels;