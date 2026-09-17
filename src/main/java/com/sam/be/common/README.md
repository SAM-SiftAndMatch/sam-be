# Common Package (`com.sam.be.common`)

Thư mục chứa các thành phần dùng chung cho toàn bộ ứng dụng (Cross-cutting Concerns).

## Cấu trúc thư mục đề xuất

```text
common/
├── audit/          # Cấu hình & Listener cho Audit Log (JPA Auditing CreatedDate, LastModifiedDate, v.v.)
├── config/         # Cấu hình dùng chung ứng dụng (CorsConfig, Swagger/OpenAPI, JacksonConfig, Security Base)
├── constant/       # Định nghĩa các hằng số dùng chung (AppConstants, MessageConstants, v.v.)
├── exception/      # Quản lý Ngoại lệ toàn cục (GlobalExceptionHandler, CustomExceptions, ErrorCode enum)
├── response/       # Chuẩn hóa dữ liệu phản hồi API (ApiResponse<T>, PageResponse<T>, ResponseStatus)
└── util/           # Các hàm tiện ích static (DateUtils, StringUtils, SecurityUtils, v.v.)
```

## Quy tắc sử dụng
- Các class trong `common` phải mang tính chất tổng quát và không phụ thuộc vào bất kỳ domain module cụ thể nào trong `modules`.
- Đảm bảo tính tái sử dụng cao trên toàn dự án.
