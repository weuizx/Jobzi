--liquibase formatted sql

--changeset jobzi:001-create-businesses-table
--comment: Create businesses table for storing company/employer information

CREATE TABLE businesses (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    telegram_chat_id BIGINT NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_businesses_telegram_chat_id ON businesses(telegram_chat_id);
CREATE INDEX idx_businesses_is_active ON businesses(is_active);

COMMENT ON TABLE businesses IS 'Stores information about businesses (employers, contractors, etc.)';
COMMENT ON COLUMN businesses.telegram_chat_id IS 'Telegram chat ID of the business owner or admin';
COMMENT ON COLUMN businesses.is_active IS 'Whether the business account is active';

--rollback DROP TABLE businesses;