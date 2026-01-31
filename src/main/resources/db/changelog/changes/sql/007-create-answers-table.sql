--liquibase formatted sql

--changeset jobzi:007-create-answers-table
--comment: Create answers table for storing candidate responses to questions

CREATE TABLE answers (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    answer_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_application_question_answer UNIQUE (application_id, question_id)
);

CREATE INDEX idx_answers_application_id ON answers(application_id);
CREATE INDEX idx_answers_question_id ON answers(question_id);

COMMENT ON TABLE answers IS 'Stores candidate answers to questionnaire questions';
COMMENT ON COLUMN answers.answer_text IS 'The candidate answer (stored as text regardless of question type)';

--rollback DROP TABLE answers;