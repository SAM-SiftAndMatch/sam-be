ALTER TABLE jobs ADD COLUMN selected_freelancer_id UUID REFERENCES users(id);

UPDATE jobs j SET selected_freelancer_id = c.freelancer_id
FROM contracts c WHERE c.job_id = j.id;

CREATE TABLE job_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id),
    freelancer_id UUID NOT NULL REFERENCES users(id),
    source VARCHAR(32) NOT NULL,
    source_id UUID,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    expires_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    responded_at TIMESTAMP(6),
    CONSTRAINT uk_job_invitation_pair UNIQUE (job_id, freelancer_id),
    CONSTRAINT ck_job_invitation_source CHECK (source IN ('DIRECT', 'RECOMMENDATION', 'PROPOSAL')),
    CONSTRAINT ck_job_invitation_status CHECK (status IN (
        'PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'EXPIRED', 'SELECTED', 'NOT_SELECTED'
    ))
);

CREATE INDEX idx_job_invitation_freelancer_page
    ON job_invitations (freelancer_id, status, created_at DESC, id DESC);
CREATE INDEX idx_job_invitation_job_page
    ON job_invitations (job_id, status, created_at DESC, id DESC);
CREATE INDEX idx_job_invitation_expiry
    ON job_invitations (status, expires_at) WHERE status = 'PENDING';

INSERT INTO job_invitations (job_id, freelancer_id, source, source_id, status, created_at)
SELECT job_id, freelancer_id, 'RECOMMENDATION', id,
       CASE WHEN status = 'ACCEPTED' THEN 'ACCEPTED' ELSE 'PENDING' END,
       COALESCE(created_at, now())
FROM ai_job_recommendations WHERE status IN ('CLIENT_REQUESTED', 'ACCEPTED')
ON CONFLICT (job_id, freelancer_id) DO NOTHING;

INSERT INTO job_invitations (job_id, freelancer_id, source, source_id, status, created_at)
SELECT job_id, freelancer_id, 'PROPOSAL', id,
       CASE WHEN status = 'ACCEPTED' THEN 'ACCEPTED' ELSE 'PENDING' END,
       COALESCE(created_at, now())
FROM proposals WHERE status IN ('INVITED', 'ACCEPTED')
ON CONFLICT (job_id, freelancer_id) DO UPDATE
SET status = CASE WHEN excluded.status = 'ACCEPTED' THEN 'ACCEPTED' ELSE job_invitations.status END;

INSERT INTO job_invitations (job_id, freelancer_id, source, status, created_at)
SELECT job_id, freelancer_id, 'RECOMMENDATION', 'ACCEPTED', COALESCE(created_at, now())
FROM chat_rooms
ON CONFLICT (job_id, freelancer_id) DO UPDATE SET status = 'ACCEPTED';

UPDATE job_invitations i SET status = 'SELECTED'
FROM jobs j WHERE i.job_id = j.id AND i.freelancer_id = j.selected_freelancer_id;

UPDATE job_invitations i SET status = 'NOT_SELECTED'
FROM jobs j WHERE i.job_id = j.id AND j.status <> 'OPEN' AND i.status IN ('PENDING', 'ACCEPTED');
