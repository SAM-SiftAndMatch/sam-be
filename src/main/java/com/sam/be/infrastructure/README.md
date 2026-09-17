# Infrastructure Layer (`com.sam.be.infrastructure`)

Thư mục chứa các cấu hình hạ tầng, tích hợp dịch vụ bên thứ 3 (Third-party services) và hệ thống Cache / Message Queue.

## Cấu trúc thư mục đề xuất

```text
infrastructure/
├── cache/          # Cấu hình & Service quản lý Cache (Redis, Spring Cache, v.v.)
├── config/         # Cấu hình hạ tầng kỹ thuật (RedisConfig, StorageConfig, MailConfig, v.v.)
└── thirdparty/     # Tích hợp dịch vụ bên thứ 3 (Payment Gateway, Cloud Storage S3, SMS, Mail, v.v.)
```

## Quy tắc sử dụng
- **Cache**: Đặt các class cấu hình Redis Template, CacheManager, Key Generators và Cache Services tại đây.
- **Config**: Chỉ chứa các cấu hình hạ tầng kết nối hệ thống bên ngoài.
- **Thirdparty**: Đặt các Client / Adapter giao tiếp với API bên thứ 3. Không để logic nghiệp vụ domain tại đây.
