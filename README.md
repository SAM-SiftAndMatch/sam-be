# 🚀 SAM Backend (`sam-be`)

> Backend service cho hệ thống SAM (Sift And Match), xây dựng trên nền tảng **Java 21**, **Spring Boot**, **PostgreSQL**, **Redis** và kiến trúc **Modular Monolith**.

---

## 📋 Mục lục
- [Yêu cầu môi trường](#-yêu-cầu-môi-trường)
- [Bắt đầu nhanh (Quick Start)](#-bắt-đầu-nhanh-quick-start)
- [Các chế độ chạy ứng dụng](#-các-chế-độ-chạy-ứng-dụng)
- [Bảng lệnh Makefile](#-bảng-lệnh-makefile)
- [Cấu trúc dự án (Architecture)](#-cấu-trúc-dự-án-architecture)
- [Quy chuẩn code & Git Commit](#-quy-chuẩn-code--git-commit)
- [Database Migration (Flyway)](#-database-migration-flyway)
- [Kiểm tra sức khỏe hệ thống (Health Check)](#-kiểm-tra-sức-khỏe-hệ-thống-health-check)

---

## 💻 Yêu cầu môi trường

Để chạy và phát triển dự án, máy của bạn cần cài đặt:
- **Java**: OpenJDK 21 trở lên
- **Docker & Docker Compose**: Để chạy Postgres, Redis và container hóa
- **Python 3**: Để cài đặt và quản lý `pre-commit` hooks
- **Make** *(tùy chọn nhưng khuyến khích)*: Chạy nhanh các phím tắt lệnh

---

## ⚡ Bắt đầu nhanh (Quick Start)

### 1. Cấu hình biến môi trường
Tạo file `.env` từ file mẫu:
```bash
cp .env.example .env
```
*(Bạn có thể chỉnh sửa thông tin kết nối DB, Redis hoặc Port trong `.env` nếu cần).*

### 2. Cài đặt Git Hooks (Bắt buộc)
Dự án sử dụng `pre-commit` để quét secret (Gitleaks), format code và chuẩn hóa commit message:
```bash
make setup
# Hoặc chạy thủ công: bash scripts/setup-precommit.sh
```

---

## 🛠️ Các chế độ chạy ứng dụng

### Cách 1: Chạy Local Dev (Khuyến khích khi lập trình tính năng)
Chạy Database (PostgreSQL) và Cache (Redis) trên Docker, chạy Backend trực tiếp trên IDE hoặc Terminal để hỗ trợ hot-reload & debug:

1. **Khởi động Postgres & Redis**:
   ```bash
   make infra-up
   ```
2. **Chạy Spring Boot Backend**:
   - Mở dự án trong IntelliJ IDEA / VS Code và chạy `SamBeApplication.java`.
   - Hoặc chạy qua dòng lệnh:
     ```bash
     ./mvnw spring-boot:run
     ```
3. **Khi xong việc, dừng hạ tầng**:
   ```bash
   make infra-down
   ```

---

### Cách 2: Chạy Full-Stack Container hóa (Giống môi trường Production)
Chạy toàn bộ Postgres, Redis và Backend trong Docker Compose:

1. **Khởi động toàn bộ**:
   ```bash
   make up
   ```
2. **Theo dõi Logs**:
   ```bash
   make logs
   ```
3. **Dừng hệ thống**:
   ```bash
   make down
   ```

---

## ⌨️ Bảng lệnh Makefile

Dự án đã thiết lập sẵn các lệnh tắt trong [`Makefile`](Makefile):

| Nhóm | Lệnh | Mô tả |
| :--- | :--- | :--- |
| **Toàn bộ hệ thống** | `make up` | Khởi động toàn bộ container (Postgres, Redis, Backend) ở chế độ detached |
| | `make down` | Dừng và xóa toàn bộ container của dự án |
| | `make logs` | Xem logs realtime của toàn bộ container |
| **Hạ tầng Local Dev** | `make infra-up` | Chỉ khởi động Postgres và Redis (dành cho dev chạy code trong IDE) |
| | `make infra-down` | Tắt Postgres và Redis |
| **Quản lý Redis riêng** | `make redis-up` | Chỉ bật Redis |
| | `make redis-down` | Tắt Redis |
| | `make redis-reset` | Xóa sạch dữ liệu cache của Redis volume |
| | `make redis-logs` | Xem logs của Redis |
| | `make redis-ping` | Ping kiểm tra kết nối Redis (`PONG`) |
| | `make redis-cli` | Mở trực tiếp terminal `redis-cli` vào container |
| **Chất lượng mã nguồn** | `make setup` | Cài đặt tự động `pre-commit` git hooks |
| | `make pc` | Chạy thủ công toàn bộ pre-commit hooks trên tất cả các file |
| | `make fmt` | Tự động format toàn bộ mã nguồn Java theo chuẩn Google Java Format |

---

## 🏗️ Cấu trúc dự án (Architecture)

Dự án được tổ chức theo kiến trúc **Modular Monolith** & **Package-by-Feature**:

```text
src/main/java/com/sam/be/
├── infrastructure/     # Tích hợp kỹ thuật bên ngoài & Hạ tầng
│   ├── cache/          # Quản lý Cache (Redis templates, cache service)
│   ├── config/         # Cấu hình kỹ thuật, Healthcheck controller
│   └── thirdparty/     # Client/Adapter giao tiếp dịch vụ bên thứ 3 (S3, Mail,...)
│
├── common/             # Thành phần dùng chung toàn ứng dụng (Cross-cutting)
│   ├── audit/          # JPA Auditing (CreatedDate, LastModifiedDate,...)
│   ├── config/         # Cấu hình chung (CORS, Swagger, Jackson,...)
│   ├── constant/       # Khai báo các hằng số dùng chung
│   ├── exception/      # Quản lý ngoại lệ toàn cục (GlobalExceptionHandler)
│   ├── response/       # Chuẩn hóa format API Response (ApiResponse<T>)
│   └── util/           # Utility static helper methods
│
└── modules/            # Các Domain/Feature modules nghiệp vụ
    ├── auth/           # Xác thực & phân quyền (nếu có)
    └── [feature]/      # Controller, Service, Repository, Entity, DTO, Mapper
```

---

## 📏 Quy chuẩn code & Git Commit

### 1. Định dạng Code Java (Spotless)
Dự án áp dụng **Google Java Format (AOSP Style)**.
Trước khi commit hoặc push code, hãy chạy:
```bash
make fmt
```
Nếu code chưa được format đúng, GitHub Actions CI sẽ từ chối build.

### 2. Chuẩn hóa Commit Message (Conventional Commits)
Commit hook sẽ tự động kiểm tra định dạng commit message:
```text
<type>(<optional-scope>): <mô tả ngắn gọn>
```

**Các `type` hợp lệ**:
- `feat`: Tính năng mới
- `fix`: Sửa lỗi (bug fix)
- `docs`: Tài liệu hướng dẫn
- `style`: Định dạng, thiếu dấu chấm phẩy,... (không đổi logic code)
- `refactor`: Tái cấu trúc code (không thêm tính năng, không sửa bug)
- `perf`: Cải thiện hiệu năng
- `test`: Thêm hoặc sửa test cases
- `build`: Thay đổi hệ thống build (pom.xml, dependencies,...)
- `ci`: Thay đổi cấu hình CI/CD (GitHub Actions,...)
- `chore`: Công việc bảo trì khác

**Ví dụ hợp lệ**:
- `feat(user): add api register new account`
- `fix(auth): fix jwt token expiration time`
- `chore: update dependencies in pom.xml`

---

## 🗄️ Database Migration (Flyway)

- Dự án sử dụng **Flyway** để quản lý thay đổi lược đồ cơ sở dữ liệu.
- Mọi thay đổi bảng, cột dữ liệu phải được viết bằng file script SQL đặt trong:
  ```text
  src/main/resources/db/migration/
  ```
- **Quy tắc đặt tên file**:
  `V<Số_phiên_bản>__<Mô_tả_ngắn>.sql` (chú ý có **2 dấu gạch dưới** `__`)
  *Ví dụ*: `V1__init_schema.sql`, `V2__add_phone_to_users.sql`.
- `spring.jpa.hibernate.ddl-auto` được đặt là `validate` để đảm bảo Hibernate không tự ý thay đổi cấu trúc bảng sau lưng Flyway.

---

## 🩺 Kiểm tra sức khỏe hệ thống (Health Check)

Khi ứng dụng chạy (cổng mặc định `8080`), bạn có thể kiểm tra trạng thái hoạt động qua:
- **Internal Health Check**: [http://localhost:8080/api/v1/internal/healthz](http://localhost:8080/api/v1/internal/healthz)
- **Spring Boot Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
