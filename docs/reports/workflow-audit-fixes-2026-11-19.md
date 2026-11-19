# Báo cáo sửa lỗi workflow, tài chính và thông báo

Ngày: 19/11/2026 (Asia/Ho_Chi_Minh). Nhánh: `fix/workflow-audit-notifications` ở cả BE và FE.
Mốc trước khi sửa: BE `36d87b4`, FE `ba05ca2`.

## Kết luận

Đã sửa nguyên nhân trong code của cả 5 vấn đề được review. Các thay đổi hướng tới
production: bảo toàn dữ liệu đã ghi nhận, dùng một đồng hồ tài chính, xử lý kết quả AI
thận trọng, tập trung điều hướng thông báo và tương thích với tài khoản ngân hàng cũ.

Đây là bản sẵn sàng để review và kiểm thử staging, **chưa phải xác nhận được triển khai
production vô điều kiện**. Người phụ trách đã xác nhận V47 chưa chạy trên production/staging;
đây là chỉnh sửa migration chưa phát hành. Cần hoàn tất kiểm thử tích hợp/UI trước khi phát hành.
Không có thao tác sửa dữ liệu hay triển khai lên server trong lần này.

## 1. V47 ghi đè số tiền của hợp đồng mẫu

**Trước:** migration cập nhật tất cả payment của một contract seed thành 12.000.000 VND,
không phân biệt tiền hợp đồng, cọc, bảo trì hay môi trường LIVE/SANDBOX. Nó còn sửa ngân sách,
proposal, giá hợp đồng và mức phí hồ sơ theo ID dù dữ liệu có thể đã được người dùng thay đổi.

**Đã sửa:** giữ version V47 nhưng chuyển thành migration không cập nhật dữ liệu (`SELECT 1`).
Việc làm đẹp số tiền demo không còn được thực hiện tự động trong quá trình nâng cấp ứng dụng.
Ngân sách, số tiền và hồ sơ seed hiện hữu được giữ nguyên, kể cả khi cài mới.

**Bằng chứng:** test chạy SQL V47 trên các bảng đã có dữ liệu mẫu và payment phát sinh ở ba
môi trường; so sánh toàn bộ dữ liệu trước/sau. V47 cũ làm test thất bại; V47 mới vượt qua test.
Test dùng SQLite chuẩn thư viện Python vì script mới chỉ chứa SQL đọc, không phải kiểm thử
một lần nâng cấp Flyway/PostgreSQL thực tế.

**Trạng thái triển khai:** người phụ trách xác nhận ngày 19/11/2026 rằng V47 chỉ ở nhánh này,
chưa chạy trên production/staging. Vì thế sửa file migration chưa phát hành là phù hợp và không
thay checksum đã ghi nhận ở hai môi trường này. Khi triển khai có thể đối chiếu lại bằng SQL đọc:

```sql
SELECT version, script, checksum, success, installed_on
FROM flyway_schema_history
WHERE version = '47';
```

- Nếu chưa chạy: phát hành V47 an toàn này theo quy trình migration bình thường.
- Nếu đã chạy: không đưa file V47 đã thay đổi lên môi trường đó ngay; checksum Flyway sẽ khác.
  Sao lưu và kiểm tra script/checksum đã áp dụng, đối chiếu các khoản liên quan với receipt,
  checkout, settlement và nhật ký kế toán trước khi lập phương án chuyển đổi được duyệt.
- Không chạy `flyway repair` chỉ để bỏ qua lỗi checksum; không suy ngược số tiền bằng một tỷ lệ
  hoặc gán tất cả payment về số tiền hợp đồng. Bản sửa không tự phục hồi dữ liệu đã bị V47 cũ ghi đè.

## 2. Thời điểm nghiệm thu lệch múi giờ

**Trước:** lấy `LocalDateTime.now()` theo múi giờ JVM nhưng bên sổ tài chính lại diễn giải giá trị
này là UTC. JVM chạy giờ Việt Nam có thể khiến mốc nghiệm thu và hết hạn bảo trì lệch 7 giờ.

**Đã sửa:** nghiệm thu lấy thời gian từ `financeStore.now()`, rồi chuyển sang `LocalDateTime`
theo UTC. Mốc này tiếp tục được sử dụng cho settlement và thời hạn giữ tiền 168 giờ.
Không cần thay đổi cấu trúc dữ liệu hoặc thêm cấu hình múi giờ để luồng này hoạt động đúng.

**Bằng chứng:** gọi service nghiệm thu thực tế trong unit test với múi giờ JVM UTC,
Asia/Ho_Chi_Minh và America/New_York. Cả ba ghi cùng một thời điểm từ đồng hồ tài chính.
Test xác nhận được mở bảo hành ngay tại thời điểm nghiệm thu, đóng cửa sổ ở đúng 168 giờ,
và lần gọi nghiệm thu lặp lại không thay thời gian hay tạo lại settlement.

**Dữ liệu lịch sử:** không tự dịch chuyển mốc nghiệm thu đã lưu. Những bản ghi được tạo bởi
JVM ngoài UTC trước bản sửa cần được đối chiếu riêng nếu đã ảnh hưởng thời hạn bảo trì/rút tiền.

## 3. Bộ lọc AI bỏ nhầm cảnh báo thương mại

**Trước:** chỉ cần cảnh báo chứa “chữ ký”, “ký xác nhận” hoặc thông tin bên A/B là có thể bị xóa,
kể cả cảnh báo về mâu thuẫn thời điểm thanh toán và nghiệm thu.

**Đã sửa:**

- Chỉ bỏ câu hoàn chỉnh, rõ ràng về thiếu chữ ký/định danh/liên hệ thuộc phần nền tảng quản lý.
- Giữ lại câu có nội dung thương mại hoặc câu trộn nhiều vấn đề; đưa về `NEEDS_CONFIRM`.
- Lọc từng cảnh báo riêng, để cảnh báo thương mại vẫn hiện khi đi cùng thông tin thiếu chữ ký.
- Đối chiếu số tiền trích xuất với giá hệ thống và giá ban đầu bằng `BigDecimal`, dung sai 1.000 VND.
  Không dựa hoàn toàn vào cờ khớp/đổi giá do AI trả về.
- Không cho `OK` khi thiếu số tiền trích xuất hoặc verdict không xác định. Giữ yêu cầu xác nhận
  của AI nếu không có căn cứ rõ ràng để loại bỏ.
- Xóa câu chat thành công cũ nếu kết quả đã bị chuyển về yêu cầu xác nhận.

**Bằng chứng:** test bao gồm câu mâu thuẫn thanh toán trước/sau ký nghiệm thu, câu trộn lỗi chữ ký
và bảo hành, danh sách có cả cảnh báo hợp lệ/lỗi nền tảng, thiếu số tiền, verdict lạ và cờ đổi giá sai.
Việc bỏ qua cảnh báo dùng quy tắc thận trọng: câu chưa nhận diện được sẽ cần hai bên kiểm tra.

## 4. Điều hướng thông báo không đồng bộ

**Trước:** menu chuông, trang tất cả thông báo và dashboard dùng các nhánh điều hướng khác nhau.
Thông báo bàn giao thiếu roomId có thể mở chi tiết dự án thay vì workspace.

**Đã sửa:** cả ba nơi dùng chung `notificationDestination()`:

- Có roomId: mở đúng phòng chat.
- Bàn giao, chỉnh sửa, nghiệm thu, khởi động, quyết toán, ký quỹ hoặc bảo trì có jobId:
  mở workspace bằng jobId để BE phân giải phòng.
- Kích hoạt subscription: mở trang gói tương ứng vai trò.
- Lời mời và proposal: giữ đường dẫn nghiệp vụ tương ứng.
- Admin nhận escalation bảo trì: mở trang quản trị ticket, tránh truy cập phòng chat không thuộc mình.
- Thiếu thông tin định tuyến: trả `null`, tránh sinh URL chứa `null`/`undefined`.

Trang tất cả thông báo cũng có tiêu đề cụ thể cho bàn giao, chỉnh sửa, nghiệm thu và bảo trì.

**Bằng chứng:** 12 test FE kiểm tra các loại sự kiện, cả hai vai trò dự án, roomId ưu tiên,
subscription không có jobId, lời mời/proposal, escalation admin và sự kiện thiếu đích đến.

## 5. Mã ngân hàng MBB cũ không dùng được với danh sách MB mới

**Trước:** bản cũ gợi ý nhập `MBB`; bản mới chỉ cho chọn `MB`. Cập nhật tài khoản đã lưu `MBB`
có thể bị chặn. Chỉ chuyển mã ở FE còn có thể tạo trùng tài khoản vì fingerprint bao gồm bankCode.

**Đã sửa:**

- BE và FE cùng quy ước mã chuẩn `MB`, chấp nhận alias `MBB`, kể cả chữ thường/khoảng trắng đầu cuối.
- FE chuẩn hóa khi mở form sửa, khi hiển thị lựa chọn, khi tìm kiếm và trước khi gửi API.
- BE kiểm tra 40 mã được hỗ trợ, có thông báo riêng khi mã không hợp lệ.
- Khi thêm MB, BE tìm cả fingerprint MB và MBB trong phạm vi đúng owner, dưới khóa owner hiện có.
  Tài khoản đã tồn tại được sử dụng lại, giữ nguyên trạng thái xác minh.
- Khi so sánh thay đổi, MB và MBB được xem là cùng ngân hàng. Đổi cách viết mã hoặc hoa/thường
  của tên không tự tạo bản thay thế hay bị chặn chỉ vì có lệnh rút tiền đang hoạt động.
- Không ghi lại bankCode/fingerprint của bản ghi cũ; không sửa snapshot người nhận của lệnh rút tiền.
- BE xử lý thêm khoảng trắng Unicode trong số tài khoản. Ký tự lạ vẫn bị từ chối.

**Bằng chứng:** test BE cho cập nhật tài khoản MBB có lệnh rút đang hoạt động nhưng thông tin
không thay đổi, thêm MB tìm lại bản MBB VERIFIED thay vì tạo bản mới, và API từ chối mã tùy ý.
Ba test FE kiểm tra alias, tính ổn định/duy nhất của mã chuẩn và từ chối đầu vào tùy ý.
Đã đối chiếu 40 mã chuẩn trong BE và FE khớp nhau.

**Giới hạn:** danh mục vẫn nằm trong hai repo và phải được cập nhật đồng bộ khi thêm ngân hàng.
Các alias chưa được định nghĩa không được tự suy đoán. Thêm tài khoản vẫn theo quy trình admin
xác minh hiện hữu; bản sửa không bổ sung tra cứu chủ tài khoản trực tiếp tại ngân hàng.

## Kiểm thử và CI

| Kiểm tra | Kết quả |
|---|---|
| Toàn bộ test BE | 282 test: 247 đạt, 35 bỏ qua, 0 lỗi |
| Test BE tập trung vào ba phần vừa sửa | 22 đạt, 0 bỏ qua |
| An toàn SQL V47 | 1 test đạt; negative control xác nhận V47 cũ làm test thất bại |
| Toàn bộ test FE | 11 tệp test đạt; có 15 trường hợp mới cho routing và mã ngân hàng |
| FE TypeScript + Vite production build | Đạt |
| Biome CI toàn FE | Đạt |
| Java format trên các file thay đổi | Đạt với Spotless/AOSP |

Đã cập nhật hai test FE cũ để phù hợp `beginCheckout()` bất đồng bộ và mô phỏng `import.meta.env`
trong Node. Các assertion kiểm tra URL thanh toán và chống open redirect vẫn được giữ.

Đã thêm bước chạy test FE vào CI PR/develop và bước kiểm tra an toàn V47 vào CI BE PR/develop.
CI BE hiện đã cấu hình PostgreSQL cho các test tích hợp; cần chờ kết quả của pipeline sau khi push.

Lệnh tái kiểm tra:

```bash
# Trong sam-be
./mvnw -o test
python3 -B -m unittest discover -s src/test/python -v

# Trong sam-fe
pnpm test
pnpm exec biome ci .
pnpm run build
```

## Điều kiện trước khi phát hành production

1. Dùng bản V47 an toàn đã sửa. Trạng thái chưa áp dụng trên production/staging đã được người
   phụ trách xác nhận; đối chiếu lại khi phát hành, và xử lý riêng môi trường dev nào đã chạy bản cũ.
2. Chạy CI có PostgreSQL/Redis và kiểm thử staging luồng nạp tiền → báo cáo → bàn giao → yêu cầu
   sửa → nghiệm thu → mở/đóng ticket → đủ điều kiện rút. Không dùng giao dịch LIVE để mô phỏng test.
3. Kiểm thử trình duyệt desktop/mobile cho bộ gõ tiếng Việt, bộ chọn ngân hàng và thao tác bàn phím.
4. Kiểm tra cả tài khoản ngân hàng MBB cũ và tài khoản MB mới; xác nhận lịch sử lệnh chi không đổi.
5. Duy trì cấu hình Google OAuth origin production đã được sửa; đây là cấu hình server, không phải
   thay đổi vai trò CLIENT trong bản code này.

Ở môi trường làm việc hiện tại không có quyền truy cập Docker socket để chạy PostgreSQL integration,
và Chromium bị sandbox chặn khởi động. Vì vậy các test DB được bỏ qua và UI/E2E chưa được xác nhận.
Không coi kết quả unit test/build là bằng chứng đủ để bỏ các bước staging ở trên.
