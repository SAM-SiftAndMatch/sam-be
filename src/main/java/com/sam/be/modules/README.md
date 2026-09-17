# Modules Package (`com.sam.be.modules`)

Thư mục chứa các Module chức năng (Domain / Feature Modules) của ứng dụng theo kiến trúc **Modular Monolith** / **Package-by-Feature**.

## Cấu trúc thư mục đề xuất

Mỗi module trong thư mục này đại diện cho một miền nghiệp vụ riêng biệt (ví dụ: `auth`, `user`, `product`, `matching`, v.v.).

```text
modules/
├── auth/                 # Module xác thực & phân quyền (Authentication & Authorization)
├── user/                 # Module quản lý người dùng (User Management)
└── [feature_name]/       # Các domain modules khác
```

### Cấu trúc chuẩn bên trong một Module (Ví dụ `user` module)

```text
modules/user/
├── controller/           # REST Controllers (@RestController) xử lý HTTP Requests
├── dto/                  # Request & Response DTOs
│   ├── request/          # Class dữ liệu đầu vào (UserCreateRequest, v.v.)
│   └── response/         # Class dữ liệu đầu ra (UserResponse, v.v.)
├── entity/               # JPA Entities / Data Models đại diện cho bảng dữ liệu
├── mapper/               # Class / Interface MapStruct chuyển đổi giữa Entity và DTO
├── repository/           # Spring Data Repositories (@Repository) truy vấn Database
└── service/              # Logic nghiệp vụ (@Service)
    ├── impl/             # Class triển khai Service Interface
    └── UserService.java  # Interface định nghĩa các phương thức nghiệp vụ
```

## Quy tắc phát triển Module
1. **Độc lập tương đối**: Mỗi module nên tự quản lý Controller, Service, Repository, Entity và DTO của riêng mình.
2. **Giao tiếp giữa các module**: Khi module A cần gọi logic của module B, khuyên dùng Service Interface của module B thay vì trực tiếp thao tác trên Repository hay Entity của module B.
3. **Phân tách trách nhiệm**:
   - `Controller`: Chỉ nhận request, validate căn bản và gọi Service, trả về `ApiResponse`.
   - `Service`: Chứa toàn bộ Business Logic.
   - `Repository`: Chỉ tương tác dữ liệu Database.
