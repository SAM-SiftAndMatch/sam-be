# Đánh giá và góp ý nền tảng SAM

## Phạm vi

Đánh giá nền tảng được lưu riêng trong `platform_feedback`. Bảng `reviews` hiện có phục vụ đánh giá các bên của hợp đồng, không được dùng để lưu feedback về SAM.

- Khách hàng và freelancer đã đăng nhập mở menu tài khoản → **Đánh giá & góp ý**, hoặc liên kết **Đánh giá SAM** ở footer. Route: `/feedback`.
- Mỗi góp ý gồm số sao bắt buộc từ 1–5 và nhận xét tùy chọn, tối đa 2.000 ký tự. Người dùng được gửi góp ý mới theo thời gian và xem lịch sử riêng có phân trang.
- Nội dung không được công bố ra trang giới thiệu hoặc cho người dùng khác. Người gửi chỉ thấy số sao, nhận xét và trạng thái xử lý; ghi chú nội bộ và người xử lý chỉ xuất hiện trong API/admin UI dành cho admin.
- Admin mở **Đánh giá & góp ý** trong sidebar, route `/admin/feedback`: xem điểm trung bình, số lượng theo sao/trạng thái, tìm theo tên/email/nội dung, lọc số sao/trạng thái và xem chi tiết.
- Admin cập nhật `NEW` (Mới tiếp nhận), `IN_PROGRESS` (Đang xem xét), `RESOLVED` (Đã xử lý), kèm ghi chú nội bộ. Có thể mở lại góp ý bằng cách đổi trạng thái.

Điểm trung bình và phân bố sao tính trên toàn bộ bản ghi đánh giá, không phải điểm duy nhất của mỗi người dùng. Bộ lọc chỉ áp dụng cho danh sách, không đổi số liệu tổng quan.

## API

Tất cả endpoint đều yêu cầu JWT. API admin được bảo vệ bằng quyền ADMIN ở cả controller và service.

| Phương thức | Endpoint | Chức năng |
| --- | --- | --- |
| POST | `/api/v1/feedback` | Gửi đánh giá; CLIENT/FREELANCER |
| GET | `/api/v1/feedback/mine?page=0&size=10` | Chỉ lấy lịch sử của người đăng nhập |
| GET | `/api/v1/admin/feedback?rating=5&status=NEW&q=...&page=0&size=20` | Danh sách admin, các bộ lọc tùy chọn |
| GET | `/api/v1/admin/feedback/summary` | Tổng quan toàn bộ đánh giá |
| GET | `/api/v1/admin/feedback/{id}` | Chi tiết admin, có ghi chú nội bộ |
| PATCH | `/api/v1/admin/feedback/{id}` | Cập nhật trạng thái và ghi chú |

Gửi đánh giá:

```json
{"rating":5,"comment":"Dễ sử dụng, mong có thêm bộ lọc tìm kiếm.","requestKey":"d08ef6e2-16dd-4b1c-81f8-774eeb76bf12"}
```

Cập nhật của admin:

```json
{"status":"IN_PROGRESS","adminNote":"Đã chuyển nhóm phụ trách xem xét.","version":0}
```

- `requestKey` là UUID được FE giữ nguyên khi thử gửi lại sau lỗi và tạo mới sau khi gửi thành công. Khóa chỉ duy nhất trong phạm vi người gửi. Cùng khóa và nội dung trả về bản ghi cũ; cùng khóa nhưng nội dung khác trả 409. Khóa dòng người gửi và unique constraint ngăn hai lần gửi đồng thời tạo bản ghi trùng.
- Giới hạn gửi cấu hình 5 lượt/giờ/người dùng qua rate limiter hiện có. Kho rate limit áp dụng chính sách fallback chung của hệ thống; không có giới hạn mỗi người chỉ được đánh giá một lần.
- `version` lấy từ chi tiết đang xem. Backend khóa bản ghi, kiểm tra phiên bản rồi cập nhật bằng JPA versioning. Nếu admin khác đã lưu, trả 409; FE yêu cầu tải bản mới trước khi gửi tiếp.
- Số trang từ 0–100.000, kích thước trang 1–100. Nhận xét/ghi chú tối đa 2.000 ký tự; từ khóa tìm tối đa 100 ký tự. Ký tự `%`, `_`, `\` trong từ khóa được xử lý như ký tự thường.
- Nội dung được hiển thị bằng văn bản thông thường; không render HTML do người dùng cung cấp. Admin không có API sửa số sao hoặc nhận xét gốc.
- Toast phân biệt gửi/lưu thành công, lỗi thao tác và lỗi tải lại dữ liệu sau khi thao tác đã thành công.

## Triển khai và kiểm tra

- Migration mới: `V48__Platform_Feedback.sql`. Không sửa migration cũ. Triển khai BE để Flyway tạo bảng/index trước khi đưa FE mới lên.
- Không cần thư viện hay biến môi trường mới. Dùng lại React Toastify và cơ chế xác thực/rate limit đang có.
- Kiểm thử unit bao gồm xác thực dữ liệu, dữ liệu riêng của người gửi, gửi lặp, phiên bản cập nhật và bộ lọc. Kiểm thử HTTP xác nhận khách chưa đăng nhập và CLIENT/FREELANCER không được truy cập admin.
- `FeedbackPostgresTest` dùng schema ngẫu nhiên riêng và chỉ chạy khi có `FINANCE_TEST_DATABASE_URL`, `FINANCE_TEST_DATABASE_USER`, `FINANCE_TEST_DATABASE_PASSWORD` của môi trường kiểm thử. Bài kiểm tra áp dụng V48, validate mapping Hibernate, chạy repository/filter/thống kê, kiểm tra gửi đồng thời và phiên bản cập nhật. CI hiện đã cấp các biến môi trường PostgreSQL này.
- Trên staging: thử cả CLIENT/FREELANCER gửi đánh giá → kiểm tra admin nhận, lọc và xử lý → quay lại lịch sử người gửi. Thử hai admin mở cùng góp ý để kiểm tra thông báo 409 và nút tải bản mới.

## Kết quả kiểm tra local ngày 19/11/2026

- BE: 321 kiểm thử, 283 thành công, 38 bỏ qua; không có lỗi. Trong đó 3 bài PostgreSQL của feedback chưa chạy do máy hiện tại không có cấu hình database test. Các bài bỏ qua còn lại thuộc bộ kiểm thử tích hợp/context có sẵn.
- Các kiểm thử feedback chạy được đã kiểm tra service, phân quyền HTTP, validation, dữ liệu riêng, gửi lặp, cập nhật phiên bản và khởi tạo mapping/repository Hibernate không truy cập DB.
- FE: toàn bộ 14 file kiểm thử thành công; file mới có 7 trường hợp về số sao, bộ lọc, gửi lại, hiển thị văn bản an toàn, lỗi tải lại và xung đột cập nhật của admin.
- TypeScript, Vite production build, Biome CI và Spotless trên các file Java thay đổi đều đã qua. Bài Python bảo vệ migration cũ cũng thành công.
- Chưa chạy migration V48 trên staging/production; cần chạy bài PostgreSQL và kiểm tra luồng thực tế trước khi rollout.
