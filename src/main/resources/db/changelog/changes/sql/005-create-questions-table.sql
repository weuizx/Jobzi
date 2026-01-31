--liquibase formatted sql

--changeset jobzi:005-create-questions-table
--comment: Create questions table for vacancy questionnaires

CREATE TYPE question_type AS ENUM ('TEXT', 'YES_NO', 'CHOICE', 'PHONE', 'DATE', 'NUMBER');

CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    vacancy_id BIGINT NOT NULL REFERENCES vacancies(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    question_type question_type NOT NULL DEFAULT 'TEXT',
    is_required BOOLEAN DEFAULT true NOT NULL,
    order_index INTEGER NOT NULL,
    options JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_questions_vacancy_id ON questions(vacancy_id);
CREATE INDEX idx_questions_order_index ON questions(vacancy_id, order_index);

COMMENT ON TABLE questions IS 'Stores questionnaire questions for each vacancy';
COMMENT ON COLUMN questions.question_type IS 'Type of question: TEXT (free text), YES_NO (boolean), CHOICE (multiple choice), PHONE (phone number), DATE (date picker), NUMBER (numeric input)';
COMMENT ON COLUMN questions.order_index IS 'Display order of questions in the questionnaire';
COMMENT ON COLUMN questions.options IS 'JSON array of options for CHOICE type questions';

--rollback DROP TABLE questions; DROP TYPE question_type;