--liquibase formatted sql

--changeset jobzi:006-create-applications-table
--comment: Create applications table for candidate job applications

CREATE TYPE application_status AS ENUM ('NEW', 'VIEWED', 'CONTACTED', 'ACCEPTED', 'REJECTED');

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT NOT NULL REFERENCES vacancies(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status application_status NOT NULL DEFAULT 'NEW',
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_vacancy_user_application UNIQUE (vacancy_id, user_id)
);

CREATE INDEX idx_applications_vacancy_id ON applications(vacancy_id);
CREATE INDEX idx_applications_user_id ON applications(user_id);
CREATE INDEX idx_applications_status ON applications(status);
CREATE INDEX idx_applications_created_at ON applications(created_at);

COMMENT ON TABLE applications IS 'Stores candidate applications to vacancies';
COMMENT ON COLUMN applications.status IS 'Application status: NEW (just submitted), VIEWED (employer saw it), CONTACTED (employer reached out), ACCEPTED (hired), REJECTED (not suitable)';
COMMENT ON COLUMN applications.notes IS 'Internal notes from employer about the candidate';

--rollback DROP TABLE applications; DROP TYPE application_status;