# Dashboard admin và khôi phục thanh toán

Cập nhật: 09/11/2026. Phạm vi triển khai: `sam-be`, `sam-fe`.

## Chính sách thanh toán lại

Không cần hai admin duyệt cho mỗi lần người dùng hủy thanh toán. Chỉ chuyển các trường hợp không xác định được tiền hoặc sai môi trường sang vận hành.

| Tình huống | Người dùng thao tác | SAM xử lý |
| --- | --- | --- |
| Link còn hiệu lực | Tiếp tục thanh toán | Dùng lại đơn hiện tại, không tạo thêm đơn. |
| Pay2S gửi IPN hủy hợp lệ, đúng đơn/số tiền, chưa có thông tin nhận tiền | Tạo lại liên kết thanh toán | Cho phép thay thế tự động; không cần admin. |
| Link hết hạn, chưa có thông tin nhận tiền | Tạo lại liên kết thanh toán | Gọi cancel đúng tài khoản Pay2S; xác nhận đóng xong mới tạo đơn thay thế. |
| Lặp yêu cầu hoặc hai tab cùng thao tác | Tạo lại liên kết thanh toán | Trả đơn thay thế đang có; khóa và unique constraint bảo đảm một đơn hoạt động cho một nghĩa vụ. |
| Đã nhận tiền / đang kích hoạt | Kiểm tra lại, quay về dịch vụ | Không tạo thêm khoản thu. |
| Có receipt/IPN thành công, timeout bất định, cancel 404, lệch merchant/demo/LIVE | Cung cấp mã hỗ trợ | Giữ lịch sử, đối soát; không suy ra chưa thanh toán từ một lỗi HTTP. |

Luồng thông thường:

1. Người dùng mở `/payment/return` và bấm **Tạo lại liên kết thanh toán**.
2. Backend kiểm tra chủ đơn, môi trường, thông tin nhận tiền và tác vụ đang chạy.
3. Nếu có bằng chứng đóng đơn hợp lệ thì dùng lại; nếu chưa có, gọi cancel Pay2S ngoài transaction.
4. Sau khi xác nhận, kiểm tra lại dữ liệu dưới khóa rồi gọi lại nghiệp vụ mua gói/nạp hợp đồng/cọc/bảo trì/tiện ích tương ứng. Không dùng giá do trình duyệt gửi lên. Đơn thay thế dùng trang quay lại trong cấu hình hiện hành, không lấy lại domain/localhost đã cũ từ task trước.
5. Lưu đơn thay thế và outbox; giao diện tự chuyển sang mã mới, theo dõi đến khi link sẵn sàng và cho tiếp tục sang Pay2S.
6. IPN thành công kích hoạt nghiệp vụ đúng một lần. Tiền đến mã cũ sau thay thế vẫn được ghi nhận để đối soát, không cấp quyền lần hai.

Điểm cần giữ: **hết hạn trên SAM không chứng minh đơn đã đóng ở Pay2S**. Không mở thanh toán mới chỉ theo giờ hết hạn hoặc nút người dùng bấm. Hai admin duyệt vẫn áp dụng khi phải kết luận thủ công bằng chứng ngoài hệ thống, gồm lỗi demo bị ghi nhầm LIVE trước đây. Đơn đã được duyệt đóng hợp lệ trong cùng môi trường có thể khôi phục sau đổi merchant; không gọi bộ khóa mới để hủy đơn thuộc merchant cũ.

API mới: `POST /api/v1/checkouts/{id}/retry`, chỉ chủ đơn, giới hạn 3 lần/phút/người dùng. API `prepare-retry` cũ được giữ để tương thích mobile; mobile vẫn có thể quay về nghiệp vụ sau bước đóng đơn. Không sửa trạng thái thành PAID từ trang return hoặc từ thao tác hủy.

Tài liệu Pay2S dùng để đối chiếu: [IPN](https://developer.pay2s.vn/docs/api/instant-payment-notification/) và [hủy Collection Link](https://developer.pay2s.vn/docs/api/cancel-order/). Không coi URL quay về trình duyệt là bằng chứng ngân hàng.

## Các trang admin

Sidebar desktop có logo SAM, thu gọn/mở rộng, các route độc lập, avatar/tên/email và đăng xuất ở cuối. Mobile dùng menu dạng ngăn kéo, hỗ trợ nút đóng và Escape. Đổi mục tài chính sẽ xóa state biểu mẫu/mật khẩu/thông tin ngân hàng đang mở của mục trước.

| Route | Nội dung |
| --- | --- |
| `/admin` | Tổng quan người dùng, doanh thu tháng/tổng, biểu đồ 30 ngày, khoản thu mới, người dùng mới và hàng chờ vận hành. |
| `/admin/revenue` | Doanh thu theo ngày, phân bổ nguồn, bảng chi tiết và phân trang. Lọc ngày, nguồn, tên gói/dự án/khách hàng. Bấm tên dự án để lọc chính xác dự án đó. |
| `/admin/users` | Danh sách tên/email/vai trò/trạng thái/ngày tham gia; tìm tên/email, lọc client/freelancer/admin và hoạt động/không hoạt động; phân trang. |
| `/admin/finance/payment-cases` | Hồ sơ sự cố thanh toán và quyết định có bằng chứng. |
| `/admin/finance/receipts` | Đối soát tiền vào. |
| `/admin/finance/checkout-review` | Gói và giao dịch cần kiểm tra. |
| `/admin/finance/withdrawals` | Chi trả freelancer. |
| `/admin/finance/banks` | Xác minh tài khoản ngân hàng. |
| `/admin/finance/tickets` | Ticket bảo trì. |
| `/admin/finance/audit` | Nhật ký vận hành. |

Danh sách người dùng hiện là chức năng tra cứu, không cho tự đổi vai trò hoặc mật khẩu từ bảng. API báo cáo chỉ trả trường cần hiển thị, không trả password hash, khóa hoặc dữ liệu ngân hàng. ADMIN được kiểm tra tại backend, không chỉ ẩn menu frontend. Đăng xuất xóa cache truy vấn; cache báo cáo gắn với tài khoản admin.

API đọc mới:

- `GET /api/v1/admin/dashboard`
- `GET /api/v1/admin/revenue?from=YYYY-MM-DD&to=YYYY-MM-DD&source=PROJECT&projectId=UUID&q=...&page=0`
- `GET /api/v1/admin/users?role=FREELANCER&status=ACTIVE&q=...&page=0`

`source`: bỏ trống, `PACKAGE`, `PROJECT`, `ADDON`, `OTHER`. Ngày mặc định 30 ngày gần nhất theo `Asia/Ho_Chi_Minh`, bao gồm ngày đầu/cuối. Khoảng cách hai mốc ngày tối đa 730 ngày; trang 20 dòng. Search tham số hóa, tìm ký tự `%`/`_` như ký tự thông thường. Báo cáo đọc trong snapshot nhất quán.

## Doanh thu được tính như thế nào?

Nguồn duy nhất là sổ cái tài chính LIVE: tổng credit trừ debit trên tài khoản `live:platform-revenue`, loại journal sandbox.

- **Gói dịch vụ:** số tiền đã hoàn tất kích hoạt và được ghi sổ doanh thu.
- **Dự án:** phí nền tảng từ quyết toán dự án hoặc hoàn tất bảo trì, kèm tên dự án/khách hàng. Không phải toàn bộ giá trị hợp đồng.
- **Tiện ích:** phí tính năng bổ sung đã được áp dụng.
- **Khoản khác:** bút toán doanh thu chưa thuộc ba loại trên, gồm điều chỉnh nếu có. Bút toán giảm doanh thu được trừ, không ép về số dương.

Tiền ký quỹ, số phải trả freelancer, tiền chờ đối soát và tiền đã nhận nhưng chưa hoàn tất dịch vụ không phải doanh thu trong báo cáo này. Đây là số liệu vận hành từ sổ cái SAM, chưa phải báo cáo lợi nhuận sau chi phí/thuế. Danh sách có thời điểm ghi sổ, nguồn, tên gói/dự án, khách hàng và số tiền nền tảng thực ghi nhận.

## Triển khai và kiểm tra

1. Triển khai backend trước: Flyway chạy **V42** bổ sung index báo cáo. Giữ V40/V41 đã áp dụng; không sửa checksum hoặc xóa lịch sử tài chính.
2. Triển khai frontend cùng phiên bản có `/admin` mới và nút tạo lại link mới. Không cần thêm hoặc đổi biến môi trường cho thay đổi này.
3. Kiểm tra tài khoản ADMIN vào dashboard; CLIENT/FREELANCER nhận 403 khi gọi các API `/admin/*`.
4. Dùng sandbox kiểm tra hủy hợp lệ rồi tạo lại không cần admin; bấm hai lần vẫn một đơn hoạt động. Đơn UNKNOWN/đã có receipt không được mở thêm thanh toán.
5. Kiểm tra số liệu gói/dự án với sổ cái LIVE; sandbox và ký quỹ không được làm tăng doanh thu.
6. Chạy `./mvnw test` với `FINANCE_TEST_DATABASE_URL` trỏ database test riêng để chạy kiểm thử migration/SQL/đồng thời; CI backend hiện đã cấu hình PostgreSQL cho nhóm này. Chạy frontend `node --test tests/*.test.cjs`, TypeScript, Biome trên file đổi và build.

Giới hạn xác minh tại máy thực hiện: không truy cập được Docker/PostgreSQL; trình duyệt headless và máy chủ xem trước bị chặn bởi môi trường. Do đó chưa xác minh trực quan bằng ảnh chụp hay chạy giao dịch LIVE. Không chỉnh `.env`, không chạy migration trên production và không phát sinh lệnh chuyển tiền thật.

Tài liệu dùng bảng và các bước đánh số, không phụ thuộc Mermaid nên vẫn đọc được khi trình Markdown không hỗ trợ sơ đồ.
