# Tệp trong hội thoại và kiểm tra phạm vi AI

## Chat trên web

Client và freelancer có thể chọn ảnh/file hoặc dán ảnh từ clipboard, xem trước phần chuẩn bị gửi, bỏ từng tệp và gửi kèm văn bản hoặc chỉ gửi tệp. Tin chỉ được xóa khỏi ô soạn sau khi REST API xác nhận lưu thành công. WebSocket tiếp tục chuyển tin mới đến người nhận; phản hồi REST và WebSocket dùng cùng ID để không nhân đôi tin nhắn.

Giới hạn áp dụng ở cả giao diện và backend:

- Tối đa 5 tệp mỗi tin; tổng tối đa 25MB.
- Ảnh JPG, JPEG, PNG, GIF, WebP: tối đa 5MB mỗi ảnh.
- PDF, DOC, DOCX, XLS, XLSX, TXT, CSV, ZIP: tối đa 10MB mỗi file.
- Nội dung văn bản: tối đa 8.000 ký tự.
- Tải tệp: tối đa 30 lượt/phút/tài khoản; gửi tin: 30 lượt/phút/tài khoản.

Backend kiểm tra định dạng thực tế ngoài phần mở rộng. SVG, HTML và file thực thi không được nhận. JPG/PNG/GIF được kiểm tra metadata và giới hạn 25 megapixel. Giới hạn dung lượng dùng đơn vị 1024 × 1024 byte.

### API

- `POST /api/v1/chat/rooms/{roomId}/attachments`: multipart, một trường `file`. Trả `{id, fileName, contentType, sizeBytes}`.
- `POST /api/v1/chat/rooms/{roomId}/messages`: nhận `content`, `attachmentIds` và `clientMessageId` (UUID). WebSocket cũng nhận các trường này. `content` có thể trống khi có tệp.
- `GET /api/v1/chat/rooms/{roomId}/attachments/{attachmentId}/content`: lấy nội dung sau khi kiểm tra thành viên của phòng. `download=true` buộc tải về.

File được lưu bằng dịch vụ Cloudinary hiện có, trong thư mục `chat/{roomId}`. URL Cloudinary không trả về client; web lấy blob bằng API có xác thực. Ảnh hiện trong tin nhắn và có chế độ mở lớn; PDF có xem trước; các loại tài liệu khác tải về để mở bằng ứng dụng phù hợp.

Chỉ chủ tải lên mới được gửi tệp, vào đúng phòng của tệp. Tệp đã gắn tin không được dùng lại để gửi tin khác. Tệp chưa gửi quá 24 giờ không được nhận gửi nữa; không có tác vụ tự xóa file Cloudinary trong thay đổi này. Người nhận chỉ được đọc sau khi tệp đã gửi. Retry cùng mã tin trả tin đã lưu; dùng lại mã cho nội dung khác bị từ chối.

Migration V44 tạo `chat_attachments` và chỉ mục mã gửi tin. Spring multipart cho phép file 10MB, request 11MB để đủ phần bao multipart. Reverse proxy bên ngoài cũng cần cho phép request ít nhất 11MB. Bản mobile tiếp tục gửi tin chữ qua REST/WebSocket; để hiển thị và gửi tệp, mobile cần dùng các trường/API mới.

## Nội dung đã chia sẻ trong workspace

Hai nút ở góc phải chuyển giữa tiến độ thu nhỏ và thư viện ảnh, tệp, liên kết. Màn hình nhỏ mở ngăn chi tiết bên phải. Thư viện dùng lịch sử trên server, độc lập với số tin nhắn đã tải trong chat, có nút Xem thêm.

- `GET /api/v1/chat/rooms/{roomId}/attachments?type=images|files&page=0&size=18`: chỉ trả tệp đã gửi trong đúng phòng; không gồm tệp mới tải lên nhưng chưa gửi.
- `GET /api/v1/chat/rooms/{roomId}/shared-links?page=0&size=18`: phân trang theo tin nhắn chứa liên kết HTTP/HTTPS hoặc www; mỗi tin có danh sách liên kết riêng, tên người gửi và ngày gửi. Bỏ qua tin hệ thống, liên kết lỗi hoặc chứa thông tin đăng nhập.

Cả hai API yêu cầu thành viên phòng, cho phép đọc lại phòng cũ. Phân trang giới hạn tối đa 100 mục; thứ tự mới nhất trước, dùng ID để ổn định khi trùng thời gian. Ảnh/tệp vẫn tải nội dung qua API xác thực hiện có. Migration V45 bổ sung chỉ mục cho tệp đã gắn tin theo phòng; cần triển khai backend cùng frontend.

## Phạm vi AI

Luồng AI Brief và các endpoint BA/Risk kiểm tra phạm vi trước khi gọi tác vụ sinh SRS/đánh giá đầy đủ:

1. Yêu cầu rỗng/quá 10.000 ký tự và một số mẫu đổi vai/bỏ quy tắc rõ ràng được chặn trước.
2. Yêu cầu còn lại qua bộ phân loại chỉ xuất JSON boolean, giới hạn 64 output token, một lần gọi, không retry tự động. Ngữ cảnh gửi vào bộ phân loại được giới hạn độ dài.
3. Chỉ yêu cầu phù hợp mới chạy bước sinh nội dung chính. Nếu chưa kiểm tra được phạm vi, backend trả lỗi để thử lại thay vì bỏ qua kiểm tra.

Bản nháp dùng mô tả/SRS từ database của chủ dự án. Phiên BA cũ được phân biệt theo tài khoản; Risk cũ lấy SRS/lịch sử đã lưu của cùng tài khoản và phiên, không sử dụng SRS/lịch sử do HTTP client tự đưa vào làm nguồn ngữ cảnh.

Câu hỏi không phù hợp nhận `status: OUT_OF_SCOPE` cùng thông báo yêu cầu quay lại phạm vi dự án. Câu từ chối hiển thị trong chat nhưng không xóa SRS, URL, ngân sách, thời hạn, tiến độ hoặc đánh giá rủi ro đã có; không đưa câu hỏi bị từ chối vào lịch sử gửi model ở lượt sau. Câu trả lời ngắn, xác nhận và thay đổi giá/thời hạn vẫn được đánh giá theo câu hỏi đang hỏi.

Giới hạn 10 lượt AI/phút/tài khoản và cắt lịch sử dài giúp kiểm soát chi phí. Bộ phân loại vẫn tốn một lượt AI nhỏ cho yêu cầu chưa chặn bằng quy tắc; không có cam kết rằng nhận diện ngữ nghĩa hoặc prompt injection đạt 100%.

Model đang cấu hình là Gemini 3.5 Flash-Lite. [Tài liệu Google](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/3-5-flash-lite) mô tả chế độ thinking mặc định tối thiểu cho các tác vụ phân loại. Khi thay model, cần kiểm tra lại khả năng trả JSON với ngân sách nhỏ này.
