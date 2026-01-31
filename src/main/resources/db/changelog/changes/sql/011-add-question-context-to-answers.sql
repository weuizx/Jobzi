--liquibase formatted sql

--changeset jobzi:011-add-question-context-to-answers
--comment: Add question context fields to answers table to preserve question details even if questions are modified or deleted

ALTER TABLE answers
    ADD COLUMN question_text TEXT,
    ADD COLUMN question_type VARCHAR(20),
    ADD COLUMN question_order INTEGER;

COMMENT ON COLUMN answers.question_text IS 'Snapshot of question text at the time of application submission (preserves context if question is modified/deleted)';
COMMENT ON COLUMN answers.question_type IS 'Snapshot of question type at the time of application submission (TEXT, NUMBER, PHONE, YES_NO, CHOICE, DATE)';
COMMENT ON COLUMN answers.question_order IS 'Order of the question in the questionnaire at the time of submission';

-- Populate existing answers with current question data (for backwards compatibility)
UPDATE answers a
SET
    question_text = q.question_text,
    question_type = q.question_type,
    question_order = (
        SELECT COUNT(*)
        FROM questions q2
        WHERE q2.vacancy_id = q.vacancy_id
        AND q2.id <= q.id
    )
FROM questions q
WHERE a.question_id = q.id;

-- Make fields NOT NULL after populating existing data
ALTER TABLE answers
    ALTER COLUMN question_text SET NOT NULL,
    ALTER COLUMN question_type SET NOT NULL,
    ALTER COLUMN question_order SET NOT NULL;

--rollback ALTER TABLE answers DROP COLUMN question_text, DROP COLUMN question_type, DROP COLUMN question_order;
