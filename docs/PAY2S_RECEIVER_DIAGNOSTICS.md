# Chẩn đoán ngân hàng nhận tiền Pay2S

`PAYMENTS_ENVIRONMENT=LIVE`, `PAY2S_BANK_CODE=MBB` và hostname
`payment.pay2s.vn` cho biết cấu hình của SAM; chúng không tự chứng minh tài khoản
nhận tiền mà Pay2S đã gắn vào một đơn trước đó.

## Log cần xem

Sau khi triển khai image chứa phần chẩn đoán này, chạy trên EC2:

```sh
sudo docker logs --since 30m sam-backend 2>&1 | grep -E 'PAY2S_CHECKOUT_(REQUEST|RESPONSE|REUSED|FAILED|CANCELLED)'
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

## Khôi phục liên kết hết hạn

Web và mobile có nút **Khôi phục thanh toán** trên trang kết quả của đơn EXPIRED.
Chỉ dùng khi chưa chuyển tiền. Sau khi khôi phục thành công, quay lại dịch vụ rồi
bấm thanh toán; SAM kiểm tra lại điều kiện mua và tạo liên kết mới.

API xác thực chủ đơn: `POST /api/v1/checkouts/{id}/prepare-retry`.
Luồng xử lý:

```text
Kiểm tra chủ đơn, môi trường, merchant và thời hạn
  -> kiểm tra chưa có receipt/IPN thành công và không có worker đang chạy
  -> thả transaction, gọi API cancel của Pay2S với đúng mã đơn cũ
  -> chỉ chấp nhận HTTP 200 + resultCode 0 + status cancelled
     và phản hồi khớp orderId, requestId, partnerCode
  -> khóa đơn và kiểm tra lại receipt/IPN
  -> đánh dấu CANCELLED, cho phép thay thế, ghi lịch sử đối soát
  -> khách quay lại dịch vụ để thanh toán qua API nghiệp vụ hiện có
```

Timeout, HTTP 404/409, dữ liệu trả về không khớp, khác merchant hoặc có thông tin
nhận tiền đều không cho phép thay thế. Gọi lại cancel sau mất phản hồi là an toàn
theo contract idempotent của Pay2S; không gọi lại create khi kết quả chưa rõ.
Yêu cầu trùng dùng cùng khóa mua chỉ chuyển sang checkout mới sau khi checkout
cũ đã được xác nhận đóng và cho phép thay thế. Giá đã chốt được giữ nguyên.

Hủy trên Pay2S không ngăn chuyển khoản ngân hàng. Tiền về mã đơn cũ sau đó vẫn
vào đối soát, không tự kích hoạt lần nữa. Các đơn cũ khác phạm vi khóa hoặc đã
nhận tiền tiếp tục dùng quy trình hỗ trợ/admin hiện có.

Luồng hợp đồng chọn khoản thanh toán theo môi trường đang chạy. Khoản SANDBOX
không đổi nhãn thành LIVE, không được dùng để tính đã góp đủ tiền LIVE và vẫn
giữ nguyên trong lịch sử. Không cần sửa env hoặc migration cho bản sửa này.

Tham khảo [API hủy đơn Pay2S](https://developer.pay2s.vn/docs/api/cancel-order/).
