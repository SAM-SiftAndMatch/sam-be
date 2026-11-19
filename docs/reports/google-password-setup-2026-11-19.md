# Thiết lập mật khẩu SAM cho tài khoản Google

Ngày: 19/11/2026. Nhánh BE và FE: `feat/google-password-setup`.

## Hành vi mới

- Dưới ô **Mật khẩu hiện tại** của form tài khoản nhận tiền có hướng dẫn: “Nếu bạn sử dụng Google để đăng nhập, hãy thiết lập mật khẩu SAM để có thể xác thực thêm tài khoản ngân hàng.”
- Liên kết **thiết lập mật khẩu** mở `/account/password/setup`, yêu cầu người dùng đã đăng nhập SAM.
- Người dùng xác nhận đúng tài khoản Google đã liên kết, sau đó nhập mật khẩu SAM mới và xác nhận mật khẩu.
- Sau khi thành công, liên kết quay lại mở lại form thêm tài khoản nhận tiền. Người dùng nhập lại thông tin ngân hàng và mật khẩu SAM vừa tạo.
- Mật khẩu này dùng chung cho đăng nhập SAM bằng email và xác thực thao tác tài chính. Người dùng tiếp tục đăng nhập Google như trước; mật khẩu Google không thay đổi.
- Lỗi của tài khoản chưa có mật khẩu hướng dẫn chọn **"Thiết lập mật khẩu"** thay vì **"Quên mật khẩu"**. Thay đổi này không bổ sung chức năng khôi phục mật khẩu đã quên.

## Kiểm soát ở backend

| API | Điều kiện |
| --- | --- |
| `GET /api/v1/auth/password/status` | JWT hợp lệ; trả trạng thái mật khẩu, điều kiện thiết lập và xác nhận Google còn hiệu lực |
| `POST /api/v1/auth/password/setup/start` | JWT hợp lệ, Origin đúng frontend đã cấu hình, tài khoản hoạt động và chưa có mật khẩu SAM |
| `POST /api/v1/auth/password/setup` | Các điều kiện trên và bằng chứng Google đúng người dùng, còn hạn, chưa dùng |

- Dùng OAuth hiện có với state, nonce, PKCE và kiểm tra ID token. Google identity phải khớp liên kết `issuer + subject` của chính người dùng SAM; không liên kết tự động theo email.
- Bằng chứng thiết lập lưu trong Redis 5 phút, tiêu thụ một lần bằng `GETDEL`; trình duyệt chỉ nhận cookie HttpOnly, SameSite=Lax, Secure theo cấu hình môi trường. Bằng chứng không xuất hiện trong URL hoặc browser storage.
- Bằng chứng gắn với user ID. Việc refresh phiên SAM sau khi quay lại từ Google không làm mất xác nhận; phiên của người dùng khác không thể sử dụng bằng chứng đó.
- Khóa dòng người dùng trong transaction, kiểm tra lại mật khẩu chưa tồn tại rồi mới ghi hash BCrypt. Không ghi đè mật khẩu đã có, kể cả khi hai lần xác nhận diễn ra gần nhau.
- Mật khẩu tối thiểu 8 ký tự, tối đa 72 byte UTF-8, xác nhận phải khớp. Không cắt ngắn hoặc trim mật khẩu. DTO che mật khẩu khi gọi `toString()`.
- Hai thao tác POST giới hạn 5 lần mỗi 300 giây theo người dùng, từ chối khi kho rate limit không hoạt động. Phản hồi API đặt `Cache-Control: no-store`.
- Nếu lưu DB lỗi sau khi bằng chứng đã được tiêu thụ, người dùng phải xác nhận Google lại. Frontend tải lại trạng thái sau lỗi và xóa hai ô mật khẩu.
- Xác nhận hết hạn trả HTTP 400 kèm hướng dẫn xác nhận lại Google, tránh kích hoạt cơ chế tự refresh JWT của frontend cho một lỗi không liên quan tới phiên SAM.

Đây là bước thiết lập thông tin đăng nhập lần đầu. Việc chọn tài khoản Google không được xem là bằng chứng người dùng vừa nhập lại mật khẩu Google hoặc hoàn thành MFA. Bằng chứng thiết lập không được dùng trực tiếp để duyệt rút tiền; các API tài chính tiếp tục kiểm tra mật khẩu SAM và áp dụng các kiểm soát hiện có.

## Kết quả kiểm thử local

- BE toàn dự án: 303 kiểm thử, 268 thành công, 35 bỏ qua do môi trường tích hợp chưa có và application context test được đánh dấu disabled sẵn; không có lỗi.
- Chạy lại 26 kiểm thử Google/thiết lập mật khẩu sau chỉnh sửa cuối: tất cả thành công. Bao gồm sai người dùng Google, bằng chứng thiếu/hết hạn/dùng lại, không ghi đè mật khẩu, hash và giới hạn UTF-8, cookies, Origin và API từ chối truy cập ẩn danh.
- Python kiểm tra migration an toàn: 1 kiểm thử thành công.
- FE: toàn bộ 12 file kiểm thử thành công; file mới có 8 trường hợp kiểm tra chuyển hướng, xác nhận từ backend, gửi/xóa mật khẩu và xử lý lỗi.
- TypeScript và Vite production build thành công; Biome CI kiểm tra 193 file thành công.
- Spotless kiểm tra các file Java thay đổi. OAuth/Redis được mock trong các kiểm thử mới; chưa thực hiện OAuth thật trên staging.

## Triển khai và kiểm tra thực tế

- Triển khai BE trước FE; không có migration DB hoặc biến môi trường mới. Dùng lại Google callback URI, frontend origin và Redis hiện có.
- Thử trên staging bằng một tài khoản Google mới chưa có mật khẩu: mở form ngân hàng → thiết lập → xác nhận Google đúng → tạo mật khẩu → quay lại thêm ngân hàng.
- Thử chọn Google khác, hủy Google, để xác nhận quá 5 phút, mở lại trang sau khi đã thiết lập. Các trường hợp này không được ghi đè hoặc tạo mật khẩu trái điều kiện.
- Sau khi thiết lập, kiểm tra cả đăng nhập Google, đăng nhập email/mật khẩu SAM và thêm ngân hàng bằng mật khẩu SAM mới. Không dùng mật khẩu Google trong form SAM.
- Kiểm thử tự động dùng mock cho Google/Redis; luồng OAuth thật, cookie trên domain triển khai và khóa dòng trên DB thật cần được kiểm tra ở staging.
