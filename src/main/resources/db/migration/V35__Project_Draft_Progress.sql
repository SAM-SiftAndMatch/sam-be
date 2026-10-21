ALTER TABLE project_drafts
    ADD COLUMN progress_step INTEGER NOT NULL DEFAULT 0
    CONSTRAINT chk_project_drafts_progress_step CHECK (progress_step BETWEEN 0 AND 5);

UPDATE project_drafts
SET progress_step = CASE
    WHEN status = 'PUBLISHED' THEN 5
    WHEN flow_type = 'AI' THEN CASE
        WHEN phase IN ('completed', 'confirm') AND srs_content IS NOT NULL AND btrim(srs_content) <> '' THEN 4
        WHEN phase = 'risk-chat' AND srs_content IS NOT NULL AND btrim(srs_content) <> '' THEN 3
        WHEN phase = 'srs-review' AND srs_content IS NOT NULL AND btrim(srs_content) <> '' THEN 2
        WHEN phase = 'ba-chat' THEN 1
        ELSE 0
    END
    ELSE CASE phase
        WHEN 'confirm' THEN 4
        WHEN 'review' THEN 3
        WHEN 'addons' THEN 2
        WHEN 'budget' THEN 1
        ELSE 0
    END
END;
