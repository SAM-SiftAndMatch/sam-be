-- Keep existing purchases for history, but stop selling paid QA until its scope is defined.
UPDATE service_packages
SET is_active = FALSE, updated_at = NOW()
WHERE id IN (
    'f0000000-0000-0000-0000-000000000003',
    'f0000000-0000-0000-0000-000000000007'
);

UPDATE service_packages
SET description = 'Ưu tiên dự án trên danh sách tìm kiếm khi còn tuyển, đến hết hạn nhận ứng tuyển.',
    updated_at = NOW()
WHERE id = 'f0000000-0000-0000-0000-000000000001';
