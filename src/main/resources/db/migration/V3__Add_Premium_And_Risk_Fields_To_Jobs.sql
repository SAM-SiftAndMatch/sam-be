-- Thêm các trường cho AI Risk Radar và File Đặc tả (SRS)
ALTER TABLE jobs ADD COLUMN srs_document_url VARCHAR(500);
ALTER TABLE jobs ADD COLUMN risk_level VARCHAR(20);

-- Thêm các trường cho tính năng Trả phí (Premium Add-ons)
ALTER TABLE jobs ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE jobs ADD COLUMN is_urgent_hiring BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE jobs ADD COLUMN requires_ai_qa BOOLEAN NOT NULL DEFAULT FALSE;