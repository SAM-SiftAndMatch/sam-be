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
PAY2S_BASE_URL=https://sandbox-payment.pay2s.vn
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

`ACB` là ví dụ, phải thay theo ngân hàng đã liên kết. `PAY2S_BASE_URL` không có dấu `/` ở cuối. Endpoint tạo link được backend nối thêm `/v1/gateway/api/create`.

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

Migration mới: `V37__Finance_Maintenance_And_Encrypted_Bank_Accounts.sql`. Flyway chạy khi khởi động theo cấu hình hiện có; giữ `HIBERNATE_DDL_AUTO=validate`. Sao lưu trước khi migrate, kiểm tra phiên bản schema thực tế và không tự sửa checksum migration đã chạy.

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
