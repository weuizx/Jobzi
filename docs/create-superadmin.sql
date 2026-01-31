-- Script to create first superadmin user
-- Run this after the first application startup and migrations

-- Replace 123456789 with your actual Telegram ID
-- Get your Telegram ID from @userinfobot

INSERT INTO users (telegram_id, first_name, username, role, is_active, created_at, updated_at)
VALUES (
    123456789,  -- YOUR TELEGRAM ID HERE
    'Admin',
    'your_username',  -- YOUR TELEGRAM USERNAME
    'SUPERADMIN',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (telegram_id) DO UPDATE
SET role = 'SUPERADMIN';

-- Verify the superadmin was created
SELECT id, telegram_id, username, role, created_at
FROM users
WHERE role = 'SUPERADMIN';