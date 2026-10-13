CREATE TABLE project_timeline_tasks (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    week_number INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'TODO',
    progress INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_project_task_order UNIQUE (job_id, sort_order),
    CONSTRAINT ck_project_task_week CHECK (week_number > 0),
    CONSTRAINT ck_project_task_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX idx_project_task_job_week
    ON project_timeline_tasks(job_id, week_number, sort_order);

CREATE TABLE project_task_reports (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES project_timeline_tasks(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    evidence_url VARCHAR(1000),
    progress INTEGER NOT NULL,
    reported_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT ck_project_report_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX idx_project_report_task_created
    ON project_task_reports(task_id, created_at DESC);

CREATE TABLE project_deliveries (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL REFERENCES users(id),
    version_number INTEGER NOT NULL,
    web_url VARCHAR(1000) NOT NULL,
    repository_url VARCHAR(1000),
    note TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    revision_reason TEXT,
    submitted_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_project_delivery_version UNIQUE (job_id, version_number)
);

CREATE INDEX idx_project_delivery_job_version
    ON project_deliveries(job_id, version_number DESC);
