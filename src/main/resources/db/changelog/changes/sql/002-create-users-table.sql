--liquibase formatted sql

--changeset jobzi:002-create-users-table
--comment: Create users table for all system users (admins and candidates)

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    username VARCHAR(255),
    phone_number VARCHAR(50),
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_telegram_id ON users(telegram_id);
CREATE INDEX idx_users_phone_number ON users(phone_number);
CREATE INDEX idx_users_username ON users(username);

COMMENT ON TABLE users IS 'Stores information about all users (business admins and job candidates)';
COMMENT ON COLUMN users.telegram_id IS 'Unique Telegram user ID';
COMMENT ON COLUMN users.is_active IS 'Whether the user account is active';

--rollback DROP TABLE users;