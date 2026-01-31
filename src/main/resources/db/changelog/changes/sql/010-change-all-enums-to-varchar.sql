--liquibase formatted sql

--changeset jobzi:010-change-all-enums-to-varchar
--comment: Change all enum columns from custom types to VARCHAR

-- 1. business_role -> VARCHAR
ALTER TABLE business_users
ALTER COLUMN role TYPE VARCHAR(50) USING role::text;

DROP TYPE IF EXISTS business_role CASCADE;

-- 2. vacancy_status -> VARCHAR
ALTER TABLE vacancies
ALTER COLUMN status TYPE VARCHAR(50) USING status::text;

DROP TYPE IF EXISTS vacancy_status CASCADE;

-- 3. question_type -> VARCHAR
ALTER TABLE questions
ALTER COLUMN question_type TYPE VARCHAR(50) USING question_type::text;

DROP TYPE IF EXISTS question_type CASCADE;

-- 4. application_status -> VARCHAR
ALTER TABLE applications
ALTER COLUMN status TYPE VARCHAR(50) USING status::text;

DROP TYPE IF EXISTS application_status CASCADE;

-- Индексы остаются, так как они уже созданы в предыдущих миграциях

--rollback CREATE TYPE business_role AS ENUM ('ADMIN', 'MANAGER'); ALTER TABLE business_users ALTER COLUMN role TYPE business_role USING role::business_role; CREATE TYPE vacancy_status AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED'); ALTER TABLE vacancies ALTER COLUMN status TYPE vacancy_status USING status::vacancy_status; CREATE TYPE question_type AS ENUM ('TEXT', 'YES_NO', 'CHOICE', 'PHONE', 'DATE', 'NUMBER'); ALTER TABLE questions ALTER COLUMN question_type TYPE question_type USING question_type::question_type; CREATE TYPE application_status AS ENUM ('NEW', 'VIEWED', 'CONTACTED', 'ACCEPTED', 'REJECTED'); ALTER TABLE applications ALTER COLUMN status TYPE application_status USING status::application_status;