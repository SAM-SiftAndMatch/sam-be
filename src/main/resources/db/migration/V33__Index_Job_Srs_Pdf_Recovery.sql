CREATE INDEX idx_jobs_srs_pdf_recovery ON jobs(created_at, id)
    WHERE srs_content IS NOT NULL AND BTRIM(srs_content) <> '';
