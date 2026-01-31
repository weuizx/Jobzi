--liquibase formatted sql

--changeset jobzi:009-change-user-role-to-varchar
--comment: Change user role column from custom type to VARCHAR

-- Изменяем тип столбца role с user_role на VARCHAR
ALTER TABLE users
ALTER COLUMN role TYPE VARCHAR(50) USING role::text;

-- Удаляем кастомный тип user_role
DROP TYPE IF EXISTS user_role CASCADE;

-- Индекс остается, так как он уже создан в миграции 008

--rollback CREATE TYPE user_role AS ENUM ('USER', 'SUPERADMIN'); ALTER TABLE users ALTER COLUMN role TYPE user_role USING role::user_role;