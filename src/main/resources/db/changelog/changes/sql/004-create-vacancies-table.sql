--liquibase formatted sql

--changeset jobzi:004-create-vacancies-table
--comment: Create vacancies table for job postings

CREATE TYPE vacancy_status AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED');

CREATE TABLE vacancies (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(255),
    salary VARCHAR(100),
    status vacancy_status NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_vacancies_business_id ON vacancies(business_id);
CREATE INDEX idx_vacancies_code ON vacancies(code);
CREATE INDEX idx_vacancies_status ON vacancies(status);
CREATE INDEX idx_vacancies_published_at ON vacancies(published_at);

COMMENT ON TABLE vacancies IS 'Stores job vacancy postings';
COMMENT ON COLUMN vacancies.code IS 'Unique short code for the vacancy (e.g., ABC123) used by candidates to apply';
COMMENT ON COLUMN vacancies.status IS 'Current status: DRAFT (not published), ACTIVE (accepting applications), PAUSED (temporarily stopped), CLOSED (no longer accepting)';

--rollback DROP TABLE vacancies; DROP TYPE vacancy_status;