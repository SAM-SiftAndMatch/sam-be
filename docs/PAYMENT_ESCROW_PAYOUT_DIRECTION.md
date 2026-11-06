# Định hướng thanh toán, escrow, công nợ và chi trả cho SAM

Ngày lập: 06/11/2026.

Cập nhật theo yêu cầu mới: giữ công nợ tối thiểu 7 ngày kể từ nghiệm thu; hỗ trợ ticket bảo trì sau khi dự án đã hoàn thành. Yêu cầu này thay thế định hướng cho rút ngay sau nghiệm thu trong bản trước.

Trạng thái 06/11/2026: đã triển khai mã cho thu Pay2S, công nợ giữ 168 giờ, ticket, đơn bảo trì có phí, ngân hàng mã hóa và chi thủ công có kiểm soát trong ba repository. Chưa triển khai lên production; chưa chạy giao dịch Pay2S thật hoặc kiểm thử tích hợp PostgreSQL trong môi trường này.

**Hướng dẫn cấu hình và quy trình hiện hành:** [PAY2S_FINANCE_SETUP.md](PAY2S_FINANCE_SETUP.md). Mục 3 ghi hiện trạng trước thay đổi; mục 9–10 giữ mô hình khái niệm/API dự kiến để giải thích thiết kế. Mục 17 dưới đây và các controller trong mã là hợp đồng triển khai thực tế.

Phạm vi triển khai: chỉ `sam-be`, `sam-fe`, `sam-mobile`. LastBite là nguồn tham khảo, không sửa LastBite và không mở rộng sang `sam-ai`.

## 1. Quyết định thiết kế đề xuất

**Client nghiệm thu thành công thì SAM tự động ghi nhận công nợ cho freelancer. Admin không cần duyệt lại việc phát sinh công nợ của một nghiệm thu hợp lệ.**

**Công nợ của dự án chưa được chi trả trong 7 ngày sau nghiệm thu.** Trong thời gian này, client được mở ticket lỗi thuộc phạm vi bàn giao ngay trong cuộc hội thoại và workspace dù dự án vẫn có trạng thái `COMPLETED`. Hết 7 ngày chỉ khóa tạo ticket bảo trì miễn phí mới; ticket mở hợp lệ trước hạn vẫn tiếp tục xử lý và giữ công nợ liên quan đến khi có kết quả.

**Chi trả công nợ về ngân hàng là nghiệp vụ riêng.** Giai đoạn đầu, freelancer yêu cầu rút tiền, admin duyệt và hệ thống gọi dịch vụ chi hộ khi đã kết nối. Nếu chưa có dịch vụ chi hộ, hỗ trợ chuyển khoản thủ công kèm đối soát. Sau đó có thể bật tự động duyệt và chi theo chính sách.

**Cần thông tin ngân hàng của freelancer để chi trả về ngân hàng.** Tuy nhiên, thiếu tài khoản ngân hàng không được làm thất bại việc nghiệm thu hoặc ghi nhận công nợ. Chỉ yêu cầu tài khoản nhận tiền đã xác minh ở bước yêu cầu chi trả.

**Cần lịch sử tài chính lưu tại backend.** Tối thiểu gồm lịch sử tiền vào, quyết toán dự án, biến động công nợ, yêu cầu rút tiền, giao dịch chi trả và thao tác quản trị.

Tài liệu dùng bảng, danh sách và khối `text` thay cho Mermaid. Mỗi luồng đều có diễn giải bằng lời, đọc được trong trình xem Markdown cơ bản hoặc mở trực tiếp dưới dạng văn bản, không cần plugin hay ảnh bên ngoài.

## 2. Phân biệt các khái niệm

| Khái niệm | Ý nghĩa trong SAM | Có nghĩa tiền đã về ngân hàng freelancer không? |
| --- | --- | --- |
| Thanh toán đầu vào | Client thanh toán giá trị hợp đồng; freelancer nộp cọc | Không |
| Escrow theo hợp đồng | Sổ nội bộ theo dõi tiền nhận và giữ theo điều kiện hợp đồng | Không |
| Quyết toán dự án | Phân bổ giá trị hợp đồng thành thù lao, phí nền tảng và hoàn cọc | Không |
| Công nợ phải trả freelancer | Số tiền SAM có nghĩa vụ trả freelancer sau quyết toán | Không |
| Công nợ khả dụng | Phần công nợ đủ điều kiện tạo yêu cầu chi trả | Không |
| Công nợ chờ hết hạn bảo trì | Đã quyết toán nhưng chưa đủ 7 ngày sau nghiệm thu | Không |
| Công nợ bị giữ do ticket | Đang có issue bảo trì hoặc tranh chấp chưa kết thúc | Không |
| Tiền đang chi | Phần công nợ đã được giữ riêng cho một yêu cầu rút | Chưa chắc |
| Chi trả thành công | Đã xác nhận giao dịch chuyển tiền thành công | Có, theo kết quả chuyển tiền đã đối soát |

Trong tài liệu, “escrow” mô tả luồng giữ và phân bổ tiền theo hợp đồng trong phần mềm. Một bản ghi escrow trong cơ sở dữ liệu không tự chứng minh tiền đang nằm trong tài khoản phong tỏa của ngân hàng hoặc nhà cung cấp.

Không dùng cùng một trạng thái `RELEASED` hoặc một nhãn “Đã thanh toán” cho cả quyết toán nội bộ và chuyển khoản thực tế.

## 3. Hiện trạng đã khảo sát trước triển khai

### 3.1. LastBite

Mã tham khảo nằm tại `/home/tinacoding/Documents/projects/LastBite`.

| Thành phần | Hành vi đã thấy trong mã |
| --- | --- |
| Thanh toán | Tạo link PayOS; xác minh webhook, mã đơn và số tiền trước khi ghi nhận |
| Sổ giao dịch | Theo dõi tiền nhận, escrow, doanh thu nền tảng, công nợ merchant và hoàn tiền |
| Hoàn thành đơn | Tự ghi nhận công nợ merchant khi hoàn tất nhận hàng hoặc xử lý no-show |
| Thời gian chờ | Công nợ được đặt thời điểm khả dụng sau 3 ngày |
| Đợt chi trả | Admin tạo bản nháp đợt tuần, duyệt và khởi tạo payout |
| Chuyển tiền | Có adapter gọi API payout, idempotency key và phương án đánh dấu kết quả thủ công |

Phần nên học là cách tách tiền nhận, tiền đang giữ, công nợ và chi trả. Không cần sao chép toàn bộ lịch T+3, đợt chi theo tuần hoặc lựa chọn nhà cung cấp.

Không coi mã tham khảo là một hệ thống tài chính đã được kiểm toán đầy đủ. Khi triển khai SAM phải kiểm tra riêng việc giảm công nợ khi chi thành công, chống chi trùng, đối soát kết quả và xử lý timeout; không sao chép cách coi mọi lỗi gọi API là chuyển tiền thất bại.

### 3.2. SAM

| Thành phần | Hiện trạng |
| --- | --- |
| Chính sách funding | Client thanh toán 100% giá trị hợp đồng, freelancer đóng cọc 2% |
| Nhận tiền | VNPay; có IPN xác minh chữ ký và số tiền |
| Nghiệm thu | `acceptDelivery` gọi quyết toán trong cùng giao dịch nghiệp vụ |
| Quyết toán | `SandboxSettlementServiceImpl` tính thù lao 90%, phí 10% và hoàn cọc |
| Trạng thái quyết toán | `SIMULATED_COMPLETED`; phản hồi có `sandbox=true` |
| Ví | Có tài khoản ví và lịch sử ghi có sandbox; chưa có luồng rút ngân hàng thực tế |
| Web | Có API số dư; trang thu nhập và thao tác rút tiền còn dùng dữ liệu mẫu |
| Mobile | Repository hiện được nối vào mock; cần adapter API thật cho nghiệp vụ này |

Hai điểm cần xử lý trước khi vận hành tiền thật:

1. `confirmFunding` hiện có thể ghi nhận tiền vào từ yêu cầu frontend, chưa xác minh kết quả với nhà cung cấp ngay trong phương thức. Kiểm tra mã giao dịch và số tiền do frontend gửi không đủ chứng minh thanh toán thành công.
2. API admin `releaseEscrow` hiện chỉ đổi trạng thái payment. Cần đưa đường xử lý phục hồi này qua cùng nghiệp vụ tài chính để không lệch sổ hoặc bỏ sót công nợ.

Tài liệu payment cũ còn mô tả cơ chế frontend tự báo đã thanh toán để thay IPN. Cơ chế đó không được dùng để tạo công nợ có thể chi trả bằng tiền thật. Khi triển khai phải cập nhật tài liệu cũ để tránh hai hướng dẫn mâu thuẫn.

## 4. Luồng nghiệp vụ tổng thể

```text
Hai bên ký hợp đồng
  |
Client thanh toán 100%; freelancer đóng cọc 2%
  |
Backend xác minh kết quả từ nhà cung cấp
  |
Ghi nhận tiền đang giữ theo hợp đồng
  |
Dự án thực hiện và freelancer bàn giao
  |
Client nghiệm thu hợp lệ
  |
TỰ ĐỘNG quyết toán và ghi công nợ
  |
Giữ công nợ trong thời hạn bảo trì 7 ngày
  |
Hết hạn VÀ không còn ticket hoặc chặn chi chưa xử lý
  |
Công nợ đủ điều kiện yêu cầu chi trả
  |
Freelancer chọn tài khoản nhận tiền và yêu cầu rút
  |
Giữ riêng số tiền của yêu cầu rút
  |
Admin duyệt hoặc hệ thống tự duyệt theo chính sách
  |
Chuyển khoản qua API hoặc thực hiện thủ công
  |
Đối soát kết quả và cập nhật lịch sử
```

Diễn giải: tiền được giữ trong thời gian làm dự án. Nghiệm thu làm phát sinh công nợ tự động nhưng chưa được rút trong 7 ngày bảo trì. Hết hạn mà còn ticket mở đúng hạn chưa giải quyết thì tiếp tục giữ khoản liên quan. Freelancer chỉ có thể rút công nợ từ các dự án đã đủ điều kiện. Tài khoản ngân hàng, duyệt chi và chuyển tiền thuộc bước chi trả, không phải điều kiện để hoàn thành dự án.

### 4.1. Nhận tiền và khởi động dự án

1. Backend tính số tiền theo hợp đồng; frontend không quyết định số tiền phải nộp.
2. Tạo payment và lần thử thanh toán có mã tham chiếu riêng.
3. Nhận IPN đã xác minh chữ ký, mã giao dịch, đơn vị nhận, số tiền, loại tiền và trạng thái thành công theo tài liệu nhà cung cấp.
4. Lưu sự kiện nhà cung cấp và hạch toán tiền nhận đúng một lần.
5. Khi đủ tiền hợp đồng và cọc theo chính sách, tự chuyển dự án sang thực hiện.

Return URL chỉ điều hướng giao diện. Khi quay lại ứng dụng, frontend đọc trạng thái từ backend; có thể cập nhật qua sự kiện và tải lại trạng thái để phục hồi khi mất kết nối. Nếu IPN chậm, hiển thị “Đang xác nhận thanh toán” và đối soát từ backend.

IPN trùng không được cộng tiền lần nữa. Nếu có nhiều giao dịch tiền thật thành công cho cùng một nghĩa vụ thanh toán, phải ghi nhận từng giao dịch và đưa phần thu thừa vào xử lý riêng; không bỏ qua khoản tiền đã nhận.

### 4.2. Nghiệm thu và quyết toán tự động

Trong một giao dịch cơ sở dữ liệu:

1. Xác minh client là người có quyền nghiệm thu và bàn giao còn hợp lệ.
2. Khóa dữ liệu liên quan; kiểm tra chưa quyết toán, funding đã được xác nhận và không có chặn nghiệp vụ.
3. Chuyển bàn giao sang đã nghiệm thu, hoàn thành hợp đồng và dự án.
4. Tạo quyết toán với số tiền, tỷ lệ phí và quy tắc làm tròn đã chốt cho hợp đồng.
5. Chuyển nghĩa vụ tiền đang giữ sang công nợ freelancer và doanh thu nền tảng; ghi riêng hoàn cọc.
6. Lưu thời điểm nghiệm thu bất biến, hạn bảo trì sau 7 ngày và thời điểm sớm nhất công nợ có thể đủ điều kiện chi trả; bản thân đến hạn chưa đủ nếu còn ticket hoặc chặn chi.
7. Sau commit, phát thông báo và cập nhật cache; dùng cơ chế sự kiện bền vững nếu cần retry.

Không gọi API chuyển tiền ngân hàng trong giao dịch nghiệm thu. Nếu ngân hàng lỗi, dự án vẫn đã nghiệm thu và khoản phải trả vẫn tồn tại.

Yêu cầu nghiệm thu lặp lại phải trả về kết quả đã có khi thích hợp, không tạo quyết toán thứ hai. Dùng cả khóa giao dịch và ràng buộc duy nhất, không chỉ kiểm tra ở giao diện.

### 4.3. Thời điểm công nợ khả dụng

- Ghi nhận công nợ ngay sau nghiệm thu, kể cả chưa có tài khoản ngân hàng.
- Quy tắc theo yêu cầu: giữ công nợ ít nhất 7 ngày kể từ thời điểm backend ghi nhận nghiệm thu hoàn thành. Không có nút admin duyệt chi sớm để bỏ qua mốc này.
- `availableAt` là mốc sớm nhất được xét khả dụng, không phải lệnh tự giải ngân. Điều kiện phải gồm đến hạn, không còn ticket chặn chi và không còn khoản giữ/tranh chấp khác.
- Đề xuất giữ cả thù lao và khoản hoàn cọc của dự án trong cùng thời hạn. Chính sách này cần được ghi trong điều kiện hợp đồng trước khi áp dụng, vì trước đây hoàn cọc được ghi có ngay trong sandbox.
- Việc mở hoặc sửa ticket không đặt lại 7 ngày bảo trì cho cả dự án. Ticket hợp lệ có thể làm thời gian giữ tiền kéo dài qua mốc này.
- Chỉ giữ tiền của đúng dự án liên quan; không khóa toàn bộ ví freelancer hoặc nguồn tiền đã đủ điều kiện từ dự án khác.
- Hiển thị riêng “Chờ hết hạn bảo trì”, “Đang giữ do ticket” và “Khả dụng”. Không cam kết ngày nhận tiền chắc chắn khi ticket còn mở.

### 4.4. Đánh giá phương án bảo trì 7 ngày

Phương án phù hợp với SAM vì khách hàng có thời gian sử dụng sản phẩm sau nghiệm thu, còn freelancer biết trước thời điểm sớm nhất được rút. Nghiệm thu xác nhận bàn giao; bảo trì xử lý lỗi trong phạm vi đã cam kết, không làm dự án quay về giai đoạn thực hiện ban đầu.

Ba điều kiện cần thiết để tránh giữ tiền thiếu căn cứ:

1. Phân biệt lỗi của phạm vi đã bàn giao với yêu cầu tính năng mới, thay đổi của client hoặc sự cố dịch vụ bên thứ ba.
2. Tách thời hạn được mở ticket khỏi hạn phản hồi và hạn sửa của từng ticket.
3. Ticket có quy trình xác nhận kết quả, xử lý im lặng và chuyển admin khi quá hạn; không để một bên tự giữ tiền hoặc tự giải phóng tiền vô thời hạn.

Quy tắc 7 ngày là yêu cầu của chủ dự án SAM. Các mức SLA 24 giờ, 48 giờ, thời gian sửa từng ticket và phương án chế tài bên dưới là đề xuất, cần chốt trước khi đưa vào điều khoản vận hành.

### 4.5. Đồng hồ bảo trì và ranh giới thời gian

```text
T0: backend ghi nhận nghiệm thu thành công
T1: T0 + 168 giờ

T0 <= thời điểm tạo ticket < T1:
  Cho mở ticket bảo trì miễn phí trong phạm vi hợp đồng

Thời điểm tạo ticket >= T1:
  Khóa mở ticket bảo trì miễn phí mới
  Cho gửi yêu cầu bảo trì có báo giá riêng

Từ T1, nếu còn ticket hợp lệ chưa kết thúc:
  Tiếp tục xử lý ticket và giữ tiền liên quan
```

Diễn giải: dùng 7 ngày liên tục, tương đương 168 giờ, không làm tròn đến cuối ngày và không tự bỏ qua cuối tuần. Ví dụ nghiệm thu lúc 14:30 ngày 06/11/2026, giờ Việt Nam, thì hạn mở ticket là trước 14:30 ngày 13/11/2026. Đúng 14:30 ngày 13/11 trở đi không được tạo ticket bảo trì miễn phí mới.

- Backend lưu `acceptedAt` và `warrantyEndsAt` theo thời điểm UTC, hiển thị giờ địa phương kèm ngày giờ cụ thể; không chỉ hiện đếm ngược.
- `acceptedAt` được ghi một lần trong giao dịch nghiệm thu. Nhấn lại nút, cập nhật mô tả, đổi trạng thái ticket hoặc bàn giao bản vá không thay đổi mốc này.
- Hạn và phiên bản chính sách được chụp theo hợp đồng. Thay cấu hình sau này không rút ngắn thời hạn của dự án đã nghiệm thu.
- Quyền tạo ticket tính theo thời điểm backend kiểm tra và ghi nhận hợp lệ sau khi khóa dữ liệu. Mở form hoặc gửi bản nháp trước hạn chưa có nghĩa đã tạo ticket thành công.
- Job đến hạn, tạo ticket và phân bổ công nợ phải đồng bộ khóa hoặc kiểm soát phiên bản trên cùng dữ liệu dự án; tránh vừa tạo ticket đúng hạn vừa chuyển tiền sang khả dụng.
- Cần thông báo gần hết hạn để client không đợi đến giây cuối. Nếu hệ thống ngừng hoạt động đúng hạn, admin có luồng xem xét sự cố có dấu vết; không cho người dùng tự sửa thời gian hoặc lùi ngày ticket.

### 4.6. Trạng thái dự án và nút ticket trong chat/workspace

Giữ `jobStatus=COMPLETED` và `contractStatus=COMPLETED`. Thêm dữ liệu bảo trì riêng thay vì đổi dự án về `IN_PROGRESS` hoặc gọi lại luồng nghiệm thu ban đầu.

| Trạng thái bảo trì đề xuất | Ý nghĩa |
| --- | --- |
| `ACTIVE` | Còn trong cửa sổ 7 ngày, client có quyền tạo ticket mới |
| `EXPIRED_WITH_OPEN_ISSUES` | Đã hết cửa sổ, còn ticket miễn phí hợp lệ chưa kết thúc |
| `CLOSED` | Hết cửa sổ và không còn ticket bảo trì hoặc phân loại đang chờ xử lý |

Các trạng thái trên độc lập với trạng thái công nợ và tranh chấp. `CLOSED` không tự chứng minh tiền đủ điều kiện chi nếu còn chặn tài chính khác. Cờ quyền được tính ở backend, không chỉ suy ra từ enum hoặc đồng hồ frontend.

Trong header cuộc hội thoại và trang chi tiết/workspace:

- Badge “Đã hoàn thành” luôn giữ nguyên.
- Trong hạn: hiện “Bảo trì đến: [ngày giờ]” và nút **Báo lỗi trong thời gian bảo trì** cho client của hợp đồng.
- Bên cạnh có **Ticket bảo trì (n)** dẫn đến cùng danh sách, cùng dữ liệu; tạo từ chat hay workspace không tạo hai hệ thống ticket khác nhau.
- Hết hạn: vô hiệu hóa nút mở ticket miễn phí kèm lý do, thay bằng **Yêu cầu bảo trì có phí**. Vẫn cho mở ticket cũ để đọc, trả lời, xem bản vá hoặc yêu cầu admin hỗ trợ.
- Freelancer thấy ticket cần phản hồi, hạn sửa và lý do công nợ bị giữ. Client không có quyền thay đổi số tiền giữ hoặc thời hạn trực tiếp.
- Khi tạo và cập nhật ticket, gửi tin hệ thống trong chat có mã ticket, trạng thái, hạn xử lý và liên kết; tin nhắn chat thông thường không tự trở thành ticket.

Workspace và kênh trao đổi của hai bên phải tiếp tục hoạt động khi dự án hoàn thành và trong lúc xử lý ticket cũ. Không khóa quyền bình luận chỉ vì đã qua ngày thứ 7. Thông tin đăng nhập, khóa triển khai hoặc dữ liệu nhạy cảm không được đưa vào thông báo công khai hay log.

Ghi nhận khảo sát bổ sung: `ProjectWorkflowPanel` và phần preview trong `WorkspaceChatView` đã cho xem workflow với cả `IN_PROGRESS` và `COMPLETED`. Đây là điểm tích hợp thuận lợi cho nút bảo trì; backend chưa có module ticket theo thiết kế này.

### 4.7. Phạm vi bảo trì miễn phí

| Tình huống | Hướng xử lý đề xuất |
| --- | --- |
| Chức năng không hoạt động đúng SRS/tiêu chí nghiệm thu đã chốt | Ticket bảo trì miễn phí nếu mở trong hạn |
| Lỗi tái hiện từ phiên bản đã nghiệm thu | Miễn phí trong phạm vi bảo trì; lưu bằng chứng và bản vá |
| Thêm tính năng, sửa thiết kế theo sở thích mới | Yêu cầu thay đổi có báo giá; không tự buộc freelancer làm miễn phí |
| Client sửa mã, cấu hình hoặc nhà cung cấp ngoài bị lỗi | Cần phân loại nguyên nhân; có thể báo giá nếu ngoài trách nhiệm đã cam kết |
| Chưa đủ thông tin hoặc hai bên bất đồng nguyên nhân | Giữ tạm theo ticket đã mở đúng hạn và chuyển phân loại/admin; không tự thu phí |
| Báo lỗi mới sau hạn bảo trì | Tạo yêu cầu dịch vụ có phí; không tạo ticket miễn phí bằng cách lùi ngày |
| Lỗi cũ mở đúng hạn nhưng vẫn chưa sửa sau hạn | Tiếp tục xử lý miễn phí; không chuyển thành yêu cầu có phí chỉ vì đã qua ngày thứ 7 |

Ticket cần có tiêu đề, mô tả, bước tái hiện, kết quả mong đợi/thực tế, mức ảnh hưởng, bằng chứng và tham chiếu yêu cầu hoặc bàn giao liên quan. Không được tự gán mọi phản ánh là lỗi của freelancer chỉ vì client mở ticket.

### 4.8. Vòng đời ticket và hạn xử lý

| Trạng thái đề xuất | Ý nghĩa | Chặn công nợ dự án? |
| --- | --- | --- |
| `OPEN` | Mở đúng hạn, chờ phản hồi và phân loại | Có, tạm thời |
| `IN_PROGRESS` | Đang xử lý lỗi thuộc phạm vi | Có |
| `WAITING_CLIENT` | Chờ thông tin hoặc kiểm tra từ client | Có, nhưng có hạn và nhắc việc |
| `FIX_SUBMITTED` | Freelancer đã gửi bản vá, chờ kiểm tra | Có |
| `DISPUTED` | Bất đồng, quá hạn hoặc chờ admin quyết định | Có |
| `RESOLVED` | Đã xác nhận xử lý xong | Không còn chặn từ ticket này |
| `OUT_OF_SCOPE` | Có kết luận ngoài phạm vi miễn phí | Không còn chặn từ ticket này |
| `WITHDRAWN` | Client rút ticket hợp lệ | Không còn chặn từ ticket này |

Một lượt thảo luận hoặc đề nghị ngoài phạm vi của freelancer không đủ để tự chuyển `OUT_OF_SCOPE`; cần client đồng ý hoặc admin quyết định. Freelancer gửi bản vá cũng không được tự chuyển thẳng sang `RESOLVED` để lấy tiền. Ticket đã có quyết định điều chỉnh tiền chỉ đóng khi việc điều chỉnh được ghi sổ thành công.

Đề xuất phân biệt ba đồng hồ:

1. **Hạn mở:** cố định 7 ngày kể từ nghiệm thu cho cả dự án.
2. **Hạn phản hồi:** ví dụ 24 giờ từ lúc tạo ticket, để freelancer xác nhận tiếp nhận hoặc yêu cầu thông tin.
3. **Hạn sửa:** ví dụ 7 ngày từ lúc tạo từng ticket, không phải tất cả ticket đều phải xong trước ngày thứ 7 của dự án. Lỗi nghiêm trọng có thể có thời hạn ngắn hơn theo điều kiện đã chốt.

Ví dụ ticket mở ngày thứ 6 có thể có hạn sửa vào ngày thứ 13. Từ ngày thứ 7 dự án không nhận ticket miễn phí mới, nhưng ticket đó vẫn miễn phí và tiền vẫn bị giữ. Không tính thêm 7 ngày mỗi lần trao đổi, đổi nhãn hoặc gửi bản vá.

Nếu cần gia hạn hoặc tạm dừng SLA vì thiếu thông tin, phải ghi lý do, mốc mới và bên chấp thuận hoặc quyết định admin. Không cho freelancer tự chuyển `WAITING_CLIENT` để vô hiệu hóa hạn sửa; bản thân trạng thái đó không tự đặt lại đồng hồ.

Khi có bản vá, client có khoảng kiểm tra đề xuất 48 giờ và được nhắc trước hạn. Ở giai đoạn đầu, client im lặng quá hạn sẽ chuyển admin xem xét thay vì tự đóng ngay. Admin có thể xác nhận đã sửa khi có bằng chứng, yêu cầu bổ sung hoặc kết luận tranh chấp; vẫn lưu toàn bộ dấu vết. Giai đoạn sau mới cân nhắc cơ chế tự xác nhận với điều kiện và quyền phản hồi được quy định rõ.

Sau T1, client có thể từ chối bản vá của ticket còn đang xử lý hoặc yêu cầu kiểm tra lại trong thời hạn của quyết định đã có. Việc này không phải ticket mới và chỉ áp dụng cùng lỗi gốc. Ticket đã đóng cuối cùng không cho tùy ý mở lại để đóng băng khoản đã khả dụng; bất đồng sau đó đi qua admin xem xét bằng chứng, không tự động thu hồi tiền đã chuyển ngân hàng.

### 4.9. Quá hạn, tranh chấp và chống lạm dụng

| Tình huống | Xử lý đề xuất |
| --- | --- |
| Freelancer không phản hồi đúng SLA | Nhắc việc, ghi vi phạm và đưa vào hàng đợi admin |
| Quá hạn sửa mà ticket chưa kết thúc | Đánh dấu `overdueAt`, chuyển `DISPUTED`, tiếp tục giữ tiền dự án |
| Client không cung cấp dữ liệu hoặc không kiểm tra bản vá | Nhắc việc, ghi hạn phản hồi; quá hạn chuyển admin xem xét |
| Ticket trùng hoặc cố biến tính năng mới thành lỗi | Liên kết/đánh dấu trùng, phân loại có lý do; không gia hạn cửa sổ 7 ngày |
| Freelancer xác nhận không thể khắc phục | Hai bên thỏa thuận hoặc admin xử lý phương án tài chính và bàn giao |

Đề xuất admin bắt đầu xem xét trong 48 giờ từ khi chuyển cấp; đây là SLA vận hành, không có nghĩa hết 48 giờ thì tự nhả tiền hoặc tự phạt. Hệ thống cần cảnh báo các trường hợp bị giữ quá lâu và người chịu trách nhiệm theo dõi.

Các phương án admin có thể xem xét: gia hạn có mốc rõ, yêu cầu hoàn thiện, xác nhận đã xử lý, kết luận ngoài phạm vi, thỏa thuận giảm thù lao/hoàn tiền một phần hoặc chuyển người khác sửa với kinh phí được thống nhất. Quyết định có lý do, bằng chứng, thông báo cho hai bên và thời hạn phản hồi khiếu nại.

Không tự phạt cố định, tịch thu cọc hoặc hoàn toàn bộ tiền chỉ vì ticket quá 7 ngày chưa xong. Cần xác định phạm vi lỗi, trách nhiệm, mức ảnh hưởng và điều khoản áp dụng. Chế tài ảnh hưởng tiền phải được chốt trước trong hợp đồng/chính sách và thực hiện qua bút toán điều chỉnh; không sửa số dư trực tiếp.

Đề xuất giai đoạn đầu giữ toàn bộ công nợ chưa trả của đúng dự án khi còn ticket chặn chi để đơn giản và đáp ứng yêu cầu. Không giữ tiền dự án khác. Admin có thể kết luận ticket ngoài phạm vi hoặc giải quyết tranh chấp, nhưng không được cho chi trước khi hết 168 giờ. Nếu sau này hỗ trợ giữ một phần, cần cơ chế định lượng và phê duyệt riêng, không cho client tự nhập số tiền giữ.

Việc ghi nhận phí nền tảng 10% không có nghĩa được tiêu hết phần nguồn tiền có thể phải dùng khi hoàn tiền. Phải xác định nguồn hoàn tiền, điều chỉnh doanh thu/phí và công nợ còn lại khi admin chấp thuận xử lý tài chính. Tổng hoàn và chi không được vượt tiền nguồn hợp lệ, không đền bù hai lần cho cùng sự kiện.

### 4.10. Điều kiện mở khóa công nợ

Backend chỉ cho chuyển phần công nợ dự án sang khả dụng khi đồng thời thỏa mãn:

1. Đã quyết toán và nguồn thanh toán hợp lệ, không phải dữ liệu sandbox dùng để chi tiền thật.
2. Đã qua `warrantyEndsAt` (ít nhất 168 giờ từ nghiệm thu).
3. Không còn ticket có trạng thái chặn chi hoặc phân loại/phản hồi đang chờ xử lý cuối cùng.
4. Không còn giữ do tranh chấp, kiểm tra hoặc quyết định tài chính chưa thực hiện xong.
5. Số tiền còn lại khớp sổ sau mọi điều chỉnh được duyệt.

Giải quyết hết ticket vào ngày thứ 3 vẫn phải chờ đến hết ngày thứ 7. Nếu ngày thứ 7 còn issue thì tiếp tục giữ; khi issue kết thúc sau hạn và không có chặn khác, khoản còn lại mới đủ điều kiện. Không bắt client bấm lại nghiệm thu toàn dự án hay tự đặt thêm một đợt bảo trì 7 ngày.

| Trường hợp | Tạo ticket miễn phí mới? | Công nợ dự án |
| --- | --- | --- |
| Ngày thứ 2, chưa có ticket | Có, nếu đúng phạm vi | Chưa được rút |
| Ticket đã xử lý xong ngày thứ 3 | Còn được mở lỗi hợp lệ khác trước hạn | Vẫn chờ hết 168 giờ |
| Đã đủ 168 giờ, không còn ticket hoặc chặn khác | Không | Được xét chuyển sang khả dụng |
| Ticket mở ngày thứ 6, ngày thứ 8 vẫn chưa xong | Không mở ticket mới; xử lý tiếp ticket cũ | Tiếp tục giữ |
| Ticket đó xử lý xong ngày thứ 10, không còn chặn khác | Không | Được xét chuyển sang khả dụng, không chờ thêm 7 ngày |
| Yêu cầu lỗi mới ngày thứ 9, không có ticket cũ liên quan | Gửi yêu cầu bảo trì có phí | Không tự giữ lại công nợ gốc đã đủ điều kiện |

Worker xét đến hạn phải idempotent, có thể chạy lại khi dừng giữa chừng. Đồng thời kiểm tra lại điều kiện khi phân bổ yêu cầu rút, duyệt và trước gửi lệnh, kể cả chi thủ công; không chỉ tin số dư đã cache. Quyết định mở khóa, tạo ticket và chuyển cấp phải có lịch sử.

### 4.11. Yêu cầu bảo trì có phí sau hạn

Sau cửa sổ miễn phí, vẫn cho client gửi **Yêu cầu bảo trì có phí** trong cùng chat/workspace, nhưng là loại yêu cầu riêng. Freelancer có quyền xem xét và báo giá, không bị tự gán nghĩa vụ hoặc tự chốt phí.

```text
Client gửi yêu cầu bảo trì có phí
  |
Freelancer đề xuất phạm vi, giá và hạn xử lý
  |
Client đồng ý và thanh toán đã được xác minh
  |
Tạo công việc bảo trì có hợp đồng/đơn hàng riêng
  |
Thực hiện, nghiệm thu và quyết toán riêng
```

Không rút thêm tiền hoặc tính phí chỉ từ việc client nhấn gửi yêu cầu. Yêu cầu báo giá không giữ lại khoản công nợ của dự án gốc đã đủ điều kiện hoặc đã chi. Thanh toán và công nợ mới phải tách khỏi hợp đồng gốc, chỉ liên kết để truy vết.

Lỗi đã được mở đúng hạn và chưa kết thúc không được chuyển sang có phí để tránh trách nhiệm. Khi client cho rằng yêu cầu sau hạn thực chất là lỗi cũ bị xử lý sai, cho gửi bằng chứng tới admin xem xét; không vì vậy mở lại tự do mọi ticket miễn phí hoặc tự thu hồi giao dịch ngân hàng đã hoàn tất.

Điều kiện bảo trì lại cho công việc sửa có phí phải được chốt trong báo giá/hợp đồng riêng. Không tự lặp vô hạn cửa sổ 7 ngày sau từng bản vá miễn phí. Nếu cung cấp bảo trì định kỳ, cần gói dịch vụ và chu kỳ thanh toán riêng thay vì sửa nghĩa vụ của dự án đã hoàn thành.

## 5. Tài khoản ngân hàng của freelancer

### 5.1. Thời điểm yêu cầu thông tin

Cho phép thêm tài khoản từ hồ sơ hoặc trang thu nhập. Khi chưa có tài khoản, freelancer vẫn xem được công nợ và lịch sử; hành động rút tiền dẫn tới bước thêm và xác minh tài khoản.

Không bắt buộc khai ngân hàng ngay khi đăng ký, nộp proposal hoặc nghiệm thu.

### 5.2. Thông tin đề xuất lưu

| Trường | Mục đích |
| --- | --- |
| ID tài khoản nhận tiền | Tham chiếu nội bộ, không dùng số tài khoản làm ID |
| Chủ sở hữu trong SAM | Gắn với freelancer đăng nhập; không tin `userId` do frontend gửi |
| Mã ngân hàng / BIN | Định danh ngân hàng để gửi nhà cung cấp |
| Tên ngân hàng | Hiển thị dễ hiểu |
| Số tài khoản | Chuỗi ký tự, giữ nguyên số 0 ở đầu; lưu mã hóa |
| Tên người thụ hưởng | Thông tin được khai báo hoặc xác minh qua nhà cung cấp |
| Bốn ký tự cuối | Hiển thị che số tài khoản trong danh sách |
| Tài khoản mặc định | Gợi ý khi tạo yêu cầu rút |
| Trạng thái xác minh | Chờ duyệt, đã xác minh, bị từ chối hoặc ngừng sử dụng |
| Dấu vết xác minh | Phương thức, người duyệt, thời điểm, lý do và tham chiếu cần thiết |
| Phiên bản và thời điểm cập nhật | Kiểm soát thay đổi thông tin người nhận |

Giai đoạn đầu chỉ cần tài khoản ngân hàng nội địa nhận VND. Không cần thông tin thẻ, CVV, mật khẩu hay OTP ngân hàng của freelancer.

### 5.3. Xác minh và thay đổi tài khoản

```text
Thêm tài khoản -> PENDING_VERIFICATION
PENDING_VERIFICATION -> VERIFIED hoặc REJECTED
VERIFIED -> DISABLED khi ngừng sử dụng
Sửa thông tin người nhận -> tạo phiên bản mới và xác minh lại
```

Diễn giải: tài khoản mới chưa dùng để chi trả ngay. Xác minh tự động chỉ triển khai nếu nhà cung cấp hỗ trợ kiểm tra người thụ hưởng và quyền tích hợp thực tế cho phép. Nếu chưa có, admin xác minh theo quy trình có lưu bằng chứng. Mã ngân hàng và định dạng số tài khoản hợp lệ không đồng nghĩa đã xác minh chủ tài khoản.

Đề xuất tài khoản nhận tiền đứng tên freelancer theo hồ sơ đã xác minh. Trường hợp tên không khớp cần luồng xem xét có lý do, không tự duyệt chỉ vì số tài khoản nhận được tiền.

Không sửa đè thông tin người nhận của một lệnh chi đã tạo. Mỗi yêu cầu rút phải lưu bản chụp ngân hàng, số tài khoản, tên thụ hưởng và phiên bản xác minh tại thời điểm tạo. Đổi tài khoản mặc định chỉ ảnh hưởng yêu cầu mới.

Khi duyệt hoặc gửi lệnh, kiểm tra tài khoản có bị vô hiệu hóa hoặc chặn không. Nếu có, dừng lệnh chưa gửi để xử lý; không âm thầm đổi sang tài khoản khác. Tài khoản đã gắn giao dịch chỉ nên ngừng sử dụng, không xóa mất lịch sử.

### 5.4. Quyền xem và bảo vệ dữ liệu

- Freelancer chỉ quản lý tài khoản của mình; admin được cấp quyền phù hợp mới được xem dữ liệu phục vụ chi trả.
- Danh sách thông thường trả số tài khoản đã che. Chỉ cấp thông tin đầy đủ cho chức năng có nhu cầu cụ thể và lưu dấu vết truy cập cần thiết.
- Mã hóa số tài khoản và bản chụp người nhận; quản lý khóa ngoài mã nguồn và dữ liệu nghiệp vụ.
- Không ghi số tài khoản đầy đủ, khóa API hoặc thông tin xác thực vào log, thông báo, file xuất thông thường.
- Chứng từ chứa dữ liệu nhạy cảm phải có kiểm soát truy cập; không dùng URL công khai vĩnh viễn.

## 6. Yêu cầu rút tiền và chi trả

### 6.1. Tạo yêu cầu rút

1. Freelancer chọn số tiền và tài khoản đã xác minh.
2. Backend kiểm tra tiền khả dụng theo từng dự án: đã hết 7 ngày bảo trì, không còn ticket chặn chi và không có giữ khác; kiểm tra thêm số tiền tối thiểu/tối đa và phí nếu đã có chính sách.
3. Khóa số dư liên quan, tạo yêu cầu với khóa chống trùng và giữ riêng số tiền trong cùng giao dịch.
4. Lưu bản chụp người nhận, khoản công nợ được dùng và số tiền thực nhận.
5. Trả mã yêu cầu để theo dõi; chưa hiển thị “Chuyển tiền thành công”.

Một yêu cầu có thể dùng công nợ từ nhiều dự án đã đủ điều kiện; một khoản công nợ có thể được rút từng phần. Cần bảng phân bổ để truy ngược khoản chi về từng quyết toán. Có thể phân bổ theo công nợ khả dụng cũ nhất trước, với thứ tự ổn định. Tuyệt đối không dùng tiền dự án đang bảo trì để bù vào số rút dù tổng công nợ cả ví còn cao.

### 6.2. Ba chế độ chi trả

| Chế độ | Cách thực hiện | Vai trò admin |
| --- | --- | --- |
| Thủ công | Admin duyệt; người vận hành chuyển khoản, nhập mã tham chiếu và chứng từ | Thực hiện và đối soát chuyển khoản |
| Qua API, có duyệt | Admin duyệt; worker gửi lệnh chi qua nhà cung cấp | Duyệt và xử lý ngoại lệ |
| Tự động theo chính sách | Hệ thống kiểm tra điều kiện, tự duyệt và gửi lệnh | Giám sát, xử lý trường hợp bị giữ hoặc chưa rõ kết quả |

Đích ban đầu đề xuất là qua API có admin duyệt; có thể vận hành chế độ thủ công trước khi đủ cấu hình và nguồn tiền của dịch vụ chi hộ. Công nợ luôn tự động ở cả ba chế độ.

Không cho cùng một yêu cầu được chi đồng thời qua API và chuyển khoản thủ công. Khi chuyển phương thức phải bảo đảm lệnh trước chưa gửi hoặc đã xác nhận không chuyển tiền.

### 6.3. Trạng thái yêu cầu chi đề xuất

| Trạng thái | Ý nghĩa | Cách xử lý số tiền đã giữ |
| --- | --- | --- |
| `REQUESTED` | Đã yêu cầu, chờ duyệt | Tiếp tục giữ |
| `APPROVED` | Được duyệt, chờ gửi lệnh | Tiếp tục giữ |
| `PROCESSING` | Lệnh đã gửi hoặc đang chuyển tiền | Tiếp tục giữ |
| `RECONCILIATION_REQUIRED` | Chưa xác định chắc chắn kết quả, ví dụ timeout sau khi gửi | Tiếp tục giữ; đối soát trước khi chi lại |
| `PAID` | Đã xác nhận chuyển tiền thành công | Ghi giảm công nợ và nguồn tiền đúng một lần |
| `FAILED` | Có kết quả chắc chắn giao dịch không chuyển tiền | Gỡ giữ cho lệnh đúng một lần; kiểm tra các chặn khác trước khi về khả dụng |
| `REJECTED` | Admin từ chối trước khi gửi lệnh | Gỡ giữ cho lệnh, kiểm tra chặn khác và lưu lý do |
| `CANCELLED` | Đã hủy hợp lệ trước khi gửi lệnh | Gỡ giữ cho lệnh; chỉ về khả dụng nếu không có chặn khác |

Luồng thông thường: `REQUESTED` rồi `APPROVED`, `PROCESSING`, cuối cùng `PAID`.

Luồng chưa rõ kết quả: từ `PROCESSING` chuyển `RECONCILIATION_REQUIRED`, sau đó chỉ sang `PAID` hoặc `FAILED` khi có bằng chứng phù hợp.

Không cho hủy lệnh đã gửi chỉ bằng thao tác đổi trạng thái nội bộ. Nếu nhà cung cấp hỗ trợ hủy, phải xác nhận hủy thực tế trước khi giải phóng số tiền.

Với thiết kế này, yêu cầu đã `FAILED` là kết thúc và khoản giữ riêng cho lệnh được giải phóng; số tiền quay về nhóm phù hợp sau khi kiểm tra các chặn khác. Nếu người dùng muốn thử lại, tạo yêu cầu mới liên kết yêu cầu cũ và kiểm tra lại điều kiện công nợ. Retry truyền tải khi chưa rõ kết quả phải dùng cùng danh tính lệnh và khóa idempotency, không tạo một khoản chi mới.

### 6.4. Chi trả thủ công và đối soát

Admin cần ghi mã giao dịch ngân hàng, thời điểm chuyển, số tiền, tài khoản nguồn tham chiếu, người nhận và chứng từ. Các dữ liệu này được kiểm tra trước khi xác nhận đã chi; không cho đánh dấu bất kỳ yêu cầu nào là đã trả chỉ bằng một nút không có căn cứ.

Lưu người duyệt, người thực hiện và người xác nhận. Nếu vận hành yêu cầu hai người kiểm soát, tách quyền tương ứng. Khi tìm thấy giao dịch đã chi, không thực hiện chuyển lại chỉ vì màn hình trước đó báo lỗi.

Chế độ thủ công cũng phải chặn duyệt/gửi chi trước hạn bảo trì hoặc khi còn ticket chặn chi. Admin không có quyền dùng chức năng xác nhận thủ công để bỏ qua quy tắc giữ 7 ngày. Sự cố đã chuyển tiền ngoài quy trình phải được ghi nhận và đối soát riêng, không che bằng cách sửa mốc nghiệm thu.

## 7. Thiết kế số dư và sổ giao dịch

### 7.1. Các số liệu freelancer cần thấy

| Số liệu | Nội dung |
| --- | --- |
| Tiền hợp đồng đang giữ | Chưa nghiệm thu; chưa phải tiền có thể rút |
| Cọc đang giữ | Tiền cọc chưa được phân bổ hoặc hoàn |
| Công nợ chờ khả dụng | Đã quyết toán nhưng chưa hết 7 ngày bảo trì |
| Công nợ khả dụng | Có thể tạo yêu cầu rút |
| Công nợ đang bị giữ | Bị chặn do ticket chưa kết thúc, tranh chấp hoặc quyết định xử lý |
| Tiền đang chi | Đã dành cho yêu cầu rút chưa kết thúc |
| Đã chi ngân hàng | Tổng giao dịch chi đã xác nhận thành công |

Tổng công nợ chưa trả bằng công nợ chờ khả dụng cộng khả dụng, bị giữ và đang chi, với các nhóm không chồng lặp. Tiền đang giữ trước nghiệm thu và tổng đã chi không cộng vào tổng công nợ chưa trả này.

Nếu cùng khoản vừa chưa đủ 7 ngày vừa có ticket, vẫn chỉ tính tiền một lần; có thể xếp vào nhóm bị giữ do ticket và hiển thị hai lý do chặn. Gỡ một lý do không làm khoản đó khả dụng khi lý do khác còn hiệu lực. Ví dụ ticket đóng ngày thứ 3 thì khoản trở về chờ hết hạn bảo trì, không vào khả dụng.

Tách thù lao, hoàn cọc và phí trong lịch sử. Hoàn cọc không phải doanh thu nghề nghiệp của freelancer; không cộng khoản này vào biểu đồ thù lao mà không ghi rõ.

### 7.2. Nguyên tắc hạch toán

Đề xuất sổ bút toán cân bằng, có nhóm giao dịch và tài khoản đối ứng. Ví dụ khái niệm:

| Sự kiện | Ghi Nợ | Ghi Có |
| --- | --- | --- |
| Nhận tiền hợp đồng đã xác minh | Tiền tại nguồn thu | Tiền giữ theo hợp đồng |
| Nhận cọc đã xác minh | Tiền tại nguồn thu | Tiền cọc đang giữ |
| Nghiệm thu, phân bổ giá trị hợp đồng | Tiền giữ theo hợp đồng | Công nợ freelancer và doanh thu nền tảng |
| Hoàn cọc thành công nợ | Tiền cọc đang giữ | Công nợ hoàn cọc freelancer |
| Chi trả ngân hàng thành công | Công nợ freelancer | Tiền tại nguồn chi |

Nợ/Có ở bảng này là cách ghi sổ kế toán, không phải nhãn tăng/giảm chung cho mọi loại tài khoản. Khi lập yêu cầu rút chỉ chuyển khoản khả dụng sang khoản dành cho chi, chưa giảm tổng nghĩa vụ phải trả. Khi chi thành công mới giảm nghĩa vụ và nguồn tiền thực tế.

Nếu nguồn thu và nguồn chi khác nhau, cần ghi nhận và đối soát việc điều chuyển tiền giữa hai nguồn. Không mặc định số dư thu qua VNPay sẵn dùng cho payout của nhà cung cấp khác. Phí nhà cung cấp cũng phải có bút toán riêng theo bên chịu phí đã thống nhất.

Quy tắc triển khai:

- Tổng Nợ bằng tổng Có trong từng giao dịch hạch toán; không cho số dư khả dụng âm.
- Số dư hiển thị là dữ liệu tổng hợp có thể kiểm tra lại từ sổ và các khoản giữ, không phải con số frontend tự thay đổi.
- Bút toán đã ghi không sửa/xóa để che lịch sử; sai sót được xử lý bằng giao dịch điều chỉnh hoặc đảo có liên kết.
- Dùng số thập phân chính xác ở backend, không dùng số thực dấu phẩy động để tính tiền. Chốt quy tắc làm tròn VND ở ranh giới tính phí, cọc và gửi nhà cung cấp.
- Lưu tỷ lệ phí, tỷ lệ cọc và số tiền vào bản chụp điều kiện hợp đồng; thay cấu hình mới không tính lại dự án cũ.
- Một sự kiện nghiệp vụ chỉ được hạch toán một lần; kiểm soát bằng khóa duy nhất và giao dịch cơ sở dữ liệu.
- Ticket mở, đổi trạng thái hoặc hết hạn bảo trì chỉ thay đổi khoản giữ/khả năng sử dụng công nợ; không ghi thêm lần nữa thù lao 90% hoặc hoàn cọc 2%.

### 7.3. Ví dụ hợp đồng 10 triệu đồng

Giả sử giữ nguyên tỷ lệ hiện tại, chưa tính phí chuyển tiền hoặc khấu trừ khác:

| Khoản | Số tiền |
| --- | --- |
| Client thanh toán | 10.000.000đ |
| Freelancer đóng cọc 2% | 200.000đ |
| Tổng tiền nhận cần theo dõi | 10.200.000đ |
| Thù lao freelancer sau phí 10% | 9.000.000đ |
| Công nợ hoàn cọc | 200.000đ |
| Tổng công nợ freelancer sau nghiệm thu | 9.200.000đ |
| Phí nền tảng | 1.000.000đ |

Ngay sau nghiệm thu, 9.200.000đ được ghi công nợ nhưng khả dụng để rút là 0đ. Trong 168 giờ đầu, dù không có ticket hoặc mọi ticket đã được xử lý sớm, khoản này vẫn chưa được giải ngân.

Hết 168 giờ mà không còn ticket hoặc chặn khác: 9.200.000đ mới trở thành khả dụng. Nếu ticket mở ngày thứ 6 vẫn đang sửa thì tiếp tục giữ cả khoản này. Giả sử xử lý xong ngày thứ 10 và không còn chặn khác, đến lúc đó khoản mới khả dụng, không cần chờ thêm một chu kỳ 7 ngày.

Sau khi đã khả dụng, freelancer có thể yêu cầu rút 5.000.000đ; phần khả dụng còn 4.200.000đ và phần đang chi là 5.000.000đ. Tổng công nợ chưa trả vẫn là 9.200.000đ. Khi giao dịch chi thành công, công nợ chưa trả còn 4.200.000đ. Nếu xác nhận chuyển tiền thất bại, hoàn phần 5.000.000đ về nhóm phù hợp sau khi kiểm tra các khoản giữ hiện tại, không tự bỏ qua chặn mới.

## 8. Lịch sử thanh toán và giao dịch

Không chỉ cần một danh sách “thanh toán”. Có thể trình bày một trang tổng hợp với bộ lọc, nhưng backend phải phân biệt các loại bản ghi và liên kết được với nhau.

| Lịch sử | Dữ liệu chính | Người xem |
| --- | --- | --- |
| Thanh toán đầu vào | Khoản nộp, lần thử, số tiền, trạng thái, mã nhà cung cấp, thời điểm xác nhận | Người trả và admin; thành viên hợp đồng xem thông tin phù hợp |
| Quyết toán dự án | Hợp đồng, bàn giao, nghiệm thu, giá trị gốc, phí, thù lao, hoàn cọc, thời điểm khả dụng | Client và freelancer của hợp đồng, admin |
| Biến động công nợ | Ghi có, chuyển sang khả dụng, giữ, giải phóng giữ, chi thành công, điều chỉnh | Freelancer sở hữu và admin |
| Bảo trì và ticket | Hạn mở, thời điểm tạo, nội dung lỗi, bản vá, SLA, quá hạn, kết luận và liên kết khoản giữ | Hai bên của dự án và admin có quyền |
| Yêu cầu rút tiền | Số tiền, phí, thực nhận, người nhận đã che, trạng thái, lý do từ chối hoặc lỗi | Freelancer sở hữu và admin |
| Các lần thực hiện payout | Mã lệnh, mã giao dịch nhà cung cấp, phương thức, kết quả, đối soát | Freelancer xem kết quả phù hợp; admin xem chi tiết vận hành |
| Nhật ký quản trị | Người thao tác, hành động, đối tượng, trạng thái trước/sau, lý do, chứng từ | Admin có quyền |

Mỗi giao dịch hiển thị tối thiểu mã tham chiếu, loại giao dịch, số tiền, thời gian, trạng thái và liên kết chi tiết. Có dòng thời gian trạng thái để biết yêu cầu đang chờ duyệt, đã gửi ngân hàng hay đang đối soát.

Ví dụ dòng thời gian dễ đọc:

```text
09:00 - Freelancer tạo yêu cầu rút 5.000.000đ
09:00 - SAM giữ riêng 5.000.000đ cho yêu cầu này
10:15 - Admin duyệt yêu cầu
10:16 - Hệ thống gửi lệnh chuyển tiền
10:17 - Xác nhận thành công, lưu mã giao dịch
```

Nếu có timeout, dòng cuối thay bằng “Đang đối soát”; không hiển thị “Thất bại, vui lòng rút lại” khi chưa xác định tiền đã chuyển hay chưa.

Danh sách cần phân trang, bộ lọc theo loại/trạng thái/thời gian/hợp đồng và thứ tự ổn định. File xuất phải tôn trọng quyền truy cập và che thông tin ngân hàng; kỳ lưu trữ và nhu cầu xuất chứng từ cần chốt trước vận hành thật.

## 9. Mô hình dữ liệu đề xuất

Tên bảng dưới đây là định hướng, sẽ chuẩn hóa theo quy ước repository khi triển khai.

| Nhóm bảng | Vai trò |
| --- | --- |
| `payments` và `payment_attempts` | Nghĩa vụ thanh toán và từng lần thực hiện qua nhà cung cấp |
| `payment_provider_events` | Sự kiện IPN/webhook, khóa chống trùng, kết quả xác minh và xử lý |
| `project_settlements` | Quyết toán duy nhất theo hợp đồng và bàn giao được nghiệm thu |
| `ledger_accounts`, `ledger_transactions`, `ledger_entries` | Tài khoản, nhóm bút toán và các dòng hạch toán cân bằng |
| `freelancer_payables` | Khoản phải trả từ quyết toán, loại thù lao/hoàn cọc, hạn khả dụng và phần còn lại |
| `project_warranties` | Một kỳ bảo trì của hợp đồng gốc: thời điểm nghiệm thu, hạn sau 168 giờ và phiên bản chính sách |
| `maintenance_tickets` | Ticket trong hạn, phạm vi, người tạo, trạng thái, hạn phản hồi/sửa và quyết định cuối |
| `maintenance_ticket_events` | Lịch sử bình luận, bằng chứng, bản vá, đổi trạng thái, gia hạn và quyết định admin |
| `payable_holds` | Lý do giữ riêng theo dự án/ticket/tranh chấp, số tiền, thời điểm áp dụng và giải phóng |
| `paid_maintenance_requests`, `maintenance_quotes` | Yêu cầu ngoài hạn, báo giá, chấp thuận và hợp đồng/đơn hàng mới liên quan |
| `bank_accounts` | Tài khoản nhận tiền, phiên bản và trạng thái xác minh |
| `withdrawal_requests` | Yêu cầu rút, số tiền giữ, phí, thực nhận và bản chụp người nhận |
| `withdrawal_allocations` | Phân bổ số tiền rút vào từng khoản công nợ |
| `payout_attempts` | Lần thực hiện chuyển tiền, phương thức và mã nhà cung cấp |
| `withdrawal_status_history` | Dòng thời gian chuyển trạng thái và lý do |
| `financial_audit_logs` | Dấu vết thao tác tài chính và xác minh ngân hàng |
| `outbox_events` | Công việc/sự kiện cần xử lý sau commit mà không bị mất khi ứng dụng dừng |

Các số dư trong `freelancer_payables` hoặc bảng tổng hợp phải kiểm tra được với sổ giao dịch, không tạo một nguồn số liệu độc lập không thể đối chiếu.

`project_settlements` có thể dùng trạng thái hạch toán như `POSTED`; việc đã chi bao nhiêu được tính từ các khoản phân bổ và payout. Không đổi cả quyết toán thành “đã chi” chỉ vì một lần rút một phần thành công.

Các liên kết quan trọng:

1. Một hợp đồng có một quyết toán hoàn thành theo mô hình nghiệm thu toàn bộ hiện tại.
2. Một quyết toán tạo các khoản công nợ thù lao và hoàn cọc riêng.
3. Một yêu cầu rút phân bổ vào một hoặc nhiều khoản công nợ.
4. Một yêu cầu rút giữ cố định một bản chụp người nhận.
5. Các lần gọi hoặc tra cứu cùng lệnh chi liên kết về một yêu cầu; không làm phát sinh khoản chi mới khi retry truyền tải.
6. Một kỳ bảo trì liên kết đến hợp đồng, bàn giao đã nghiệm thu và workspace/chat của dự án. Ticket dùng cùng nguồn dữ liệu dù tạo từ hai màn hình khác nhau.
7. Mỗi lý do giữ có khóa chống trùng. Nhiều ticket hoặc thời hạn bảo trì cùng chặn một khoản không làm số tiền bị giữ tăng nhiều lần.
8. Công việc bảo trì có phí dùng hợp đồng/đơn hàng mới; không tạo quyết toán thứ hai cho cùng hợp đồng gốc hoặc mở lại trạng thái thực hiện của dự án gốc.

Metadata trả về workspace cần có `acceptedAt`, `warrantyEndsAt`, `maintenanceStatus`, `openIssueCount`, `canCreateWarrantyTicket`, `canRequestPaidMaintenance` và lý do chặn chi. Backend quyết định quyền dựa trên thành viên, thời gian và dữ liệu thực; frontend dùng các cờ này để hiển thị nhất quán. Các mốc SLA và mọi lần gia hạn của ticket phải lưu riêng, không tái sử dụng `warrantyEndsAt` làm hạn sửa.

Không thể chỉ thêm vài enum vào `wallet_ledger_entries` hiện tại: cấu trúc đó đang yêu cầu mọi dòng gắn settlement và contract, số tiền dương và chưa có tài khoản đối ứng. Cần migration bổ sung hoặc thay mô hình phù hợp để thể hiện khoản rút gộp, giảm công nợ, bút toán đảo và chuyển nguồn tiền.

## 10. API dự kiến

Đây là API đề xuất, không phải cam kết các endpoint đã tồn tại. Giữ wrapper phản hồi và cơ chế phân quyền của SAM. API tài chính không nhận chủ sở hữu tùy ý từ frontend.

| Nhóm | API đề xuất | Mục đích |
| --- | --- | --- |
| Ngân hàng | `GET /api/v1/banks` | Danh mục ngân hàng hỗ trợ |
| Ngân hàng | `GET /api/v1/bank-accounts/me` | Danh sách tài khoản của tôi |
| Ngân hàng | `POST /api/v1/bank-accounts` | Thêm tài khoản chờ xác minh |
| Ngân hàng | `POST /api/v1/bank-accounts/{id}/set-default` | Chọn mặc định |
| Ngân hàng | `POST /api/v1/bank-accounts/{id}/disable` | Ngừng sử dụng |
| Công nợ | `GET /api/v1/wallets/me` | Mở rộng số dư và các nhóm tiền |
| Lịch sử | `GET /api/v1/wallets/me/transactions` | Lịch sử công nợ có phân trang |
| Quyết toán | `GET /api/v1/contracts/{id}/settlement` | Chi tiết phân bổ của hợp đồng |
| Bảo trì | `GET /api/v1/projects/{id}/maintenance` | Tóm tắt hạn bảo trì, quyền tạo và lý do giữ công nợ |
| Ticket | `POST /api/v1/projects/{id}/maintenance-tickets` | Client tạo ticket miễn phí, backend bắt buộc kiểm tra còn hạn |
| Ticket | `GET /api/v1/projects/{id}/maintenance-tickets` | Danh sách ticket, kể cả khi dự án đã hoàn thành hoặc hết hạn mở |
| Ticket | `GET /api/v1/maintenance-tickets/{id}` | Nội dung, sự kiện, bản vá và hạn xử lý |
| Ticket | `POST /api/v1/maintenance-tickets/{id}/comments` | Hai bên tiếp tục trao đổi ticket cũ, kể cả sau 7 ngày |
| Ticket | `POST /api/v1/maintenance-tickets/{id}/submit-fix` | Freelancer gửi bản vá, không tự xác nhận đã giải quyết |
| Ticket | `POST /api/v1/maintenance-tickets/{id}/review-fix` | Client xác nhận hoặc nêu rõ lỗi gốc còn tồn tại |
| Ticket | `POST /api/v1/maintenance-tickets/{id}/escalate` | Đề nghị admin xử lý bất đồng/quá hạn |
| Bảo trì có phí | `POST /api/v1/projects/{id}/paid-maintenance-requests` | Gửi yêu cầu báo giá riêng sau hạn, chưa thu tiền |
| Bảo trì có phí | `POST /api/v1/paid-maintenance-requests/{id}/quotes` | Freelancer báo giá, phạm vi, hạn và điều kiện bảo trì tiếp theo |
| Bảo trì có phí | `POST /api/v1/maintenance-quotes/{id}/accept` | Client chấp thuận; tạo công việc và quy trình thanh toán riêng |
| Rút tiền | `POST /api/v1/withdrawals` | Tạo yêu cầu có khóa chống trùng |
| Rút tiền | `GET /api/v1/withdrawals/me` | Danh sách yêu cầu của tôi |
| Rút tiền | `GET /api/v1/withdrawals/{id}` | Chi tiết, dòng thời gian và kết quả |
| Rút tiền | `POST /api/v1/withdrawals/{id}/cancel` | Hủy khi chưa gửi lệnh và còn được phép |
| Admin | `GET /api/v1/admin/bank-accounts` | Hàng đợi xác minh ngân hàng |
| Admin | `POST /api/v1/admin/bank-accounts/{id}/verify` | Xác minh có căn cứ |
| Admin | `POST /api/v1/admin/bank-accounts/{id}/reject` | Từ chối kèm lý do |
| Admin | `GET /api/v1/admin/withdrawals` | Hàng đợi chi trả và bộ lọc |
| Admin | `GET /api/v1/admin/maintenance-tickets` | Ticket quá hạn, tranh chấp hoặc chờ phân loại/xác nhận |
| Admin | `POST /api/v1/admin/maintenance-tickets/{id}/decisions` | Kết luận có bằng chứng, gia hạn hoặc điều chỉnh theo chính sách |
| Admin | `POST /api/v1/admin/withdrawals/{id}/approve` | Duyệt yêu cầu |
| Admin | `POST /api/v1/admin/withdrawals/{id}/reject` | Từ chối trước khi gửi lệnh |
| Admin | `POST /api/v1/admin/withdrawals/{id}/reconcile` | Yêu cầu tra cứu/đối soát; không tự gán thành công |
| Admin | `POST /api/v1/admin/withdrawals/{id}/confirm-manual-payment` | Xác nhận chuyển khoản thủ công với dữ liệu đối soát |

Giữ endpoint nghiệm thu hiện có và thay phần quyết toán bên trong. API duyệt chi tạo công việc xử lý sau commit; worker thực hiện chuyển tiền. Không để trình duyệt giữ khóa nhà cung cấp hoặc gọi API chi hộ trực tiếp.

API tạo ticket phải kiểm tra hạn ở backend dù nút vẫn hiển thị do trang chưa tải lại; trả mã lỗi nghiệp vụ như `WARRANTY_WINDOW_EXPIRED` và gợi ý yêu cầu có phí. API xem/trao đổi ticket cũ không được từ chối chỉ vì đã hết hạn tạo. Các quyết định tài chính của admin phải thực hiện qua dịch vụ hạch toán hiện hành với khóa chống trùng; không nhận trực tiếp một `balance` mới từ giao diện.

Nếu giữ endpoint confirm funding để tương thích frontend cũ, nó chỉ kích hoạt kiểm tra phía backend hoặc trả trạng thái hiện có; không tự nhận lời báo thành công của frontend làm bằng chứng.

## 11. Giao diện web và mobile

### 11.1. Freelancer

- Trang thu nhập: thù lao đã ghi nhận, hoàn cọc riêng, tiền đang giữ, công nợ khả dụng, bị giữ, đang chi và đã chi ngân hàng.
- Quản lý ngân hàng: thêm tài khoản, trạng thái xác minh, lý do từ chối, chọn mặc định, ngừng sử dụng.
- Yêu cầu rút: số tiền, người nhận đã che, phí, thực nhận và bước xem lại trước khi gửi.
- Lịch sử: bộ lọc và trang chi tiết cho từng loại giao dịch; có dòng thời gian trạng thái.
- Khi thiếu ngân hàng: hiển thị công nợ bình thường, hướng dẫn thêm tài khoản khi rút.
- Sau nghiệm thu: ghi “Đã ghi nhận công nợ - đang chờ hết 7 ngày bảo trì”. Nếu có ticket thì hiện “Đang giữ do ticket [mã]”; chỉ ghi “Đã chuyển về ngân hàng” khi payout thành công.
- Trong workspace và chat của dự án hoàn thành: xem ticket, phản hồi, gửi bản vá, hạn sửa và kết quả admin; không phải nghiệm thu lại hợp đồng gốc.

### 11.2. Client

- Xem khoản đã thanh toán và trạng thái xác nhận từ backend.
- Sau nghiệm thu, xem giá trị quyết toán và dự án đã hoàn thành.
- Trong 7 ngày: vẫn có nút “Báo lỗi trong thời gian bảo trì” ở chat và workspace, cùng bộ đếm và hạn chính xác.
- Hết hạn: xem/trao đổi ticket cũ và nút “Yêu cầu bảo trì có phí”; không còn được tạo ticket miễn phí mới.
- Kiểm tra bản vá hoặc yêu cầu admin hỗ trợ trên ticket có sẵn; gửi yêu cầu báo giá không tự phát sinh phí.
- Không xem số tài khoản đầy đủ hoặc lịch sử rút tiền của freelancer ở các dự án khác.
- Không yêu cầu client bấm thêm nút “giải ngân” sau khi đã nghiệm thu hợp lệ.

### 11.3. Admin trên sam-fe

- Hàng đợi xác minh tài khoản nhận tiền.
- Hàng đợi duyệt chi với số tiền, nguồn công nợ, người nhận và trạng thái.
- Màn hình xử lý thủ công/đối soát, có mã giao dịch và chứng từ.
- Báo cáo tiền đang giữ, công nợ chưa trả, tiền đang chi, đã chi và giao dịch cần xử lý.
- Hàng đợi ticket quá hạn, chờ phân loại, chờ client hoặc tranh chấp; nhìn được khoản công nợ dự án đang bị giữ và SLA xử lý.
- Quyết định gia hạn, kết luận ngoài phạm vi hoặc điều chỉnh có căn cứ; không có quyền bỏ qua thời hạn giữ tối thiểu 7 ngày.
- Phân quyền thao tác tài chính ở backend; ẩn nút ở frontend không đủ.

### 11.4. sam-mobile

Nối repository API thật cho các luồng liên quan. Dùng cùng API, số dư và quy tắc với web; không duy trì số dư thật trong mock hoặc tính lại phí từ cấu hình frontend.

Quay về app từ nhà cung cấp thanh toán chỉ dẫn tới màn hình đọc trạng thái backend. Mất kết nối hoặc đóng app không làm mất yêu cầu rút đã tạo. Đặc tả API cần xong trước khi triển khai giao diện tài chính.

Nút bảo trì, thời hạn mở, phản hồi bản vá và chính sách chi trả trên mobile phải giống web. Đồng hồ thiết bị sai hoặc app chạy nền không thay đổi hạn backend; khi quay lại app phải tải lại quyền thao tác.

## 12. Nhà cung cấp thanh toán và nguồn tiền chi trả

Theo yêu cầu triển khai mới nhất, SAM dùng **Pay2S** cho thu hợp đồng/cọc và đơn bảo trì. VNPay vẫn là lựa chọn cấu hình tương thích, đồng thời còn phục vụ subscription cũ. LastBite dùng PayOS là nguồn tham khảo kiến trúc, không đồng nghĩa SAM đang dùng PayOS.

Adapter hiện thực Collection Link V1, HMAC của yêu cầu tạo link và `m2signature` của IPN. Backend đối chiếu merchant, mã đơn, số tiền, môi trường, chống ghi trùng và chỉ ghi tiền sau khi xác minh. Redirect từ browser không xác nhận thanh toán. Khoản không thể phân bổ được giữ ở receipt REVIEW để đối soát, không tạo công nợ có thể rút.

Chưa xác định được API khởi tạo chuyển tiền ra ngân hàng trong tài liệu Pay2S công khai đã đối chiếu. Bản triển khai dùng **chi thủ công có kiểm soát**: duyệt, nhận xử lý, chuyển tại ngân hàng và xác nhận bằng mã giao dịch/chứng từ. Không có endpoint payout giả định. API lịch sử có giao dịch OUT không chứng minh tài khoản có quyền tạo lệnh chi.

Giữ nguồn thu và ngân quỹ dùng chi có đối soát thực tế. Thiếu tiền nguồn không làm mất công nợ freelancer. Khi chưa rõ kết quả chuyển, giữ reservation và đưa vào đối soát, không tự coi là thất bại để chuyển lần nữa.

Nguồn chính thức:

- [Pay2S Collection Link V1](https://developer.pay2s.vn/docs/api/collection-link/).
- [Pay2S IPN và chữ ký](https://developer.pay2s.vn/docs/api/instant-payment-notification/).
- [Pay2S lấy thông tin tích hợp](https://developer.pay2s.vn/docs/api/tich-hop-ky-thuat/).
- [Pay2S API lịch sử giao dịch](https://developer.pay2s.vn/docs/history-api/tai-lieu-ky-thuat/).

Biến môi trường, xoay khóa mã hóa, quy trình bật LIVE và giới hạn hiện tại được ghi trong [hướng dẫn vận hành](PAY2S_FINANCE_SETUP.md).

## 13. Lộ trình chỉ trong ba repository

| Giai đoạn | sam-be | sam-fe | sam-mobile | Điều kiện hoàn thành |
| --- | --- | --- | --- | --- |
| 1. Chuẩn hóa tiền vào | Xác minh IPN/tra cứu; chống trùng; bỏ xác nhận tiền dựa trên FE; tách môi trường | Hiển thị đang xác nhận và trạng thái BE | Chuẩn bị adapter thật cho luồng liên quan | Không thể giả thanh toán từ frontend |
| 2. Công nợ tự động | Nâng quyết toán, sổ giao dịch, mốc nghiệm thu, giữ 168 giờ và API lịch sử; thống nhất đường phục hồi admin | Thay số dư/thu nhập mẫu; hiển thị tiền chờ bảo trì | Hiển thị cùng công nợ và hạn từ BE | Nghiệm thu ghi công nợ một lần, chưa được rút trước hạn |
| 2b. Ticket bảo trì | Module ticket, khóa công nợ theo dự án, SLA, xử lý quá hạn, sự kiện chat và quyền sau hoàn thành | Nút báo lỗi trong chat/workspace, danh sách, bản vá, admin xử lý | Cùng ticket, quyền và thông báo với web | Ticket trong hạn giữ tiền; hết hạn chỉ khóa tạo mới |
| 3. Ngân hàng và rút tiền | Tài khoản nhận tiền, xác minh, giữ khoản rút, duyệt, phân bổ, audit | Giao diện freelancer và admin | Ngân hàng, tạo rút, lịch sử | Không rút vượt số dư; theo dõi được mọi yêu cầu |
| 4. Chi trả | Worker, adapter chi hộ hoặc chế độ thủ công có chứng từ, đối soát | Hiển thị kết quả và xử lý ngoại lệ | Đồng bộ trạng thái thực | Giảm công nợ đúng một lần khi chi thành công |
| 5. Bảo trì có phí và tự động hóa | Yêu cầu/báo giá riêng, hợp đồng liên quan; tự duyệt/chi theo chính sách; tùy chọn đợt chi theo lịch | Báo giá, thanh toán riêng, cấu hình và giám sát | Yêu cầu dịch vụ có phí và lịch chi nếu có | Client đồng ý trước thu phí; không trộn công nợ gốc |

Migration được thêm mới trong `sam-be`; không sửa ngược migration đã chạy. Dữ liệu `sandbox=true` và `SIMULATED_COMPLETED` không tự đổi thành công nợ thật có thể rút. Cần môi trường thử nghiệm tách biệt và kế hoạch chuyển đổi dữ liệu có đối soát nếu tồn tại tiền thật từ trước.

Không đổi ý nghĩa trường cũ mà để frontend vẫn hiểu là “đã chuyển khoản”. Cập nhật BE, FE, mobile và tài liệu API cùng hợp đồng dữ liệu, có bước tương thích khi cần.

Không bật chi tiền thật khi mới có job tự mở khóa sau 7 ngày mà chưa có ticket, chặn chi theo dự án và xử lý trường hợp ticket mở sát hạn. Trong giai đoạn chưa có báo giá/thanh toán bảo trì riêng, chỉ hiển thị trạng thái chưa hỗ trợ hoặc luồng liên hệ rõ ràng; không giả lập thu phí thành công.

## 14. Kiểm thử cần có khi triển khai

Đây là kế hoạch kiểm thử cho thay đổi nghiệp vụ sắp tới, không phải báo cáo các kiểm thử đã chạy khi viết tài liệu này.

1. Gửi confirm giả hoặc chỉ đổi return URL không được ghi nhận đã thu tiền.
2. IPN trùng, sai chữ ký, sai số tiền hoặc sai đơn vị nhận không làm phát sinh tiền khả dụng sai.
3. Nghiệm thu lặp/đồng thời chỉ có một quyết toán; lỗi giữa chừng rollback toàn bộ nghiệp vụ liên quan.
4. Nghiệm thu khi chưa khai ngân hàng vẫn ghi công nợ; rút tiền khi chưa xác minh ngân hàng bị chặn.
5. Nhiều yêu cầu rút đồng thời không vượt tiền khả dụng; gửi lại cùng khóa không giữ tiền hai lần.
6. Khoản chờ khả dụng hoặc bị giữ không được dùng để rút; rút từng phần được phân bổ đúng.
7. Đổi tài khoản mặc định không đổi người nhận của lệnh đã tạo; freelancer không xem/sửa tài khoản của người khác.
8. Callback thành công lặp chỉ giảm công nợ một lần; callback đến muộn không làm lùi trạng thái đã chốt.
9. Timeout sau khi gửi lệnh giữ tiền ở trạng thái đối soát; không tự chi lại hoặc hoàn số tiền giữ.
10. Thất bại đã xác nhận, từ chối và hủy hợp lệ giải phóng khoản giữ cho lệnh đúng một lần, không bỏ qua khoản giữ khác.
11. Hai admin thao tác đồng thời không thể duyệt, chi hoặc xác nhận thủ công hai lần.
12. Thiếu nguồn tiền chi hộ và worker dừng/khởi động lại không làm mất yêu cầu hoặc chi trùng.
13. Tổng bút toán cân bằng; các nhóm số dư, phân bổ và công nợ khớp sổ; phí và làm tròn khớp ví dụ chuẩn.
14. Dữ liệu sandbox không thể tạo yêu cầu chi tiền thật; lịch sử và file xuất che đúng thông tin ngân hàng.
15. Web và mobile đọc cùng kết quả backend, kể cả khi app bị đóng giữa quá trình thanh toán hoặc rút tiền.
16. Nghiệm thu ghi thời điểm bất biến và hạn đúng 168 giờ; nhấn lại hoặc cập nhật bản vá không thay đổi mốc này.
17. Tạo ticket trước hạn được phép; đúng hạn hoặc sau hạn bị từ chối ở backend dù đồng hồ máy client hoặc giao diện chưa cập nhật.
18. Dự án `COMPLETED` vẫn xem chat/workspace và mở ticket trong hạn; chỉ đúng client được tạo ticket miễn phí.
19. Ticket mở ngày thứ 6 được xử lý tiếp sau ngày thứ 7 và không tự chuyển sang có phí; bản vá vẫn được trao đổi trên ticket cũ.
20. Không ticket hoặc ticket đã đóng sớm vẫn không được rút trước đủ 168 giờ; mọi đường admin/thủ công tuân thủ cùng quy tắc.
21. Ticket hợp lệ còn mở khi hết hạn tiếp tục giữ đúng tiền của dự án, không khóa công nợ dự án khác.
22. Tạo ticket và job mở khóa đồng thời không làm lọt tiền sang khả dụng; mở khóa và rút đồng thời không dùng dữ liệu cache cũ.
23. Nhiều ticket và nhiều lý do giữ không làm cộng tiền giữ trùng; đóng một ticket không nhả tiền khi ticket khác hoặc hạn bảo trì còn chặn.
24. Ticket sửa xong sau hạn làm phần còn lại khả dụng đúng một lần khi hết mọi chặn; không ghi thêm thù lao/hoàn cọc hoặc tạo quyết toán mới.
25. Freelancer gửi bản vá hoặc tự gán ngoài phạm vi không đủ để nhả tiền; client im lặng được nhắc và chuyển admin theo chính sách.
26. Quá hạn phản hồi/sửa chuyển cấp đúng SLA; không tự phạt, hoàn tiền hoặc đóng ticket chỉ từ đồng hồ quá hạn.
27. Sửa/gia hạn SLA không đặt lại cửa sổ 7 ngày; đổi `WAITING_CLIENT` không tự tạm dừng SLA vô hạn.
28. Quyết định điều chỉnh tiền có khóa chống trùng, bút toán và dấu vết; không làm chi/hoàn quá nguồn hoặc bồi hoàn trùng.
29. Yêu cầu bảo trì sau hạn chỉ tạo báo giá; chưa có chấp thuận/thanh toán thì không thu phí hoặc bắt freelancer thực hiện.
30. Thanh toán bảo trì có phí, hợp đồng mới và công nợ mới không giữ lại hoặc sửa quyết toán dự án gốc.
31. Ticket đã đóng cuối cùng không bị tùy ý mở lại sau hạn để giữ khoản đã đủ điều kiện; luồng admin xem xét tranh chấp giữ nguyên lịch sử và đối soát tiền đã chi.

## 15. Các chính sách cần chốt trước khi bật tiền thật

| Nội dung | Định hướng hiện tại | Cần xác nhận trước vận hành |
| --- | --- | --- |
| Phí nền tảng và cọc | Giữ 10% và 2% như mã hiện tại | Thời điểm áp dụng và cách làm tròn |
| Công nợ sau nghiệm thu | Tự động ghi nhận, chưa được rút | Đề xuất giữ cả thù lao và hoàn cọc của dự án |
| Thời gian chờ chi | Yêu cầu đã có: tối thiểu 7 ngày liên tục từ nghiệm thu | Ghi vào điều kiện hợp đồng và snapshot; không cho admin chi sớm |
| Ticket miễn phí | Chỉ tạo trong 168 giờ đầu; ticket cũ xử lý tiếp sau hạn | Tiêu chí phạm vi, bằng chứng và người phân loại |
| Hạn sửa từng ticket | Đề xuất 7 ngày từ lúc tạo ticket, tách hạn mở của dự án | Mức theo độ nghiêm trọng và cách gia hạn |
| Hạn phản hồi/kiểm tra | Đề xuất freelancer phản hồi 24 giờ, client kiểm tra bản vá 48 giờ | Giờ liên tục hay giờ làm việc cho SLA; xử lý thiếu thông tin |
| Quá hạn và tranh chấp | Giữ công nợ dự án, chuyển admin; không tự phạt | SLA admin, chế tài, quyền phản hồi và điều kiện điều chỉnh tiền |
| Phạm vi giữ tiền | Đề xuất toàn bộ công nợ chưa trả của đúng dự án | Chính sách giữ một phần chỉ bổ sung khi có cách định lượng được duyệt |
| Bảo trì sau hạn | Yêu cầu/báo giá riêng, client đồng ý trước thanh toán | Phí, phạm vi, bảo trì tiếp theo và thời điểm hỗ trợ luồng này |
| Người nhận | Tài khoản đứng tên freelancer, cần xác minh | Quy trình tên không khớp và bằng chứng xác minh |
| Phí rút/chuyển tiền | Hiển thị tách riêng nếu phát sinh | Bên chịu phí; số tiền ghi giảm công nợ và thực nhận |
| Hạn mức | Backend kiểm soát | Mức tối thiểu, tối đa, số lần và hạn mức ngày |
| Nhà cung cấp | Giữ VNPay chiều thu; chiều chi tách riêng | Dịch vụ chi hộ, nguồn tiền, quyền và thông tin kết nối |
| Vận hành | Admin duyệt chi giai đoạn đầu | Vai trò tài chính; một người hay hai người kiểm soát |
| Tự động chi | Giai đoạn mở rộng | Điều kiện đủ, hạn mức tự động và lịch chi |
| Hoàn tiền và thất bại dự án | Theo dõi nghĩa vụ riêng, có lịch sử | Quy tắc phân bổ cọc, trả client và tranh chấp; không mặc định đã tự động hóa |
| Lưu trữ | Giữ dấu vết tài chính và chứng từ | Thời hạn lưu, xuất báo cáo và quyền truy cập |

Phạm vi ban đầu giữ mô hình quyết toán toàn bộ sau nghiệm thu. Chi trả theo milestone, tự kết luận freelancer bỏ việc hoặc tự phạt cọc cần đặc tả riêng trước khi bổ sung.

Cam kết bảo trì, thời gian giữ tiền và cách xử lý ticket phải được hai bên biết trước khi ký. Không áp hồi tố thời hạn giữ hoặc chế tài mới cho hợp đồng cũ đã hoàn thành/được chi mà chưa có kế hoạch chuyển đổi phù hợp.

## 16. Điểm bắt đầu trong mã hiện tại

Các liên kết tương đối dưới đây dành cho workspace đang chứa ba repository cùng cấp:

- [Workflow nghiệm thu](../src/main/java/com/sam/be/modules/workflow/service/impl/ProjectWorkflowServiceImpl.java).
- [Quyết toán và ghi công nợ](../src/main/java/com/sam/be/modules/wallet/service/impl/ProjectSettlementServiceImpl.java).
- [Xử lý thanh toán, IPN và release](../src/main/java/com/sam/be/modules/payment/service/impl/PaymentServiceImpl.java).
- [Sổ ví hiện tại](../src/main/java/com/sam/be/modules/wallet/entity/WalletLedgerEntry.java).
- [Migration ví và quyết toán sandbox](../src/main/resources/db/migration/V25__Create_Manual_Qa_And_Sandbox_Wallet_Tables.sql).
- [Tài liệu payment cũ cần đồng bộ khi triển khai](API_Specification_Payment_v1.md).
- [Trang thu nhập web còn dữ liệu mẫu](../../sam-fe/src/pages/FreelancerEarningsPage.tsx).
- [Repository mobile đang dùng mock](../../sam-mobile/src/services/repository.ts).
- [Workflow web tiếp tục hỗ trợ dự án hoàn thành](../../sam-fe/src/components/ProjectWorkflowPanel.tsx).
- [Màn chat workspace tích hợp nút và tóm tắt bảo trì](../../sam-fe/src/pages/WorkspaceChatView.tsx).
- [Tóm tắt workspace backend cần mở rộng metadata bảo trì](../src/main/java/com/sam/be/modules/chat/service/WorkspaceSummaryService.java).

Nếu mở riêng repository `sam-be`, các liên kết sang `sam-fe` và `sam-mobile` cần có workspace cùng cấu trúc. Toàn bộ giải thích và luồng nghiệp vụ trong tài liệu vẫn đọc được độc lập.


## 17. Phạm vi đã hiện thực và phần còn cần xác minh

### 17.1. Những ràng buộc đã đưa vào mã

- Nghiệm thu giữ nguyên `COMPLETED`, ghi công nợ một lần; toàn bộ thù lao và cọc hoàn chưa được rút trước đủ 168 giờ. Thao tác nghiệm thu lặp không reset đồng hồ.
- Chỉ client sở hữu dự án mở ticket miễn phí trong `[acceptedAt, acceptedAt + 168 giờ)`. Backend quyết định theo UTC. Đúng hạn kết thúc không mở mới; không phụ thuộc đồng hồ hoặc nút ẩn phía client.
- Ticket hợp lệ chưa kết thúc giữ công nợ của nguồn đó, kể cả vượt ngày thứ 7. Freelancer gửi bản vá; client kiểm tra. Quá hạn chuyển admin, không tự đóng ticket, phạt hoặc khấu trừ.
- Khi đã hết cửa sổ, yêu cầu mới tạo báo giá/đơn có phí riêng. Không đổi một lỗi cũ đang có nghĩa vụ xử lý thành dịch vụ thu phí. Đơn mới có nguồn thanh toán, nghiệm thu, công nợ và bảo trì riêng.
- Dữ liệu ngân hàng AES-256-GCM với key ID, AAD; fingerprint dùng khóa HMAC riêng. Chỉ admin được reveal sau xác thực lại và có audit. Yêu cầu chi giữ snapshot người nhận mã hóa, không thay theo hồ sơ sau này.
- Sổ cái bút toán kép, journal/audit/receipt bất biến, ràng buộc DB và khóa giao dịch chống chi vượt. Tiền sandbox không khả dụng cho rút thật.
- Chi thủ công có trạng thái đối soát, mã giao dịch duy nhất, request key idempotent và kiểm soát hai admin mặc định. Kiểm tra điều kiện giữ tiền tại các bước chi, không có bypass 7 ngày.
- WebSocket xác thực JWT/session, kiểm tra thành viên room/contract và người nhận thông báo; sender lấy từ phiên xác thực. Access token web/mobile giữ trong bộ nhớ; refresh dùng cookie và kiểm soát thay đổi người dùng.

### 17.2. Mã nguồn và API thực tế

| Phần | Nguồn triển khai |
| --- | --- |
| Ví, ngân hàng, yêu cầu chi và admin | [FinanceController](../src/main/java/com/sam/be/modules/finance/FinanceController.java) — `/api/v1/finance/*` |
| Ticket, sự kiện và SLA | [MaintenanceController](../src/main/java/com/sam/be/modules/finance/MaintenanceController.java) và [MaintenanceService](../src/main/java/com/sam/be/modules/finance/MaintenanceService.java) |
| Báo giá và đơn bảo trì riêng | [PaidMaintenanceController](../src/main/java/com/sam/be/modules/finance/PaidMaintenanceController.java) |
| Thu tiền và xác minh IPN | [PaymentServiceImpl](../src/main/java/com/sam/be/modules/payment/service/impl/PaymentServiceImpl.java) và [Pay2sClient](../src/main/java/com/sam/be/modules/finance/Pay2sClient.java) |
| Mã hóa | [BankDataCipher](../src/main/java/com/sam/be/modules/finance/BankDataCipher.java) |
| Ràng buộc dữ liệu | [Migration V37](../src/main/resources/db/migration/V37__Finance_Maintenance_And_Encrypted_Bank_Accounts.sql) |

Không dùng các endpoint dự kiến trong mục 10 để tích hợp mà bỏ qua controller thực tế. Hướng dẫn [Pay2S và vận hành](PAY2S_FINANCE_SETUP.md) ghi đường dẫn chính và biến môi trường hiện dùng.

### 17.3. Điều kiện chưa được coi là hoàn thành production

Đã chạy kiểm thử tự động backend/web/mobile trong workspace. Kiểm thử DB có schema tạm đã được viết nhưng chưa chạy vì môi trường không cho kết nối PostgreSQL; chưa thực hiện giao dịch Pay2S sandbox/production hoặc xác minh chuyển ngân hàng. Phải kiểm thử staging và migration trước khi bật `FINANCE_LIVE_PAYOUTS_ENABLED=true`.

Chưa có adapter chuyển tiền tự động, hoàn tiền provider tự động, công cụ xử lý hạch toán receipt REVIEW, job tái mã hóa dữ liệu cũ hay backfill tự động công nợ trước V37. Xử lý hoàn tiền/điều chỉnh bồi thường phải theo quy trình đối soát có bằng chứng, không sửa trực tiếp lịch sử bất biến. Mobile còn cần kiểm thử thiết bị và hoàn thiện phân trang dữ liệu ngoài phạm vi tài chính mới.

Thiết kế bảo vệ nhiều lớp không đồng nghĩa bảo mật tuyệt đối: production cần bảo vệ runtime/secret, kiểm soát quyền admin, sao lưu khóa, theo dõi đối soát và rà soát bảo mật độc lập. Không để khóa hoặc dữ liệu đầy đủ xuất hiện trong bundle, log hay chat.


## 18. Bổ sung thu nhập và nhắc cập nhật ngân hàng

Đã có API thu nhập riêng `/api/v1/finance/income` và `/income/entries`: thống kê thù lao sau phí từ nghiệm thu, loại tiền hoàn cọc, không giảm khi rút tiền; báo cáo tháng/năm theo giờ Việt Nam và tách số liệu sandbox.

Homepage freelancer web/mobile hiển thị tóm tắt thu nhập và nhắc bổ sung ngân hàng dựa trên API `/api/v1/finance/bank-accounts/setup-status`. Đã gửi thông tin thì hiển thị chờ xác minh; đã có tài khoản xác minh thì ẩn nhắc bổ sung. Lỗi tải không bị hiểu thành chưa khai báo.

Cập nhật ngân hàng dùng `PUT /api/v1/finance/bank-accounts/{id}`, tạo phiên bản mã hóa mới để xác minh, giữ bản cũ và snapshot lệnh chi. Khi có lệnh chi chưa kết thúc thì yêu cầu thêm tài khoản mới thay vì thay bản đang dùng. Migration V38 bổ sung liên kết phiên bản và index thu nhập; không sửa V37 đã áp dụng.

Chi tiết payload, trường dữ liệu và các bước test: [FREELANCER_INCOME_BANK_API.md](FREELANCER_INCOME_BANK_API.md).
