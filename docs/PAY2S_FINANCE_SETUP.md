# Pay2S, công nợ và giải ngân SAM — hướng dẫn cấu hình

Ngày cập nhật: 06/11/2026. Phạm vi mã: `sam-be`, `sam-fe`, `sam-mobile`.

## 1. Những gì bản triển khai thực hiện

- Nhận tiền hợp đồng và đơn bảo trì qua Pay2S Collection Link V1; backend xác minh IPN trước khi ghi nhận tiền.
- Nghiệm thu tự động ghi công nợ, giữ đủ 168 giờ theo UTC. Dự án vẫn `COMPLETED`; khách hàng mở ticket trong chat/workspace trong thời gian này.
- Ticket miễn phí hợp lệ còn mở tiếp tục giữ tiền, kể cả đã qua ngày thứ 7. Quá hạn phản hồi/xử lý được chuyển admin; không tự phạt hoặc tự chuyển ticket cũ sang thu phí.
- Sau cửa sổ bảo trì, yêu cầu mới dùng báo giá và đơn bảo trì riêng. Nghiệm thu đơn mới tạo công nợ và cửa sổ bảo trì riêng.
- Freelancer khai báo tài khoản ngân hàng, admin xác minh; yêu cầu rút chỉ dùng công nợ thật đã đủ điều kiện.
- Chi ngân hàng là **thủ công có kiểm soát**: duyệt, nhận xử lý, chuyển tại ngân hàng, nhập mã giao dịch và tham chiếu chứng từ, xác nhận kết quả. Không có lời gọi API Pay2S để chuyển tiền cho freelancer.

Tài liệu Pay2S được đối chiếu có API [tạo link thu tiền](https://developer.pay2s.vn/docs/api/collection-link/) và [IPN của link thanh toán](https://developer.pay2s.vn/docs/api/instant-payment-notification/). API [lịch sử giao dịch IN/OUT](https://developer.pay2s.vn/docs/history-api/tai-lieu-ky-thuat/) không phải API khởi tạo lệnh chi. Khi có hợp đồng dịch vụ chi hộ cùng tài liệu chính thức, cần adapter và kiểm thử riêng trước khi tự động chuyển tiền.

## 2. Lấy thông tin từ Pay2S

Trong tài khoản Pay2S, vào phần tích hợp Website/Application để lấy `Partner Code`, `Access Key`, `Secret Key` theo [hướng dẫn tích hợp](https://developer.pay2s.vn/docs/api/tich-hop-ky-thuat/). Liên kết ngân hàng nhận tiền của **SAM** và dùng đúng mã ngân hàng/số tài khoản đã đăng ký. Không đưa tài khoản freelancer vào cấu hình này.

Xác nhận với Pay2S tài khoản/khóa được cấp dùng cho sandbox hay production. Chỉ đổi chữ `SANDBOX` thành `LIVE` không biến tài khoản demo thành tài khoản nhận tiền thật.

Đây là tích hợp Collection Link V1. Không dùng Bearer token của webhook biến động số dư để thay `PAY2S_SECRET_KEY`. Endpoint của SAM xác minh trường `m2signature` của IPN; nó không nhận mọi định dạng webhook ngân hàng.

## 3. Backend `.env` cho sandbox

Bổ sung vào cấu hình hiện có của `sam-be`, thay toàn bộ giá trị trong dấu `<...>`:

```dotenv
PAYMENTS_PROVIDER=pay2s
PAYMENTS_ENVIRONMENT=SANDBOX
PAY2S_BASE_URL=https://payment.pay2s.vn
PAY2S_PARTNER_CODE=<PARTNER_CODE_SANDBOX>
PAY2S_ACCESS_KEY=<ACCESS_KEY_SANDBOX>
PAY2S_SECRET_KEY=<SECRET_KEY_SANDBOX>
PAY2S_BANK_CODE=ACB
PAY2S_BANK_ACCOUNT=<TAI_KHOAN_NHAN_TIEN_CUA_SAM>
PAY2S_IPN_URL=https://<BACKEND_STAGING>/api/v1/payments/pay2s-ipn

PAYMENTS_RETURN_URL=https://<WEB_STAGING>/client/payment/vnpay-return
PAYMENTS_ALLOWED_RETURN_ORIGINS=https://<WEB_STAGING>

FINANCE_BANK_ACTIVE_KEY=v1
FINANCE_BANK_KEYS=v1:<BASE64_KHOA_MA_HOA_32_BYTE>
FINANCE_BANK_FINGERPRINT_KEY=<BASE64_KHOA_HMAC_32_BYTE_KHAC>
FINANCE_LIVE_PAYOUTS_ENABLED=false
FINANCE_DUAL_CONTROL=true
FINANCE_MINIMUM_WITHDRAWAL=50000
FINANCE_DAILY_WITHDRAWAL_LIMIT=100000000
```

`ACB` là ví dụ, phải thay theo ngân hàng đã liên kết. `PAY2S_BASE_URL` không có dấu `/` ở cuối. Mặc định gọi host API create chính thức `payment.pay2s.vn`, kể cả khi dùng bộ khóa demo; môi trường tiền vẫn là SANDBOX. Host của trang checkout được provider trả về có thể là `sandbox-payment.pay2s.vn`. Endpoint tạo link được backend nối thêm `/v1/gateway/api/create`.

Đường dẫn web vẫn mang tên `vnpay-return` để tương thích route hiện có; màn hình đã xử lý trạng thái thanh toán chung. Không thêm query/fragment vào `PAYMENTS_RETURN_URL`. Danh sách origin phân tách bằng dấu phẩy, gồm scheme và host, thêm port nếu dùng cổng riêng. Backend chỉ cho phép đường dẫn return đã định nghĩa, không chuyển hướng tùy ý theo dữ liệu người dùng.

Local web có thể dùng `http://localhost:5173/client/payment/vnpay-return` trong SANDBOX. IPN phải là địa chỉ backend Pay2S gọi được từ Internet; `localhost` trên máy phát triển không đáp ứng điều kiện này.

Các cấu hình DB, Redis, JWT, CORS của ứng dụng vẫn cần đầy đủ. `CORS_ALLOWED_ORIGINS` phải chứa đúng origin web/mobile; đây là cấu hình khác với allowlist return URL. Không dùng wildcard cho phiên có cookie.

Repository có `spring.config.import=optional:file:.env[.properties]`: backend đọc `.env` theo thư mục làm việc của tiến trình. Chạy JAR/IDE từ thư mục `sam-be` để dùng file này, hoặc cấp biến môi trường qua runtime/secret manager. Compose hiện có cũng đọc `env_file: .env`. Không đặt file ở thư mục khác rồi giả định backend tìm thấy; biến môi trường runtime được ưu tiên theo cơ chế cấu hình Spring.

Ứng dụng chủ động không khởi động nếu đã chọn `pay2s` nhưng thiếu khóa/ngân hàng/IPN hoặc cấu hình origin không hợp lệ. Muốn tiếp tục chạy cổng cũ trong lúc xin khóa, đặt `PAYMENTS_PROVIDER=vnpay` và giữ cấu hình VNPay hợp lệ.

## 4. Tạo và quản lý khóa mã hóa ngân hàng

Tạo hai giá trị độc lập bằng hai lần chạy lệnh này trong terminal quản trị riêng:

```bash
openssl rand -base64 32
```

Dùng giá trị thứ nhất cho `FINANCE_BANK_KEYS=v1:...`; giá trị thứ hai cho `FINANCE_BANK_FINGERPRINT_KEY`. Không dùng lại JWT signing key, mật khẩu DB hoặc Pay2S secret. Không gửi các giá trị này vào chat, commit Git hay đặt trong biến `VITE_*`/`EXPO_PUBLIC_*`.

Số tài khoản và tên thụ hưởng được mã hóa AES-256-GCM, nonce ngẫu nhiên 12 byte, authentication tag 128 bit. AAD ràng buộc bản mã với chủ sở hữu và ID bản ghi để phát hiện đổi bản mã giữa các tài khoản. Dữ liệu trên yêu cầu chi là snapshot mã hóa riêng; thay tài khoản ngân hàng sau này không đổi người nhận của yêu cầu cũ.

Mã ngân hàng, 4 số cuối, trạng thái và fingerprint HMAC được lưu để hiển thị/đối chiếu, không phải toàn bộ bản ghi được che khỏi người có quyền DB. API danh sách chỉ trả bản che. Admin xem đầy đủ phải xác thực lại mật khẩu, đúng quyền và được ghi audit; web tự ẩn hộp thoại sau 60 giây hoặc khi rời tab.

Khóa phải ở secret manager hoặc cấu hình runtime có quyền đọc hạn chế, tách khỏi bản sao DB. Sao lưu khóa có kiểm soát cùng quy trình khôi phục: mất khóa là không giải mã được dữ liệu cũ. Mã hóa ứng dụng không bảo vệ được khi cả runtime và khóa đều bị chiếm quyền. Xác thực lại mật khẩu hiện tại chưa phải MFA; bản này chưa bổ sung MFA cho quản trị viên.

### Xoay khóa

```dotenv
FINANCE_BANK_ACTIVE_KEY=v2
FINANCE_BANK_KEYS=v1:<KHOA_CU>,v2:<KHOA_MOI>
FINANCE_BANK_FINGERPRINT_KEY=<GIU_KHOA_FINGERPRINT_HIEN_TAI>
```

Bản ghi mới dùng `v2`; dữ liệu cũ vẫn giải mã bằng `v1`. Không xóa khóa cũ khi còn bản ghi hoặc backup cần đọc. Bản này chưa có job tái mã hóa toàn bộ dữ liệu. Đổi khóa fingerprint cần migration tính lại có kiểm soát, không chỉ sửa env vì sẽ phá nhận diện trùng tài khoản.

## 5. Production

Sau khi xác nhận tài khoản Pay2S thật, thay các giá trị sandbox bằng bộ production đồng nhất:

```dotenv
PAYMENTS_ENVIRONMENT=LIVE
PAY2S_BASE_URL=https://payment.pay2s.vn
PAY2S_PARTNER_CODE=<PARTNER_CODE_PRODUCTION>
PAY2S_ACCESS_KEY=<ACCESS_KEY_PRODUCTION>
PAY2S_SECRET_KEY=<SECRET_KEY_PRODUCTION>
PAY2S_BANK_CODE=<MA_NGAN_HANG_DA_LIEN_KET>
PAY2S_BANK_ACCOUNT=<TAI_KHOAN_NHAN_TIEN_CUA_SAM>
PAY2S_IPN_URL=https://api.sam.pro.vn/api/v1/payments/pay2s-ipn
PAYMENTS_RETURN_URL=https://sam.pro.vn/client/payment/vnpay-return
PAYMENTS_ALLOWED_RETURN_ORIGINS=https://sam.pro.vn
FINANCE_LIVE_PAYOUTS_ENABLED=false
FINANCE_DUAL_CONTROL=true
```

Tách database và khóa mã hóa production khỏi staging; không chuyển database chứa hợp đồng sandbox sang LIVE chỉ bằng đổi env. Hợp đồng có nguồn thu thật/sandbox trộn sẽ bị chặn quyết toán và cần đối soát riêng. Bật HTTPS, `JWT_COOKIE_SECURE=true`, cấu hình CORS chính xác, tắt SQL/request-body logging ở hạ tầng có thể ghi dữ liệu tài chính. Không bật trace nội dung HTTP tới Pay2S trong production.

Backend chỉ cho phép origin Pay2S chính thức; kiểm tra phản hồi demo trước khi nhận link LIVE. IPN được xác minh HMAC, đối chiếu merchant, mã đơn, số tiền, trạng thái và chống ghi trùng theo tham chiếu provider. Browser return chỉ đọc trạng thái backend, không xác nhận tiền.

Khoản tiền không khớp, đến muộn hoặc thừa được lưu chờ rà soát và hạch toán riêng; admin xem tab **Đối soát tiền vào**. Bản này chưa có API hoàn tiền nhà cung cấp hoặc nút tự phân bổ lại receipt đang REVIEW. Vận hành phải đối soát ngân hàng, thực hiện hoàn tiền ngoài hệ thống nếu cần, rồi đưa phần tự động hóa đó vào thay đổi riêng có kiểm thử.

## 6. Quy trình giải ngân

```text
Nghiệm thu
  -> ghi công nợ một lần
  -> giữ ít nhất 168 giờ
  -> ticket hợp lệ chưa đóng: tiếp tục giữ
  -> đủ điều kiện: freelancer yêu cầu rút
  -> admin A duyệt
  -> admin B nhận chuyển khoản
  -> chuyển tại ngân hàng
  -> xác nhận mã giao dịch + chứng từ
  -> ghi PAID và giảm công nợ
```

1. Freelancer thêm ngân hàng tại trang thu nhập; phải nhập mật khẩu hiện tại. Tài khoản OAuth chưa có mật khẩu phải thiết lập qua luồng Quên mật khẩu đã có.
2. Admin vào `/admin/finance`, xác minh chủ tài khoản bằng căn cứ vận hành thực tế. Hệ thống chưa có API tự xác minh tên thụ hưởng.
3. Trước khi bật chi, kiểm tra số dư thực tế tài khoản nguồn SAM, sao lưu DB/khóa, thử quy trình hai admin và đối soát kết quả. Cấp quyền ADMIN qua quy trình quản trị sẵn có; không mở đăng ký ADMIN công khai.
4. Chỉ sau đó đặt `FINANCE_LIVE_PAYOUTS_ENABLED=true` và khởi động lại backend. Cờ này bật yêu cầu và thao tác chi tiền thật, không biến chuyển khoản thành tự động.
5. Yêu cầu chỉ dành cho công nợ LIVE, đủ 168 giờ, không có ticket chặn và ngân hàng VERIFIED. Số tiền được giữ bằng allocation ngay trong transaction; hai yêu cầu đồng thời không được sử dụng cùng tiền.
6. Với `FINANCE_DUAL_CONTROL=true`, admin duyệt không thể nhận chuyển tiền cho chính lệnh đó. Mật khẩu được yêu cầu lại và giới hạn tần suất các thao tác nhạy cảm. Nếu Redis không thực thi được giới hạn, các thao tác này bị chặn cho đến khi dịch vụ phục hồi.
7. Người nhận xử lý phải giữ đúng snapshot người nhận, số tiền, mã lệnh; đối chiếu tại ngân hàng trước khi bấm xác nhận. Ngân hàng là nơi thực sự chuyển tiền. Lưu chứng từ trong kho nội bộ có phân quyền rồi nhập mã tham chiếu; bản này không tạo kho chứng từ mới.
8. Khi không rõ kết quả, chuyển **Cần đối soát** và tiếp tục giữ tiền; không tạo lệnh chuyển thứ hai. Xác nhận không chuyển tiền cần căn cứ ngân hàng và người đối soát độc lập khi bật kiểm soát kép.

Một mã giao dịch ngân hàng chỉ được xác nhận một lần. Yêu cầu lặp với cùng request key không tạo thêm lệnh. Quyết định giải ngân phải kiểm tra lại điều kiện tại thời điểm xử lý; admin không có nút bỏ qua 7 ngày.

Sổ cái đang giả định nguồn tiền thu và nguồn chi thuộc cùng hệ thống ngân quỹ SAM được đối soát. Nếu dùng nhiều tài khoản nguồn hoặc nhiều nhà cung cấp chi cần thêm hạch toán chuyển nguồn, phí ngân hàng và đối soát tương ứng. Không coi số công nợ trong ứng dụng là số dư ngân hàng thực tế.

## 7. Màn hình và API chính

| Đối tượng | Điểm truy cập |
| --- | --- |
| Freelancer web | Trang thu nhập hiện có: công nợ, hạn giữ, ngân hàng, rút và lịch sử |
| Admin web | `/admin/finance`: ngân hàng, lệnh chi, ticket, receipt, audit |
| Khách hàng | Nút bảo trì trong chat/workspace của dự án đã nghiệm thu |
| Mobile | Hồ sơ freelancer → tài chính; workspace hoàn thành → bảo trì |
| IPN công khai | `POST /api/v1/payments/pay2s-ipn` — xác minh chữ ký, không cần JWT |
| Ví và sổ giao dịch | `GET /api/v1/finance/wallet`, `GET /api/v1/finance/transactions?page=0` |
| Ngân hàng | `GET/POST /api/v1/finance/bank-accounts`, `POST /{id}/disable` |
| Yêu cầu rút | `GET/POST /api/v1/finance/withdrawals`, `GET /{id}`, `POST /{id}/cancel` |
| Bảo trì | `GET /api/v1/projects/{scopeId}/maintenance`, `GET/POST /maintenance-tickets` dưới cùng scope |
| Cập nhật ticket | `GET /api/v1/maintenance-tickets/{id}`, `POST /{id}/actions`, admin `POST /{id}/extend` |
| Đơn bảo trì | Báo giá `/maintenance-tickets/{id}/quotes`, chấp thuận `/maintenance-quotes/{id}/accept`, đơn `/paid-maintenance-orders/{id}/checkout` và `/actions` |

`scopeId` là ID dự án gốc hoặc ID đơn bảo trì có phí đã hoàn thành. Các đường dẫn rút gọn trong bảng đều nằm dưới `/api/v1`. API phân trang mặc định 25 bản ghi; request key là UUID, mobile lấy tại `GET /api/v1/finance/request-key`.

Để tránh nhầm ngữ nghĩa: `POSTED` là đã ghi công nợ; `SETTLED` là đã chuyển nguồn escrow sang nghĩa vụ phải trả; chỉ withdrawal `PAID` thể hiện vận hành đã xác nhận chuyển ngân hàng.

## 8. Web và mobile `.env`

Web dùng `VITE_API_URL=https://api.sam.pro.vn` (origin, không nối `/api/v1`); không thêm khóa Pay2S/khóa ngân hàng vào bundle. Mobile dùng:

```dotenv
EXPO_PUBLIC_API_URL=https://api.sam.pro.vn
EXPO_PUBLIC_AUTH_ORIGIN=https://sam.pro.vn
```

Giá trị API là origin, không nối `/api/v1`. Origin xác thực native phải được backend cho phép. Không đặt biến API thì mobile giữ chế độ dữ liệu xem trước và không thực hiện chi tiền thật. Bản API adapter dùng access token trong bộ nhớ và refresh cookie HttpOnly; AsyncStorage chỉ giữ metadata người dùng công khai. Cần kiểm thử vòng đời cookie trên bản build iOS/Android thực tế; chưa có chứng nhận kiểm thử thiết bị trong môi trường phát triển này.

Luồng thanh toán mobile mở trình duyệt ngoài; sau khi quay lại cần tải lại màn hình để đọc trạng thái backend. Không có callback deep link được xem là xác nhận thanh toán. Adapter mobile mới nối các luồng liên quan; tải danh sách dự án/chat hiện giới hạn theo trang đầu, không thay thế một đợt hoàn thiện toàn bộ tính năng mobile.

Mua gói dịch vụ/subscription cũ vẫn dùng VNPay. `PAYMENTS_PROVIDER` áp dụng nguồn hợp đồng/cọc và đơn bảo trì mới, không chuyển toàn bộ sản phẩm subscription sang Pay2S.

## 9. Migration và kiểm thử trước khi bật LIVE

Migration tài chính: `V37__Finance_Maintenance_And_Encrypted_Bank_Accounts.sql`; thống kê thu nhập và cập nhật ngân hàng bổ sung `V38__Freelancer_Income_And_Bank_Account_Revisions.sql`. API mới và kịch bản test xem [FREELANCER_INCOME_BANK_API.md](FREELANCER_INCOME_BANK_API.md). Flyway chạy khi khởi động theo cấu hình hiện có; giữ `HIBERNATE_DDL_AUTO=validate`. Sao lưu trước khi migrate, kiểm tra phiên bản schema thực tế và không tự sửa checksum migration đã chạy.

Dữ liệu thanh toán cũ thiếu dấu xác minh không tự trở thành tiền thật. Công nợ sandbox không được rút. Chưa có migration tự hạch toán lại mọi hợp đồng lịch sử đã quyết toán trước V37; phải kiểm kê và có kế hoạch backfill/đối soát riêng trước khi áp dụng cho hệ thống đang có công nợ thật.

Chạy backend:

```bash
./mvnw clean test
```

Bộ kiểm thử PostgreSQL chạy trong schema dùng một lần, không sửa các bảng ứng dụng. Cấp database test riêng và tài khoản có quyền tạo/xóa schema:

```dotenv
FINANCE_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/sam_finance_test
FINANCE_TEST_DATABASE_USER=<TEST_DB_USER>
FINANCE_TEST_DATABASE_PASSWORD=<TEST_DB_PASSWORD>
```

Đưa các biến vào môi trường tiến trình rồi chạy `./mvnw -Dtest=FinancePostgresTest test`. Không đặt thông tin database production vào cấu hình kiểm thử. Các test này kiểm tra journal cân bằng/bất biến, nguồn công nợ bất biến, reservation và hai yêu cầu rút đồng thời.

Các bước staging còn bắt buộc trước tiền thật: khởi động backend với PostgreSQL/Redis và migration; chạy bộ test PostgreSQL; gửi IPN sandbox thật và IPN sai/lặp/sai số tiền; nghiệm thu lặp; ranh giới 168 giờ; ticket ngày cuối; thử chi hai admin với ngân hàng thật theo giao dịch kiểm soát; kiểm tra timeout/đối soát; khôi phục DB và khóa; test web/mobile bằng tài khoản client/freelancer/admin độc lập.

Đã kiểm tra trong workspace: backend clean test 103 test, 97 đạt và 6 bỏ qua (5 PostgreSQL và 1 context test có sẵn); web lint/build và kiểm thử vòng đời phiên; mobile lint/typecheck/format/tests và export web bằng Expo. Kiểm thử HTTP dùng bộ converter thực của Spring đã kiểm tra tạo link và tiếp nhận IPN giữa các phiên bản Jackson. Môi trường thực thi không cho kết nối PostgreSQL/Docker; chưa dùng khóa merchant thực tế để giao dịch với Pay2S, nên các kết quả này chưa chứng minh một lần thu/chi production đã thành công. Không bật chi tiền thật chỉ dựa vào unit test.

## 10. Chính sách hiện đang đặt mặc định

| Chính sách | Giá trị |
| --- | --- |
| Cửa sổ mở ticket miễn phí và giữ tiền tối thiểu | 168 giờ từ nghiệm thu, UTC |
| Phản hồi đầu tiên | 24 giờ |
| Hạn xử lý ticket | 7 ngày từ lúc tạo, admin được gia hạn có lý do |
| Khách hàng kiểm tra bản vá | 48 giờ trước khi escalated, không tự đóng |
| Quá hạn | Chuyển xử lý bất đồng, tiếp tục giữ; không tự phạt |
| Phí nền tảng | 10%, công nợ thù lao 90%; cọc hoàn vào công nợ |
| Rút tối thiểu | 50.000 VND, cấu hình env |
| Hạn mức rút trong ngày | 100.000.000 VND theo ngày Việt Nam, cấu hình env |
| Kiểm soát kép | Bật mặc định |
| Chi tiền thật | Tắt mặc định |

Cần công bố điều kiện nghiệm thu, phạm vi lỗi miễn phí, quyền xử lý tranh chấp, phí/phần làm tròn, thời gian xử lý lệnh chi và phí ngân hàng trong điều khoản sản phẩm. Mã triển khai không thay thế quyết định vận hành hoặc việc rà soát hợp đồng dịch vụ thanh toán.


## 11. Chẩn đoán khi bấm thanh toán nhưng không tạo được link

Backend trả thông báo an toàn cho khách hàng, kèm `errors.provider`, `errors.reason`, `errors.reference`, `errors.ipnOrigin`, `errors.redirectOrigin`, và `errors.upstreamHttpStatus`/`errors.upstreamResultCode` khi nhận được các mã này. Hai origin phản ánh cấu hình thực của tiến trình đang chạy, chỉ chứa scheme/host/port, không chứa userinfo, path hoặc query. Không trả nguyên body/message từ nhà cung cấp vì chúng có thể chứa khóa, số tài khoản hoặc token URL.

Ví dụ cấu trúc lỗi (chỉ minh họa, không phải kết quả giao dịch thực):

```json
{
  "code": 1005,
  "message": "Cổng thanh toán chưa sẵn sàng cho giao dịch này. Vui lòng liên hệ hỗ trợ. Mã hỗ trợ: 00000000-0000-4000-8000-000000000000",
  "errors": {
    "provider": "PAY2S",
    "reason": "SIGNATURE_REJECTED",
    "reference": "00000000-0000-4000-8000-000000000000",
    "upstreamHttpStatus": "200",
    "upstreamResultCode": "42"
  }
}
```

Mã số `42` trong ví dụ không phải bảng mã lỗi được xác nhận của Pay2S; dùng mã thực tế trong response để đối chiếu hỗ trợ. HTTP trả về của SAM vẫn là 502 cho lỗi provider, hoặc 504 cho timeout, nên lỗi xác thực của Pay2S không làm frontend hiểu nhầm là phiên đăng nhập SAM hết hạn.

| `errors.reason` | Hướng kiểm tra |
| --- | --- |
| `ACCESS_DENIED` | Đúng bộ Partner/Access/Secret, quyền Collection Link và môi trường được Pay2S cấp; nếu upstream 403 cũng kiểm tra chặn truy cập từ nhà cung cấp |
| `SIGNATURE_REJECTED` | Secret Key nguyên bản, không Base64; ba khóa cùng bộ; không có khoảng trắng thừa |
| `BANK_ACCOUNT_REJECTED` | `bank_id` và số tài khoản thuộc ngân hàng đã liên kết; demo dùng tài khoản demo được cấp |
| `IPN_URL_REJECTED`, `REDIRECT_URL_REJECTED`, `CALLBACK_URL_REJECTED` | Provider từ chối IPN, redirect hoặc callback chung; kiểm tra đúng trường và origin thực tế trong lỗi |
| `IPN_URL_INVALID`, `REDIRECT_URL_INVALID` | Backend phát hiện cấu hình URL không hợp lệ trước khi gọi provider; IPN phải HTTPS công khai, không dùng localhost/nội bộ |
| `AMOUNT_REJECTED` | Số tiền VND nguyên dương và hạn mức provider |
| `PROVIDER_REJECTED` | Provider trả resultCode khác 0; dùng reference/mã thực tế để đối chiếu, không tự coi đơn đã thanh toán |
| `DNS_FAILED` | Máy chạy Java phân giải được host API Pay2S |
| `TLS_FAILED` | Kiểm tra Java trust store, chứng chỉ CA và proxy; không tắt kiểm tra TLS |
| `CONNECTION_FAILED`, `TIMEOUT` | Kết nối Internet/proxy/firewall của tiến trình backend; không tự động thử lại POST bằng một mã đơn mới |
| `RATE_LIMITED` | Giảm tần suất tạo link, thử lại sau |
| `HTTP_REJECTED` | Xem HTTP upstream; kiểm tra API origin, đường dẫn và gateway/WAF; response HTML không được coi là JSON thành công |
| `INVALID_RESPONSE`, `RESPONSE_TOO_LARGE` | Provider không trả JSON đúng hợp đồng hoặc body vượt 64 KiB; cần đối chiếu phiên bản API |
| `PAYMENT_MISMATCH` | Provider trả khác orderId/số tiền; backend chủ động từ chối |
| `UNTRUSTED_CHECKOUT_URL` | Link không thuộc origin Pay2S HTTPS cho phép; không mở rộng allowlist bằng wildcard để vượt lỗi |
| `ENVIRONMENT_MISMATCH` | Chọn LIVE nhưng nhận demo hoặc chọn SANDBOX nhưng provider khai báo production/live; không đổi nhãn môi trường để vượt kiểm soát |

Tìm dòng `PAY2S_CHECKOUT_FAILED` trong console/log backend theo cùng `reference`. Dòng này chỉ chứa mã phân loại, HTTP/result code, môi trường, API origin, IPN/redirect origin và tên lớp lỗi, không chứa body, khóa, chữ ký, số tài khoản hoặc URL có token.

Nếu lỗi xảy ra trên máy local, kiểm tra đặc biệt `PAY2S_IPN_URL`: `http://localhost:8080/...` không thể nhận callback từ server Pay2S. Cần backend staging hoặc tunnel HTTPS trỏ về backend local. Sai IPN này chắc chắn ảnh hưởng việc xác nhận sau thanh toán, nhưng chưa đủ để kết luận mọi lỗi tạo link đều do IPN.

Tài liệu Pay2S có mô tả sandbox chạy chung host production và cũng liệt kê host sandbox riêng. Dùng host tương ứng bộ khóa được cấp theo [tài liệu môi trường](https://developer.pay2s.vn/docs/api/tich-hop-ky-thuat/); giữ `PAYMENTS_ENVIRONMENT=SANDBOX` khi dùng demo. Không tự chuyển sang LIVE để thử khắc phục lỗi kết nối.


### Test local với frontend cổng 5173

Cấu hình gọi create theo [endpoint Collection Link chính thức](https://developer.pay2s.vn/docs/api/collection-link/):

```dotenv
PAYMENTS_PROVIDER=pay2s
PAYMENTS_ENVIRONMENT=SANDBOX
PAY2S_BASE_URL=https://payment.pay2s.vn
PAYMENTS_RETURN_URL=http://localhost:5173/client/payment/vnpay-return
PAYMENTS_ALLOWED_RETURN_ORIGINS=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Backend nối thêm `/v1/gateway/api/create` và gửi POST JSON. Không đặt toàn bộ đường dẫn create vào `PAY2S_BASE_URL`, không đổi sang LIVE để sửa host. Request key và IPN vẫn được ký/xác minh theo đúng bộ khóa demo. Không tự động thử lại qua một host khác khi provider trả lỗi.

Frontend cổng 5173 chỉ là nơi trình duyệt quay lại. Backend cổng 8080 nhận IPN tại `/api/v1/payments/pay2s-ipn`. IPN phải là URL HTTPS công khai. Với frontend local trong SANDBOX, backend tự thay redirect gửi cho provider bằng `/api/v1/payments/pay2s-return` trên cùng origin HTTPS của IPN, rồi endpoint đó chuyển trình duyệt về `PAYMENTS_RETURN_URL` đã cấu hình. Để kiểm thử toàn bộ xác nhận thanh toán bằng IPN thực cần một địa chỉ HTTPS công khai forward về backend. Không dùng browser return để tự ghi nhận đã nhận tiền.

Với HTTP 400/422 có JSON, backend cũng phân loại các message lỗi ở root hoặc `error.message`; không bỏ qua chúng chỉ vì response thiếu `resultCode`. HTTP 400 riêng lẻ chưa đủ để kết luận sai khóa, sai host hay sai callback.

### Khắc phục CALLBACK_URL_REJECTED khi test local

Đây là phân loại từ thông báo lỗi callback của provider, có thể liên quan `ipnUrl` hoặc `redirectUrl`. Không suy ra lỗi chữ ký hay thay bộ khóa chỉ từ mã này. Nếu IPN đang là localhost thì sửa cấu hình đó trước: Pay2S gọi IPN từ server bên ngoài, không phải từ trình duyệt đang chạy web SAM.

1. Giữ web tại `http://localhost:5173` và backend tại `http://localhost:8080`.
2. Tạo HTTPS tunnel vào **backend 8080**. Có thể cài `cloudflared` theo [Downloads chính thức](https://developers.cloudflare.com/tunnel/downloads/) và chạy:

```bash
cloudflared tunnel --url http://localhost:8080
```

Nếu dùng Docker trên Linux, có thể dùng image chính thức để chạy cùng lệnh mà không cài CLI trên host:

```bash
docker run --rm --network host cloudflare/cloudflared:latest tunnel --url http://127.0.0.1:8080
```

Chỉ chạy một trong hai cách. Terminal in một URL dạng `https://<ten-ngau-nhien>.trycloudflare.com`; giữ tiến trình hoạt động trong thời gian test. Dịch vụ local có thể được truy cập qua URL đó, nên chỉ dùng môi trường/dữ liệu test và dừng tunnel khi xong. Không bật đăng nhập email/trang challenge trên đường IPN vì server Pay2S không thực hiện đăng nhập tương tác. Theo [Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/), hostname thay đổi khi tạo tunnel mới.

3. Đặt **URL thực được in ra**, không giữ nguyên ví dụ bên dưới:

```dotenv
PAYMENTS_ENVIRONMENT=SANDBOX
PAY2S_BASE_URL=https://payment.pay2s.vn
PAY2S_IPN_URL=https://<ten-ngau-nhien>.trycloudflare.com/api/v1/payments/pay2s-ipn
PAYMENTS_RETURN_URL=http://localhost:5173/client/payment/vnpay-return
PAYMENTS_ALLOWED_RETURN_ORIGINS=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

4. Nạp lại cấu hình. IDE/JAR: dừng và chạy lại backend. Docker Compose dùng `env_file`: recreate container để nhận biến mới, không chỉ restart container cũ:

```bash
docker compose up -d --force-recreate backend
```

Chạy trong thư mục `sam-be` và dùng đúng file Compose của môi trường đang chạy. Không cần đổi khóa Pay2S/khóa mã hóa hoặc bật chi tiền thật.

5. Kiểm tra route bằng request **không có chữ ký**, không tạo giao dịch:

```bash
curl -i --request POST \
  'https://<ten-ngau-nhien>.trycloudflare.com/api/v1/payments/pay2s-ipn' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

Kết quả mong đợi cho request trống này là HTTP 401 với `Invalid Pay2S signature`: chứng tỏ đã tới đúng handler và chữ ký vẫn được kiểm tra. HTTP 404/502 hoặc trang đăng nhập/challenge cho thấy tunnel/route chưa đúng. IPN thật có chữ ký hợp lệ mới được xác minh/hạch toán và ACK thành công.

6. Tạo một thanh toán demo mới từ hợp đồng. Nếu provider vẫn trả CALLBACK_URL_REJECTED sau khi IPN đã công khai, cần phân biệt lỗi đối với redirect URL local và IPN theo thông báo provider; không mở xác nhận thanh toán từ browser để vượt lỗi.


### Callback HTTPS cho frontend vẫn chạy localhost:5173

Frontend FundingPanel gửi return URL theo `window.location.origin`, ví dụ `http://localhost:5173/workspace/<roomId>/contract`. Do đó chỉ sửa `PAY2S_IPN_URL` chưa đủ để mọi callback gửi tới provider đều dùng HTTPS.

Bản triển khai hiện tại dùng một tunnel backend cho cả hai đường:

```text
Pay2S IPN -> https://<backend-tunnel>/api/v1/payments/pay2s-ipn
Pay2S redirect -> https://<backend-tunnel>/api/v1/payments/pay2s-return
Browser follows relay -> http://localhost:5173/client/payment/vnpay-return
```

Không cần thêm tunnel frontend hoặc thêm biến env. Giữ `PAYMENTS_RETURN_URL` là URL web local, `PAYMENTS_ALLOWED_RETURN_ORIGINS`/CORS cho localhost:5173, và IPN dùng tunnel HTTPS thật. Trong SANDBOX, request tạo link có frontend return HTTP/local sẽ được ký với redirect URL relay HTTPS thực sự gửi đi. Với frontend HTTPS công khai, backend dùng trực tiếp return URL đã qua allowlist.

Endpoint GET `/api/v1/payments/pay2s-return` công khai chỉ chuyển hướng 303 tới URL mặc định được backend cho phép. Không nhận đích redirect từ query, không chuyển tiếp tham số trạng thái/tiền từ provider, không sửa trạng thái thanh toán, trả `Cache-Control: no-store` và `Referrer-Policy: no-referrer`. Trang web hiện có dùng sessionStorage đã lưu để quay lại đúng workspace. Xác nhận tiền vẫn chỉ đi qua IPN có chữ ký.

**Khi vừa nhận thay đổi mã nguồn này và chạy bằng Compose build local**, cần build image lại:

```bash
docker compose up -d --build --force-recreate backend
```

Chỉ `--force-recreate` cập nhật env nhưng có thể vẫn chạy mã trong image cũ. Không cần migration mới cho endpoint relay. Sau build, mở URL tunnel `/api/v1/payments/pay2s-return`: trình duyệt phải quay về trang frontend local. Nếu 404 hoặc yêu cầu JWT ở route relay, kiểm tra image và reverse proxy đang chạy đúng bản.

Nếu vẫn lỗi, lấy response mới hoặc dòng `PAY2S_CHECKOUT_FAILED`: `reason` giờ phân biệt `IPN_URL_REJECTED` và `REDIRECT_URL_REJECTED`; `ipnOrigin`/`redirectOrigin` cho biết cả hai origin thực gửi tới provider. Origin còn localhost hoặc thiếu hai trường này có thể cho thấy đang dùng cấu hình/image cũ. Không cần gửi nguyên body provider hoặc khóa bí mật để chẩn đoán.

### Mô phỏng thanh toán demo bằng terminal, không cần Postman

Script chỉ cần Python 3, không cài thư viện và không cần rebuild backend:

```bash
cd /home/tinacoding/Documents/projects/sam-projects/sam-be
python3 scripts/pay2s_simulate_payment.py
```

Script hỏi `paymentId`, đọc `PAY2S_SECRET_KEY` từ `.env` của repo (biến môi trường process ưu tiên nếu đã export), yêu cầu `PAYMENTS_ENVIRONMENT=SANDBOX` và tự chuyển UUID thành `orderId` không có dấu gạch. Script không chạy nội dung `.env` như shell, không in khóa hoặc phản hồi provider nguyên bản, không theo HTTP redirect khi gửi khóa. Endpoint cố định là API demo chính thức, không lấy đích gửi khóa từ URL tùy ý.

Lấy `result.paymentId` từ DevTools → Network → response tạo thanh toán:

- Khách hàng: `POST /api/v1/payments/fund`, nạp 100% giá trị hợp đồng.
- Freelancer: `POST /api/v1/payments/deposit`, đặt cọc 2% giá trị hợp đồng.

Bật **Preserve log** trước khi bấm thanh toán để giữ response khi trình duyệt chuyển trang. Chạy script một lần cho mỗi `paymentId` khác nhau; mỗi lần chạy sẽ mô phỏng thành công đơn demo đó. Có thể truyền UUID trực tiếp làm đối số thay vì nhập tương tác. Chỉ dùng ID của đơn đã tạo thành công với khóa demo hiện tại; không nhập `contractId`, mã `PAY2SJSC` hoặc link QR.

Theo [API mô phỏng Pay2S](https://developer.pay2s.vn/docs/api/simulate-payment/), request dùng Secret Key demo làm Bearer token; provider tự lấy số tiền đã lưu của đơn và gửi IPN có chữ ký tới URL đã lưu khi tạo đơn. Không có chuyển tiền thật. Script không sửa DB hay thay thế việc xác minh chữ ký IPN của backend.

Giữ tunnel backend đang chạy. Nếu hostname tunnel thay đổi, cập nhật `PAY2S_IPN_URL`, nạp lại backend và tạo đơn mới. Phản hồi mô phỏng thành công chỉ xác nhận phía Pay2S; cần đợi callback rồi kiểm tra `GET /api/v1/payments/funding/{contractId}` bằng JWT của thành viên hợp đồng. Sau hai IPN hợp lệ, `fundPaid`, `depositPaid`, `allPaid` đều là `true`; dự án đang `AWAITING_PAYMENT` chuyển sang `IN_PROGRESS`.

Nếu đã sửa lỗi kết nối callback và cần yêu cầu gửi lại IPN, chạy với cùng `paymentId`:

```bash
python3 scripts/pay2s_simulate_payment.py --resend-ipn
```

Không dùng cờ này cho mọi lần thử thông thường. HTTP 401: kiểm tra Secret Key; 403: tài khoản/đơn không phải demo; 404: ID không thuộc phạm vi khóa; 409: kiểm tra điều kiện đơn, ngân hàng demo, IPN URL. Không đưa khóa vào frontend hoặc gửi khóa qua chat.
