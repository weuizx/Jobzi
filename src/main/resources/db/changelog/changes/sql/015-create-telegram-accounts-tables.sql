--liquibase formatted sql

--changeset jobzi:015-create-telegram-accounts-tables
--comment: Create tables for dynamic Telegram account pool management

-- Main table for Telegram accounts in the pool
CREATE TABLE telegram_accounts (
    id BIGSERIAL PRIMARY KEY,
    phone_number_encrypted TEXT NOT NULL,
    api_id INTEGER NOT NULL,
    api_hash_encrypted TEXT NOT NULL,
    session_name VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    auth_state VARCHAR(50),
    last_used_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_telegram_accounts_phone UNIQUE (phone_number_encrypted),
    CONSTRAINT chk_telegram_accounts_status CHECK (status IN ('INACTIVE', 'AUTHENTICATING', 'AUTHENTICATED', 'ERROR')),
    CONSTRAINT chk_telegram_accounts_auth_state CHECK (auth_state IS NULL OR auth_state IN ('WAITING_CODE', 'WAITING_PASSWORD'))
);

CREATE INDEX idx_telegram_accounts_status ON telegram_accounts(status);
CREATE INDEX idx_telegram_accounts_session_name ON telegram_accounts(session_name);
CREATE INDEX idx_telegram_accounts_is_active ON telegram_accounts(is_active);
CREATE INDEX idx_telegram_accounts_created_by ON telegram_accounts(created_by_user_id);

COMMENT ON TABLE telegram_accounts IS 'Pool of Telegram accounts for sending messages';
COMMENT ON COLUMN telegram_accounts.phone_number_encrypted IS 'AES-256 encrypted phone number';
COMMENT ON COLUMN telegram_accounts.api_id IS 'Telegram API ID from my.telegram.org';
COMMENT ON COLUMN telegram_accounts.api_hash_encrypted IS 'AES-256 encrypted Telegram API hash';
COMMENT ON COLUMN telegram_accounts.session_name IS 'Unique session identifier for TDLight session directory';
COMMENT ON COLUMN telegram_accounts.status IS 'Current account status: INACTIVE, AUTHENTICATING, AUTHENTICATED, ERROR';
COMMENT ON COLUMN telegram_accounts.auth_state IS 'Current authentication state: WAITING_CODE, WAITING_PASSWORD, or NULL';
COMMENT ON COLUMN telegram_accounts.last_used_at IS 'Timestamp of last message sent from this account';
COMMENT ON COLUMN telegram_accounts.error_message IS 'Last error message if status is ERROR';
COMMENT ON COLUMN telegram_accounts.is_active IS 'Whether account is enabled for use in the pool';

-- Table for tracking authentication sessions
CREATE TABLE telegram_auth_sessions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES telegram_accounts(id) ON DELETE CASCADE,
    auth_state VARCHAR(50) NOT NULL,
    code_hash TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_telegram_auth_sessions_state CHECK (auth_state IN ('WAITING_CODE', 'WAITING_PASSWORD', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_telegram_auth_sessions_account_id ON telegram_auth_sessions(account_id);
CREATE INDEX idx_telegram_auth_sessions_expires_at ON telegram_auth_sessions(expires_at);

COMMENT ON TABLE telegram_auth_sessions IS 'Tracks ongoing authentication sessions for Telegram accounts';
COMMENT ON COLUMN telegram_auth_sessions.account_id IS 'Reference to the telegram account being authenticated';
COMMENT ON COLUMN telegram_auth_sessions.auth_state IS 'Current state of authentication flow';
COMMENT ON COLUMN telegram_auth_sessions.code_hash IS 'Hash for tracking code submission';
COMMENT ON COLUMN telegram_auth_sessions.expires_at IS 'Session expiration time (10 minutes TTL)';

--rollback DROP TABLE telegram_auth_sessions;
--rollback DROP TABLE telegram_accounts;
