# Tải biên lai chuyển khoản khi xác nhận đã chi

Migration **V53__Private_Payout_Receipt_Images.sql** bổ sung ảnh biên lai cho lệnh chi. Không sửa V52 đã triển khai. Dùng keyring `FINANCE_BANK_KEYS` hiện có để mã hóa ảnh, không cần Cloudinary hoặc biến môi trường mới.

## Cách dùng

Admin vào **Chi trả freelancer → Xác nhận đã chi**, nhập mật khẩu SAM, ghi chú đối soát và mã giao dịch ngân hàng. Chọn 1–5 ảnh biên lai JPG/PNG/WebP, tối đa 5 MiB/ảnh. Form có xem trước và bỏ ảnh; không cần lưu biên lai ở nơi khác hoặc nhập link. Tên tham chiếu `CT-<mã lệnh chi>` được hệ thống tạo tự động.

Sau khi thành công, vào **Xem chi tiết → Biên lai chuyển khoản** để xem ảnh. Chỉ admin được xem ảnh biên lai; freelancer vẫn xem được trạng thái, mã giao dịch và lịch sử chi trả như trước. Ảnh bằng chứng xin rút sớm và ảnh biên lai của admin được lưu riêng, áp dụng quyền truy cập riêng.

## API

- `POST /api/v1/finance/admin/withdrawals/{id}/confirm-manual-payment`, `multipart/form-data`: part `request` là JSON với Content-Type `application/json`, gồm `password`, `bankReference`, `note`. Các part `images` chứa 1–5 file ảnh. Xác thực lại mật khẩu, role ADMIN và rate limit fail-closed như luồng xác nhận cũ.
- `GET /api/v1/finance/withdrawals/{id}`: admin có thêm `receiptImages` với metadata ảnh. Response của chủ yêu cầu không có trường này.
- `GET /api/v1/finance/admin/withdrawals/{id}/receipt-images/{imageId}`: trả binary sau khi kiểm tra quyền ADMIN; `Cache-Control: no-store`, không cung cấp URL ảnh công khai.
- Endpoint xác nhận dạng JSON cũ vẫn nhận `evidenceReference` để tương thích client cũ. Chứng từ dạng mã/link đã lưu tiếp tục hiển thị trong chi tiết lệnh chi.

## Tính nhất quán

Chỉ người được phân công thực hiện chuyển khoản có thể gắn ảnh và xác nhận lệnh đang PROCESSING hoặc RECONCILIATION_REQUIRED. Các kiểm tra duyệt chi, nguồn tiền, ticket chặn chi và đối soát hiện có vẫn áp dụng.

Ảnh được kiểm tra định dạng và dung lượng tại backend rồi mã hóa trước khi lưu database. Ghi ảnh, mã giao dịch, sổ tài chính và trạng thái PAID trong cùng transaction. Lỗi tải ảnh hoặc lỗi ghi sổ sẽ rollback toàn bộ, giữ nguyên tiền đã dành riêng cho lệnh đang xử lý.

Retry cùng mã giao dịch và cùng các ảnh trả thành công mà không lưu thêm ảnh hay ghi sổ lần hai. Mã giao dịch hoặc ảnh khác sau khi PAID bị từ chối. Ảnh đã nộp không thể sửa, xóa hoặc bổ sung sau khi lệnh được xác nhận. Ràng buộc database không cho lưu ảnh rời mà không hoàn tất lệnh chi.

Ảnh nằm trong backup database; một bộ 5 ảnh tối đa cần khoảng 34 MiB dữ liệu mã hóa. Không đưa mật khẩu, ảnh biên lai hoặc URL công khai chứa thông tin người nhận vào log/chat/thông báo.

## Triển khai

Đưa migration V53 cùng backend lên server trước frontend. Backend dùng Flyway sẽ áp dụng V53 khi khởi động; nếu chạy migration riêng, nâng target lên 53. Không cần đổi khóa tài chính của database hiện tại.
