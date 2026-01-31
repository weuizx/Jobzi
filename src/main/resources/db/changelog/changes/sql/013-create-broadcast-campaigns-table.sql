--liquibase formatted sql

--changeset jobzi:013-create-broadcast-campaigns-table
--comment: Create broadcast_campaigns table for storing advertising campaigns

CREATE TABLE broadcast_campaigns (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_broadcast_campaigns_business_id ON broadcast_campaigns(business_id);
CREATE INDEX idx_broadcast_campaigns_status ON broadcast_campaigns(status);

COMMENT ON TABLE broadcast_campaigns IS 'Advertising campaigns for business';
COMMENT ON COLUMN broadcast_campaigns.status IS 'DRAFT, READY, SENT';
COMMENT ON COLUMN broadcast_campaigns.title IS 'Internal campaign title for tracking';
COMMENT ON COLUMN broadcast_campaigns.message_text IS 'The actual message text to broadcast';

--rollback DROP TABLE broadcast_campaigns;