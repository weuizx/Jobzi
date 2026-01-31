--liquibase formatted sql

--changeset jobzi:003-create-business-users-table
--comment: Create business_users table for managing user roles in businesses

CREATE TYPE business_role AS ENUM ('ADMIN', 'MANAGER');

CREATE TABLE business_users (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role business_role NOT NULL DEFAULT 'MANAGER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_business_user UNIQUE (business_id, user_id)
);

CREATE INDEX idx_business_users_business_id ON business_users(business_id);
CREATE INDEX idx_business_users_user_id ON business_users(user_id);
CREATE INDEX idx_business_users_role ON business_users(role);

COMMENT ON TABLE business_users IS 'Junction table connecting users to businesses with their roles';
COMMENT ON COLUMN business_users.role IS 'Role of the user in the business: ADMIN (full access) or MANAGER (limited access)';

--rollback DROP TABLE business_users; DROP TYPE business_role;