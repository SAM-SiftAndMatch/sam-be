# Pay2S — thu tiền, kích hoạt dịch vụ và giải ngân SAM

Cập nhật: 07/11/2026. Backend, web và mobile chỉ tạo khoản thu mới qua Pay2S. Cổng thanh toán trước đã được gỡ khỏi runtime, endpoint, cấu hình và giao diện. Lịch sử cũ trong database vẫn được giữ nguyên.

## 1. Phạm vi

- Khách hàng nạp 100% hợp đồng, freelancer cọc 2%.
- BUSINESS cho khách hàng, PRO DEV cho freelancer.
- Nổi bật/Gợi ý freelancer khi tạo dự án hoặc mua sau: thanh toán ngay hoặc tiếp tục đơn chờ.
- Đơn bảo trì có phí.
- Web/mobile có trạng thái xác minh và lịch sử giao dịch; admin có tab đối soát checkout/gói.

Gói AI QA đang ngừng bán và tính năng bảo hành mẫu chưa có nghiệp vụ không được mở bán bởi lần chuyển cổng này. Các tính năng đã bao gồm trong BUSINESS được backend xác định, không tạo khoản thu thêm. Giá được chốt ở backend; phí gói/addon không cộng vào ngân sách trả freelancer.

## 2. Backend `.env`

Giữ các biến DB, Redis, JWT, Google OAuth và lưu trữ đang dùng. Bổ sung hoặc sửa:

```dotenv
PAYMENTS_PROVIDER=pay2s
PAYMENTS_ENVIRONMENT=SANDBOX
PAY2S_BASE_URL=https://payment.pay2s.vn
PAY2S_PARTNER_CODE=<PARTNER_CODE>
PAY2S_ACCESS_KEY=<ACCESS_KEY>
PAY2S_SECRET_KEY=<SECRET_KEY>
PAY2S_BANK_CODE=<BANK_ID_DA_LIEN_KET>
PAY2S_BANK_ACCOUNT=<SO_TAI_KHOAN_NHAN_TIEN_CUA_SAM>
PAY2S_IPN_URL=https://<BACKEND_PUBLIC>/api/v1/payments/pay2s-ipn
PAYMENTS_RETURN_URL=http://localhost:5173/payment/return
PAYMENTS_ALLOWED_RETURN_ORIGINS=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173
JWT_COOKIE_SECURE=false

FINANCE_BANK_ACTIVE_KEY=v1
FINANCE_BANK_KEYS=v1:<BASE64_KHOA_32_BYTE>
FINANCE_BANK_FINGERPRINT_KEY=<BASE64_KHOA_32_BYTE_DOC_LAP>
FINANCE_LIVE_PAYOUTS_ENABLED=false
FINANCE_DUAL_CONTROL=true
FINANCE_MINIMUM_WITHDRAWAL=50000
FINANCE_DAILY_WITHDRAWAL_LIMIT=100000000
```

Thay mọi placeholder, không giữ dấu `<...>`. Ngân hàng nhận tiền thuộc SAM; ngân hàng của freelancer khai báo riêng trong ứng dụng. Bộ ba khóa phải cùng tài khoản/cửa hàng Pay2S quản lý ngân hàng đó. Lấy khóa theo [hướng dẫn tích hợp](https://developer.pay2s.vn/docs/api/tich-hop-ky-thuat/), liên kết tài khoản theo [hướng dẫn ngân hàng](https://developer.pay2s.vn/docs/general/them-tai-khoan-ngan-hang/).

`PAY2S_BASE_URL` không có slash cuối hoặc đường dẫn create. Môi trường demo dùng khóa/ngân hàng demo; đổi nhãn thành LIVE không biến khóa demo thành khóa thật. Không dùng khóa webhook biến động số dư thay cho Secret Key Collection Link.

Backend đọc `.env` từ thư mục làm việc hoặc nhận biến runtime qua Compose/secret manager. `.env` không được commit. Không đặt khóa Pay2S hoặc khóa mã hóa trong `VITE_*`/`EXPO_PUBLIC_*`.

### Local và tunnel

Frontend local cổng 5173; backend cổng 8080. Mở một tunnel HTTPS vào backend và giữ terminal hoạt động:

```bash
docker run --rm --network host cloudflare/cloudflared:latest tunnel --url http://127.0.0.1:8080
```

Đặt hostname thực nhận được vào `PAY2S_IPN_URL`. Trong SANDBOX, backend dùng HTTPS relay `/api/v1/payments/pay2s-return` trên origin IPN cho browser callback, rồi chuyển về `PAYMENTS_RETURN_URL`. Relay chỉ chuyển tiếp `orderId` có dạng 32 ký tự hex; query không xác nhận tiền và không chọn đích redirect.

Tunnel đổi hostname thì cập nhật env và tạo checkout mới. IPN dùng URL đã lưu lúc tạo đơn. Không dùng quick tunnel cho production.

## 3. Production

Dùng tài khoản, khóa và ngân hàng thật đã được Pay2S kích hoạt. Với domain của SAM:

```dotenv
PAYMENTS_PROVIDER=pay2s
PAYMENTS_ENVIRONMENT=LIVE
PAY2S_BASE_URL=https://payment.pay2s.vn
PAY2S_PARTNER_CODE=<PARTNER_CODE_THAT>
PAY2S_ACCESS_KEY=<ACCESS_KEY_THAT>
PAY2S_SECRET_KEY=<SECRET_KEY_THAT>
PAY2S_BANK_CODE=<BANK_ID_THAT>
PAY2S_BANK_ACCOUNT=<SO_TAI_KHOAN_THAT>
PAY2S_IPN_URL=https://api.sam.pro.vn/api/v1/payments/pay2s-ipn
PAYMENTS_RETURN_URL=https://sam.pro.vn/payment/return
PAYMENTS_ALLOWED_RETURN_ORIGINS=https://sam.pro.vn
CORS_ALLOWED_ORIGINS=https://sam.pro.vn
JWT_COOKIE_SECURE=true
FINANCE_LIVE_PAYOUTS_ENABLED=false
FINANCE_DUAL_CONTROL=true
```

Nếu thực sự phục vụ cả `www.sam.pro.vn`, thêm đúng origin HTTPS đó vào CORS và return allowlist. Web phải build với `VITE_API_URL=https://api.sam.pro.vn`; mobile dùng `EXPO_PUBLIC_API_URL=https://api.sam.pro.vn` và `EXPO_PUBLIC_AUTH_ORIGIN=https://sam.pro.vn`.

Không dùng database/khóa mã hóa production chung với demo. Backend từ chối cấp chéo quyền lợi giữa LIVE và SANDBOX.

## 4. Migration và chuyển phiên bản

Migration mới là `V39__Unified_Pay2s_Checkouts.sql`, chạy sau V37/V38. Không sửa checksum migration đã áp dụng.

1. Sao lưu database và bộ khóa mã hóa hiện tại.
2. Ngừng tạo đơn ở bản cũ; hoàn tất/hủy và đối soát link cũ còn chờ tại nhà cung cấp. Bản mới không nhận callback cổng cũ.
3. Triển khai backend có V39 và web/mobile cùng phiên bản API. Xóa các biến khóa cổng cũ khỏi môi trường; backend không còn yêu cầu chúng.
4. Đổi URL quay về thành `/payment/return` như trên. Không giữ đường dẫn quay về cũ khi triển khai frontend mới.
5. Kiểm tra migration, IPN, từng mục đích thu tiền và giao diện lịch sử trước khi mở cho người dùng.

Các subscription cũ được gắn `environment=LEGACY`: giữ lịch sử/status gốc nhưng không tự coi tiền chưa rõ nguồn là tiền thật hoặc cấp quyền lợi LIVE. Nếu cần giữ quyền lợi cũ đã được trả thật, phải đối soát nguồn và chuyển dữ liệu có kiểm soát trước khi mở lại dịch vụ; không cập nhật hàng loạt thành LIVE.

`compose-prod.yaml` dùng image, cần build/push image chứa code mới trước. Trên server, tại thư mục chứa file Compose và `.env`:

```bash
docker compose -f compose-prod.yaml pull backend
docker compose -f compose-prod.yaml up -d --force-recreate backend
```

Local Compose build từ source:

```bash
docker compose up -d --build --force-recreate backend
```

Không chỉ restart container khi thay env. Giữ `HIBERNATE_DDL_AUTO=validate`.

## 5. API và tự cập nhật

| API | Mục đích |
| --- | --- |
| `GET /api/v1/packages` | Gói đang bán cho đúng vai trò, giá backend |
| `POST /api/v1/subscriptions/purchase` | Tạo/tiếp tục đơn gói; response chứa `checkout` |
| `POST /api/v1/jobs/{job}/addons/checkout` | Tạo/tiếp tục checkout addon |
| `POST /api/v1/payments/fund` | Khách hàng nạp hợp đồng |
| `POST /api/v1/payments/deposit` | Freelancer cọc |
| `POST /api/v1/paid-maintenance-orders/{id}/checkout` | Thu tiền bảo trì |
| `GET /api/v1/checkouts/{id}` | Chủ đơn đọc trạng thái xác minh |
| `GET /api/v1/checkouts/me?page=0` | Lịch sử chủ đơn, 20 bản ghi/trang |
| `GET /api/v1/checkouts/admin/review?page=0` | Admin xem khoản cần đối soát/kích hoạt lỗi |
| `POST /api/v1/payments/pay2s-ipn` | Nhận IPN có chữ ký |

Checkout trả `orderId` UUID, `purpose`, `objectId`, `amount`, `currency`, `provider`, `environment`, `paymentStatus`, `fulfillmentStatus`, `checkoutUrl`, `transferContent`, `expiresAt`. Response nạp hợp đồng/cọc/bảo trì có `orderId` là tham chiếu checkout, độc lập với `paymentId`. Không dùng `paymentId` để suy ra mã checkout mới.

IPN kiểm tra HMAC/merchant/mã đơn/số tiền/môi trường, lưu receipt bất biến và trạng thái PAID, rồi ACK sau commit. Worker durable đọc hàng chờ bằng khóa database, tự thử lại với backoff khi kích hoạt lỗi. Trạng thái PAID/PENDING hoặc PAID/RETRY nghĩa là đã nhận tiền, đang kích hoạt; không yêu cầu người dùng trả lại.

Gói bắt đầu thời hạn 30 ngày khi kích hoạt, callback lặp không kéo dài hạn. Job nền cập nhật EXPIRED và cache khi hết hạn. Addon bật đúng lựa chọn đã mua và giải quyết thông báo nhắc. Gợi ý freelancer được xử lý bởi hàng chờ bền vững trên scheduler riêng, có retry; không chặn IPN hoặc các giao dịch khác. Đủ tiền hợp đồng và cọc mới khởi động dự án. Bảo trì mới có nguồn tiền riêng.

Tiền thừa/sai/đến muộn/đơn bị hủy/dự án đóng được lưu để REVIEW. Không tự cấp quyền lợi hoặc hoàn tiền nhà cung cấp. Lỗi create chưa rõ được giữ UNKNOWN, không tự tạo thêm link để tránh trả lặp; admin đối soát giao dịch tại provider/ngân hàng trước khi xử lý. Chưa có nút ghi đè PAID hoặc tự phân bổ lại các khoản REVIEW.

Thông báo/cập nhật cache được dùng cùng với đọc lại trạng thái khi quay về. Trang kết quả web/mobile polling có giới hạn, có nút kiểm tra lại. Khi mất IPN, không suy diễn đã thanh toán từ URL quay về.

## 6. Mô phỏng demo không cần Postman

Từ thư mục `sam-be`:

```bash
python3 scripts/pay2s_simulate_payment.py
```

Nhập **orderId của checkout**, lấy từ response:

- Mua gói: `result.checkout.orderId`.
- Addon: `result.orderId`.
- Hợp đồng/cọc/bảo trì: `result.orderId`.
- Hoặc xem lịch sử `GET /api/v1/checkouts/me` / trang `/payments/history` trên web.

Script đọc Secret Key từ `.env` hoặc biến runtime, chỉ cho phép SANDBOX và gọi [API mô phỏng chính thức](https://developer.pay2s.vn/docs/api/simulate-payment/). Không cần chuyển tiền thật. Chạy riêng cho từng checkout, kể cả khoản nạp hợp đồng và cọc.

Nếu đã sửa kết nối callback và cần gửi lại IPN:

```bash
python3 scripts/pay2s_simulate_payment.py --resend-ipn
```

Nhập lại cùng orderId. Giữ tunnel hoạt động, đợi worker kích hoạt và xem PAID/APPLIED. Lặp mô phỏng không cấp thêm quyền lợi. HTTP 401: sai khóa; 403: không phải demo; 404: mã không thuộc phạm vi khóa; 409: điều kiện đơn không phù hợp.

## 7. Khóa mã hóa và giải ngân

Chỉ với database mới chưa có khóa, chạy hai lần `openssl rand -base64 32`, dùng hai kết quả độc lập cho `FINANCE_BANK_KEYS` và `FINANCE_BANK_FINGERPRINT_KEY`. Dữ liệu ngân hàng mã hóa AES-256-GCM có AAD theo chủ sở hữu/bản ghi. Không thay đè khóa của database đang có dữ liệu; phải giữ key ID cũ khi xoay khóa. Mất khóa sẽ không đọc được dữ liệu cũ. Chi tiết API ở [FREELANCER_INCOME_BANK_API.md](FREELANCER_INCOME_BANK_API.md).

Thu tiền tự động không đồng nghĩa chuyển tiền freelancer tự động. Sau nghiệm thu, giữ ít nhất 168 giờ; ticket hợp lệ còn mở tiếp tục chặn rút. Chỉ bật `FINANCE_LIVE_PAYOUTS_ENABLED=true` sau khi đã đối soát nguồn tiền, xác minh tài khoản và kiểm tra quy trình hai admin. Chuyển ngân hàng vẫn thủ công có chứng từ, kiểm soát kép và xác thực lại mật khẩu; không có API Pay2S chi hộ trong bản triển khai.

## 8. Kiểm thử và chẩn đoán

Dùng mã `reference` trong response/log `PAY2S_CHECKOUT_FAILED`. Log chỉ có mã lỗi, HTTP status, môi trường và origin callback; không gửi Secret Key hoặc URL checkout có token để hỗ trợ chẩn đoán. IPN URL và redirect phải đi được qua HTTPS, không bị trang đăng nhập/challenge của proxy chặn.

Backend có kiểm thử chữ ký, callback lặp/tiền sai/đến muộn/môi trường, snapshot giá, kích hoạt và retry. PostgreSQL tests chạy trong schema tạm khi có `FINANCE_TEST_DATABASE_URL`, `FINANCE_TEST_DATABASE_USER`, `FINANCE_TEST_DATABASE_PASSWORD`; chỉ trỏ tới DB test. CI bật bộ kiểm thử này trên PostgreSQL service riêng. Môi trường local của agent chưa xác minh migration trên PostgreSQL hoặc giao dịch Pay2S live.

Trước khi production, thử từng loại thanh toán trên staging, đóng trình duyệt trước callback, quay về bằng cả vai trò, kiểm tra chế độ app background/mở lại, callback lặp và điều kiện 7 ngày. Kiểm tra nhận tiền thực tế ở ngân hàng khi thử LIVE; không chỉ nhìn màn hình thành công.
