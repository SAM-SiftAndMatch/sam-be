CREATE TABLE job_srs_pdfs (
    job_id UUID PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    pdf_data BYTEA,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP(6),
    error_message VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_job_srs_pdf_status CHECK (status IN ('PENDING', 'GENERATING', 'READY', 'FAILED')),
    CONSTRAINT ck_job_srs_pdf_ready CHECK ((status = 'READY') = (pdf_data IS NOT NULL))
);
CREATE INDEX idx_job_srs_pdfs_queue ON job_srs_pdfs(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED', 'GENERATING');

-- Existing published projects are handled gradually by the bounded worker.
INSERT INTO job_srs_pdfs (job_id)
SELECT id FROM jobs WHERE NULLIF(BTRIM(srs_content), '') IS NOT NULL;
