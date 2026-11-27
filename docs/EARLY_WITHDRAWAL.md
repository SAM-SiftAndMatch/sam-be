# Yêu cầu rút tiền sớm

Triển khai trong sam-be và sam-fe, migration V52. Đây là ngoại lệ xét duyệt cho từng lệnh chi; không sửa `accepted_at`, `available_at` hoặc thời hạn tạo ticket bảo trì. Các lệnh cũ được phân loại `NORMAL`.

Freelancer vào **Thu nhập & thanh toán → Số dư theo dự án**, chọn **Xin rút tiền sớm** ở dự án còn trong 168 giờ bảo trì. Điều kiện: dự án gốc đã nghiệm thu, nguồn LIVE đã xác minh, không có ticket bảo trì đang mở, không có yêu cầu rút sớm đang xử lý. Mỗi yêu cầu chỉ dùng một khoản công nợ. Chỉ rút phần thù lao sau phí còn lại; tiền hoàn cọc vẫn chờ luồng thông thường.

## API

- `POST /api/v1/finance/withdrawals/early`: multipart/form-data. Part `request` là JSON (`Content-Type: application/json`) gồm `bankAccountId`, `requestKey`, `payableId`, `amount`, `reason`, `password`; lặp part `images` cho 1–5 ảnh JPG/PNG/WebP, tối đa 5 MiB mỗi ảnh. Lý do bắt buộc, tối đa 2000 ký tự. Chỉ role FREELANCER; xác thực lại mật khẩu và rate limit fail-closed. Ảnh và lệnh chi được lưu trong cùng transaction; lỗi ảnh hoặc lỗi ghi dữ liệu không tạo yêu cầu và không giữ tiền.
- `GET /api/v1/finance/withdrawals/{id}`: chủ yêu cầu hoặc admin; yêu cầu EARLY có thêm `project`, `requestReason`, `images`.
- `GET /api/v1/finance/withdrawals/{id}/images/{imageId}`: trả binary qua xác thực, chỉ chủ yêu cầu hoặc admin. Không phát hành URL lưu trữ công khai.
- `GET /api/v1/finance/admin/withdrawals?type=EARLY&page=0`: lọc yêu cầu rút sớm. Bỏ `type` để lấy tất cả; có thể dùng `NORMAL`.
- `POST /api/v1/finance/admin/withdrawals/{id}/approve`: giữ endpoint hiện có; body có `password` và `note`. `note` bắt buộc với EARLY, tối đa 1000 ký tự. Các endpoint chuyển khoản, từ chối, hủy, đối soát tiếp tục sử dụng lệnh này.

## Tính nhất quán và bảo mật

Mã `requestKey` phải giữ nguyên khi retry cùng nội dung. Đổi số tiền, ngân hàng, dự án, lý do hoặc ảnh phải dùng mã mới. Retry cùng mã chỉ trả yêu cầu đã tạo; nội dung khác bị từ chối. Khóa theo freelancer đồng bộ với rút thường và tạo ticket bảo trì, cùng unique index bảo đảm tối đa một lệnh EARLY đang xử lý trên mỗi khoản công nợ.

Tiền được giữ qua `reserved` và bảng phân bổ hiện có, không cộng khoản được duyệt vào số dư khả dụng chung. Khi đủ 168 giờ, yêu cầu đang xử lý vẫn giữ tiền; không phát sinh yêu cầu thứ hai. Hủy hoặc từ chối giải phóng reservation, ví tự phân loại theo thời gian và ticket hiện tại.

Lý do, dự án, loại yêu cầu, số tiền và ảnh bất biến sau khi tạo. Ảnh kiểm tra nội dung và định dạng ở backend, mã hóa AES-GCM bằng keyring tài chính hiện có, AAD gắn với chủ sở hữu và mã ảnh. Bảng ảnh nằm trong backup database; cần dự trù tối đa khoảng 34 MiB ciphertext cho một yêu cầu đủ 5 ảnh lớn. Không log multipart, mật khẩu hoặc ảnh. API tài chính trả `Cache-Control: no-store`.

Client chỉ nhận thông báo quyết định duyệt chi sớm, không nhận lý do hoặc ảnh. Admin nhận thông báo yêu cầu mới; freelancer nhận cập nhật trạng thái. Nội dung bằng chứng không được đưa vào chat hoặc thông báo.

## Chi trả và bảo trì

`FINANCE_LIVE_PAYOUTS_ENABLED` và các hạn mức vẫn áp dụng. `FINANCE_DUAL_CONTROL` mặc định true: người chuyển khoản khác người duyệt. Ngân hàng phải còn VERIFIED khi duyệt và bắt đầu chuyển. Ticket mới vẫn chặn duyệt hoặc bắt đầu chi, kể cả sau khi đã duyệt chi sớm.

Nếu có chặn mới khi khoản tiền có thể đã được chuyển, đưa lệnh sang `RECONCILIATION_REQUIRED` trước khi ghi nhận kết quả thực tế. Khi bật kiểm soát kép, người đưa sang đối soát phải khác người thực hiện chuyển khoản. Chỉ người nhận thực hiện chuyển khoản xác nhận đã chi với mã giao dịch và chứng từ; ghi nhận trong đối soát không tự bỏ qua nguồn sandbox hoặc nguồn công nợ sai. Không tự trả reservation chỉ vì có ticket mới hoặc timeout. Khi đã chi, ticket bảo trì vẫn xử lý, không tự thu hồi giao dịch ngân hàng.

Chính sách tham gia được cập nhật thành phiên bản 2026-11-27; hồ sơ cần chấp nhận bản mới khi tham gia công việc tiếp theo. Nội dung hợp đồng đã lưu trước đó không bị ghi đè.
