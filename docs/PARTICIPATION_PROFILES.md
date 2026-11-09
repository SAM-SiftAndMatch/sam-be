# Hồ sơ tham gia dự án và thông tin hợp đồng

Phiên bản điều khoản hiện tại: `2026-11-09`.

## Đăng ký và hồ sơ

`POST /api/v1/auth/register` yêu cầu `acceptedTerms: true`. Luồng hoàn tất Google đã kiểm tra trường này và nay lưu thêm thời điểm, phiên bản chấp nhận trên tài khoản.

Hai API `PUT /api/v1/profiles/client/me` và `PUT /api/v1/profiles/freelancer/me` yêu cầu bổ sung:

```json
{
  "contractName": "Nguyễn Văn An",
  "contactEmail": "an@example.com",
  "phoneNumber": "0901234567",
  "acceptedTerms": true
}
```

Các trường hồ sơ nghề nghiệp/công ty được gửi kèm như trước. Client cá nhân có thể để tên công ty trống. Tên và email dùng cho hợp đồng được lưu riêng, không đổi tên hoặc email đăng nhập.

GET hồ sơ trả thêm các trường liên hệ trên, `policyAcceptedAt` và `policyVersion`. Tài khoản cũ không được tự đánh dấu đã chấp nhận điều khoản. Trước khi đăng dự án, ứng tuyển, chấp nhận lời mời hoặc lập hợp đồng mới, tài khoản phải có đầy đủ thông tin liên hệ và đã chấp nhận phiên bản điều khoản hiện tại.

Web hướng người dùng đến hồ sơ khi thiếu thông tin, giữ đường dẫn, query và dữ liệu điều hướng để quay lại bước đang làm sau khi lưu. Các trang `/terms` và `/privacy` công khai nội dung được chấp nhận.

## Ngân sách

Dự án mới yêu cầu ngân sách tối thiểu `3000000` VNĐ; ngân sách tối đa không nhỏ hơn ngân sách tối thiểu. Cả hai giá trị phải là số nguyên. Backend kiểm tra cả DTO và service; bản nháp cũ dưới mức tối thiểu phải được cập nhật trước khi xuất bản. Prompt AI cũng nêu mức tối thiểu này và không tự nâng ngân sách nếu người dùng chưa xác nhận.

## Hợp đồng

Khi lập nháp, backend kiểm tra hồ sơ hai bên rồi lưu riêng họ tên, email, điện thoại, ngày lập theo múi giờ `Asia/Ho_Chi_Minh`, phiên bản điều khoản và nội dung quy định nền tảng. `GET /api/v1/chat/rooms/{roomId}/contract` trả các trường này cho hai bên của hợp đồng.

Nội dung quy định nền tảng được lưu trong `platformTerms`, độc lập với phần điều khoản do hai bên sửa. Sửa hồ sơ về sau không thay đổi thông tin đã lưu trên hợp đồng. Nội dung AI chỉ soạn phần thỏa thuận chi tiết; không tạo các trường liên hệ hoặc ngày tháng còn để trống.

Migration V43 thêm các cột mới. Hợp đồng cũ giữ nội dung thỏa thuận; chỉ sao chép tên hiện đang được hệ thống hiển thị và ngày lập đã có. Không suy đoán số điện thoại, email liên hệ hay lần chấp nhận điều khoản của hợp đồng cũ.

## Áp dụng

Triển khai backend cùng migration V43 và frontend tương ứng. Flyway chạy migration khi backend khởi động; không cần sửa tay dữ liệu hay reset database. Các ứng dụng khác gọi API đăng ký/hồ sơ cần gửi các trường bắt buộc mới và thu thập xác nhận của người dùng.
