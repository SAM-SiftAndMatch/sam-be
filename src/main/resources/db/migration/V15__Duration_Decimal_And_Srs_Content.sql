-- V15: Hỗ trợ thời gian thập phân (vd 3.5 tháng) + lưu nội dung SRS vào DB để render đẹp trong app
ALTER TABLE jobs
    ALTER COLUMN estimated_duration_months TYPE NUMERIC(4, 1);

ALTER TABLE jobs
    ADD COLUMN srs_content TEXT;
