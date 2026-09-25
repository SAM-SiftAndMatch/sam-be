DROP TABLE IF EXISTS job_skills CASCADE;

CREATE TABLE job_skills (
                            job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
                            skill_id INT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
                            required_years_of_experience INT,
                            PRIMARY KEY (job_id, skill_id)
);

ALTER TABLE ai_job_recommendations ADD COLUMN ai_comment TEXT;