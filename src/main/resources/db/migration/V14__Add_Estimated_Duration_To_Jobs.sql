-- Add estimated_duration_months column to jobs table
-- This represents the estimated project duration in months
-- Deadline will be set later when contract is signed, not at job creation

ALTER TABLE jobs
ADD COLUMN estimated_duration_months INTEGER;

-- Make deadline nullable (it already is, but documenting the intent)
-- Deadline will be calculated and set when freelancer-client sign contract
COMMENT ON COLUMN jobs.deadline IS 'Actual deadline set when contract is signed, calculated from contract start date + estimated_duration_months';
COMMENT ON COLUMN jobs.estimated_duration_months IS 'Estimated project duration in months from AI SRS analysis';
