-- Keep document limits in the application policy; allow video attachments up to 50 MiB.
ALTER TABLE chat_attachments DROP CONSTRAINT chat_attachments_size_bytes_check;
ALTER TABLE chat_attachments ADD CONSTRAINT chat_attachments_size_bytes_check
    CHECK (size_bytes > 0 AND size_bytes <= CASE
        WHEN content_type IN ('video/mp4', 'video/webm') THEN 52428800
        WHEN content_type LIKE 'image/%' THEN 5242880
        ELSE 10485760 END);
