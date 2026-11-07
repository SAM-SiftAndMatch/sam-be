# Thu nhập freelancer, ngân hàng và nhắc cập nhật trên homepage

Cập nhật 06/11/2026. Áp dụng cho `sam-be`, `sam-fe`, `sam-mobile`. Bản này bổ sung vào [hướng dẫn Pay2S](PAY2S_FINANCE_SETUP.md).

## 1. Thu nhập khác số dư rút được

**Thu nhập** là thù lao sau phí từ dự án/đơn bảo trì đã nghiệm thu. Không cộng hoàn cọc, không cộng tiền chỉ vừa nạp escrow, không giảm khi tạo yêu cầu rút hoặc khi chuyển ngân hàng. Vẫn ghi nhận thu nhập trong thời gian bảo trì 168 giờ; việc rút tiếp tục theo quy tắc giữ tiền và ticket đã chốt.

**Ví/công nợ** theo dõi tiền có thể rút, đang giữ, đã đặt lệnh chi và đã chuyển. Tiền hoàn cọc có thể nằm trong khoản phải trả nhưng không phải thu nhập từ công việc.

Ví dụ: giá trị nghiệm thu 10.000.000 VND, phí 1.000.000 VND, cọc hoàn 200.000 VND. Thu nhập là **9.000.000 VND**; khoản phải trả trước giữ/rút là **9.200.000 VND**. Sau chuyển khoản 9.200.000 VND, thu nhập vẫn 9.000.000 VND.

Thu nhập thật và sandbox tách riêng. Thử bằng Pay2S sandbox sẽ tăng `sandboxNetIncome`/`yearSandboxNetIncome`, không tăng `totalNetIncome` thật. Web/mobile hiển thị số mô phỏng riêng.

Nguồn là `finance_payables` đã ghi nhận khi nghiệm thu, đối chiếu giá trị gốc từ settlement/đơn bảo trì. Không suy đoán thu nhập bằng số dư ví cũ. Hợp đồng đã hoàn tất trước migration V37 nhưng chưa được đối soát/backfill vào nguồn này không tự xuất hiện trong thống kê mới.

## 2. API mới

Tất cả API dưới đây yêu cầu Bearer JWT hợp lệ và vai trò **FREELANCER**. Backend lấy chủ tài khoản từ phiên, không nhận `userId` từ query/body. Dữ liệu trả trong `ApiResponse.result`, HTTP response không cho cache.

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | `/api/v1/finance/income?year=2026` | Tổng thu nhập, thống kê năm, 12 tháng |
| GET | `/api/v1/finance/income/entries?year=2026&page=0` | Chi tiết thu nhập đã nghiệm thu, 25 mục/trang |
| GET | `/api/v1/finance/bank-accounts/setup-status` | Trạng thái khai báo ngân hàng cho homepage |
| GET | `/api/v1/finance/bank-accounts` | Danh sách ngân hàng đã che thông tin |
| POST | `/api/v1/finance/bank-accounts` | Thêm ngân hàng để xác minh |
| PUT | `/api/v1/finance/bank-accounts/{id}` | Cập nhật bằng phiên bản mới, giữ lịch sử |
| POST | `/api/v1/finance/bank-accounts/{id}/disable` | Ngừng dùng cho các yêu cầu mới |

Năm mặc định do backend xác định theo `Asia/Ho_Chi_Minh`, hỗ trợ 2000–2100. Phân tháng theo **thời điểm nghiệm thu**, không theo ngày mở ticket hay ngày rút tiền. Khoảng năm lấy đầu năm bao gồm và đầu năm sau không bao gồm.

### Kết quả thu nhập

| Trường | Ý nghĩa |
| --- | --- |
| `currency`, `timezone`, `year` | VND, múi giờ báo cáo, năm đã chọn |
| `totalNetIncome` | Tổng thù lao sau phí thật từ trước đến nay |
| `totalGrossIncome`, `totalPlatformFees` | Tổng giá trị nghiệm thu và phí thật |
| `completedCount` | Số lần nghiệm thu thật, gồm cả đơn bảo trì có phí |
| `yearNetIncome`, `yearGrossIncome`, `yearPlatformFees` | Số liệu thật trong năm được chọn |
| `yearCompletedCount` | Số lần nghiệm thu thật trong năm |
| `sandboxNetIncome`, `yearSandboxNetIncome` | Thù lao mô phỏng, tách khỏi tổng thật |
| `months` | Đủ 12 phần tử, tháng không có phát sinh trả số 0 |

Mỗi phần tử `months` có `month`, `netIncome`, `grossIncome`, `platformFees`, `completedCount`, `sandboxNetIncome`.

API chi tiết trả `items`, `page`, `size`, `totalElements`, `year`. Mỗi item gồm `id`, `jobId`, `jobTitle`, `maintenanceOrderId` (null nếu dự án gốc), `acceptedAt`, `grossIncome`, `platformFees`, `netIncome`. Danh sách chính chỉ gồm thu nhập thật, không trộn demo vào lịch sử thật.

### Thêm/cập nhật ngân hàng

Body POST và PUT giống nhau:

```json
{
  "bankCode": "VCB",
  "accountNumber": "00123456789",
  "accountHolder": "NGUYEN VAN A",
  "password": "MAT_KHAU_TAI_KHOAN_HIEN_TAI"
}
```

`password` dùng để xác thực lại, không lưu vào bản ghi ngân hàng. Tài khoản OAuth chưa có mật khẩu cần dùng chức năng thiết lập qua Quên mật khẩu. Các thao tác dùng chung giới hạn nhạy cảm 5 lượt/300 giây/người dùng; Redis không thực thi được giới hạn thì thao tác bị chặn.

PUT yêu cầu nhập lại đầy đủ thông tin, không tải số tài khoản/tên đầy đủ từ API về form để điền sẵn. Backend mã hóa AES-256-GCM như luồng thêm; không trả bản mã, số tài khoản đầy đủ hoặc tên thụ hưởng trong response thông thường.

Khi thay thông tin: giữ bản mã bản cũ, chuyển bản cũ DISABLED và liên kết `replaced_by` tới bản mới. Bản mới thông thường PENDING và phải xác minh trước khi rút. Nếu chọn đúng một tài khoản đã có và đã xác minh của chính người đó thì backend tái sử dụng bản đã xác minh, không tạo bản trùng. Gửi lặp PUT cùng ID cũ và cùng thông tin trả lại bản thay thế, không tạo thêm phiên bản.

Không sửa ngân hàng gắn với lệnh chi REQUESTED/APPROVED/PROCESSING/RECONCILIATION_REQUIRED. Backend trả 409 và hướng dẫn thêm tài khoản riêng hoặc chờ lệnh cũ kết thúc. Người dùng vẫn có thể dùng POST để thêm ngân hàng khác. Snapshot người nhận của lệnh cũ không đổi.

Gửi PUT giữ nguyên toàn bộ dữ liệu của bản PENDING/VERIFIED không tạo phiên bản thừa. Bản REJECTED có thể gửi lại qua PUT để tạo bản PENDING mới, kể cả cần gửi lại cùng thông tin sau khi đã bổ sung căn cứ xác minh với hỗ trợ.

## 3. Homepage và giao diện

| Trạng thái backend | Ý nghĩa | Giao diện |
| --- | --- | --- |
| `ACTION_REQUIRED` | Không có tài khoản VERIFIED hoặc PENDING; gồm chưa khai báo hoặc chỉ còn bản từ chối/ngừng dùng | Banner nhắc bổ sung + nút Cập nhật ngân hàng |
| `PENDING` | Đã gửi, chưa có bản VERIFIED | Banner đang chờ xác minh + nút Xem tài khoản |
| `READY` | Có ít nhất một bản VERIFIED | Ẩn banner nhắc bổ sung |

Kết quả còn có `hasVerifiedAccount`, `hasBankAccount` (có bản VERIFIED/PENDING), `needsBankAccount`, `pendingCount`; không có số tài khoản/tên đầy đủ.

Lỗi tải API không được coi như người dùng chưa nhập ngân hàng. Hiển thị trạng thái không kiểm tra được và nút thử lại, không tự hiện form thêm.

Web: homepage freelancer có tóm tắt thu nhập và banner; nút banner mở trang thu nhập ở tab ngân hàng cùng form thêm. Sau lưu sẽ tải lại dữ liệu tài chính/trạng thái. Banner kiểm tra lại khi quay về trang, focus cửa sổ hoặc sau tối đa 60 giây khi vẫn đang mở.

Mobile: homepage có cùng tóm tắt và banner trong chế độ API thật. Khi quay lại homepage/tab được focus, tải lại từ backend. Chế độ mock không giả lập trạng thái ngân hàng hay thu nhập thật. Trang tài chính mặc định mở **Thu nhập**, còn số dư được theo dõi ở tab riêng. Cập nhật ngân hàng nằm cạnh tài khoản đang sử dụng.

## 4. Migration và cách test

Cần khởi động backend để Flyway áp dụng **V38__Freelancer_Income_And_Bank_Account_Revisions.sql**. Không sửa migration V37 đã chạy. V38 thêm liên kết phiên bản ngân hàng và index thống kê thu nhập; không yêu cầu thêm biến `.env` so với cấu hình mã hóa/Pay2S đã có.

Các tình huống kiểm tra:

1. Freelancer chưa có ngân hàng: homepage nhắc cập nhật; API setup trả ACTION_REQUIRED; bấm nút mở đúng form.
2. Thêm ngân hàng: response che số tài khoản; homepage chuyển PENDING, không tiếp tục yêu cầu nhập lần nữa.
3. Admin xác minh: homepage READY và ẩn nhắc bổ sung sau tải lại.
4. Cập nhật số/tên ngân hàng: bản mới được tạo, xác minh lại; bản cũ giữ lịch sử và không bị ghi đè bản mã. Thử gửi lặp đúng body để kiểm tra không tạo trùng.
5. Có lệnh chi chưa kết thúc: PUT bị chặn; POST thêm ngân hàng mới vẫn dùng được.
6. Trước nghiệm thu: thu nhập chưa tăng. Sau nghiệm thu: thù lao tăng ngay dù tiền còn bị giữ 7 ngày. Hoàn cọc không tăng thu nhập.
7. Sau rút tiền: thu nhập không giảm. Kiểm tra tháng/ngày cuối năm theo giờ Việt Nam.
8. Đăng nhập freelancer khác hoặc client/admin: không truy cập được dữ liệu của người trước; API thu nhập/ngân hàng chỉ phục vụ chính freelancer có quyền.
9. Test sandbox: xem riêng số mô phỏng, không mong số thật tăng.

Kiểm thử PostgreSQL trong `FinancePostgresTest` chạy cả V37 và V38 trong schema dùng một lần. Chạy theo biến `FINANCE_TEST_DATABASE_*` tại [hướng dẫn setup](PAY2S_FINANCE_SETUP.md). Môi trường workspace chưa chạy được các kiểm thử này do hạn chế kết nối DB; phải kiểm tra migration và truy vấn thật trên database test trước rollout.
