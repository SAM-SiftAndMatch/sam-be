# Chẩn đoán ngân hàng nhận tiền Pay2S

`PAYMENTS_ENVIRONMENT=LIVE`, `PAY2S_BANK_CODE=MBB` và hostname
`payment.pay2s.vn` cho biết cấu hình của SAM; chúng không tự chứng minh tài khoản
nhận tiền mà Pay2S đã gắn vào một đơn trước đó.

## Log cần xem

Sau khi triển khai image chứa phần chẩn đoán này, chạy trên EC2:

```sh
sudo docker logs --since 30m sam-backend 2>&1 | grep -E 'PAY2S_CHECKOUT_(REQUEST|RESPONSE|REUSED|FAILED)'
```

- `REQUEST`: cấu hình thực tế dùng để gửi yêu cầu; `bank=MBB`,
  `accountLast4=****2318` là ngân hàng và bốn số cuối được gửi đi.
- `RESPONSE`: `providerEnvironment`, `providerIsDemo`, `checkoutHost`,
  danh sách ngân hàng/STK đã che trong `receivers` và so sánh `receiverMatch`.
  Ghép REQUEST/RESPONSE bằng `reference` hoặc `orderId`.
- `REUSED`: SAM trả lại đơn có sẵn; thao tác đó không gọi create của Pay2S.
  `orderId` ở đây có dấu gạch ngang, tương ứng mã create sau khi bỏ dấu gạch ngang.
- `FAILED`: lỗi đã phân loại; không in nguyên phản hồi nhà cung cấp.

`EXACT_MATCH` nghĩa là tất cả mục `qrList` khớp chính xác bank_id và account_number
được gửi. `DIFFERENT` nghĩa là có mục khác. `UNVERIFIED` nghĩa là thiếu dữ liệu để
so sánh. Đây là thông tin chẩn đoán, không phải xác nhận giao dịch hay chính sách
chặn tài khoản ảo: ánh xạ tài khoản ảo phải được xác minh riêng với Pay2S.

## Đọc kết quả

1. REQUEST gửi ACB: kiểm tra đúng backend, image và cấu hình Spring đang chạy.
2. REQUEST gửi MBB nhưng RESPONSE trả ACB: có bằng chứng tại ranh giới API;
   đối chiếu đơn và tài khoản merchant với Pay2S. Không suy ra lý do nhà cung cấp
   đổi tài khoản nếu chưa có xác nhận.
3. RESPONSE trả MBB nhưng trang đang xem là ACB: đối chiếu mã đơn và thời gian;
   kiểm tra đang mở đúng liên kết. Nếu đúng đơn, cung cấp mã đơn cho Pay2S kiểm tra.
4. Chỉ thấy REUSED: đang dùng lại đơn cũ. Đổi env không tạo lại liên kết của đơn đó.
5. Thiếu qrList hoặc cờ môi trường: ghi nhận là chưa có bằng chứng, không mặc định
   coi tài khoản đã được xác minh LIVE.

Backend vẫn từ chối phản hồi khai báo demo khi SAM đang LIVE. Log không chứa
access key, secret key, partner code, URL thanh toán có token, QR, tên chủ tài khoản
hay toàn bộ STK. Không bật log HTTP body để chẩn đoán.

Không tạo thêm đơn LIVE chỉ để lấy log, không chuyển tiền vào tài khoản chưa xác
minh, không xóa đơn cũ hay sửa trạng thái PAID/EXPIRED bằng SQL. Với đơn hết hạn,
dùng quy trình đối soát hiện có và xác nhận đơn phía nhà cung cấp đã đóng trước
khi cho phép thay thế. Log mới không khôi phục được phản hồi của những lần tạo
đơn trước khi triển khai; không cần thêm biến môi trường hoặc migration.

Tham khảo cấu trúc `bankAccounts`, `qrList`, `isDemo`, `environment`:
[Pay2S Collection Link](https://developer.pay2s.vn/docs/api/collection-link/).
