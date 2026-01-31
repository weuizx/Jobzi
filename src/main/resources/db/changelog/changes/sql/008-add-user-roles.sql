--liquibase formatted sql

--changeset jobzi:008-add-user-roles
--comment: Add user role system for superadmins and regular users

CREATE TYPE user_role AS ENUM ('USER', 'SUPERADMIN');

ALTER TABLE users
ADD COLUMN role user_role NOT NULL DEFAULT 'USER';

CREATE INDEX idx_users_role ON users(role);

COMMENT ON COLUMN users.role IS 'User role: USER (regular user/candidate) or SUPERADMIN (platform administrator)';

--rollback ALTER TABLE users DROP COLUMN role; DROP TYPE user_role;
