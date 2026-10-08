# TÀI LIỆU ĐẶC TẢ HỆ THỐNG: AI-POWERED IT FREELANCE PLATFORM
**Công nghệ lõi (Tech Stack):** Java Spring Boot, ReactJS, TypeScript, PostgreSQL, Redis, WebSockets, Docker.

---

## PHẦN I: TRẢI NGHIỆM NGƯỜI DÙNG & LUỒNG THỰC THI DỰ ÁN (WORKFLOW)

Hệ thống được chia làm 4 giai đoạn sinh tử, bao quát vòng đời của một dự án IT từ lúc thai nghén ý tưởng đến lúc trao tiền nhận code.

### GIAI ĐOẠN 1: KHỞI TẠO & CHỐT YÊU CẦU (BRIEFING & SCOPING)
Mục tiêu của giai đoạn này là biến ý tưởng mù mờ của khách hàng (Non-tech) thành tài liệu kỹ thuật chuẩn chỉnh mà không cần chat qua lại tốn thời gian.

*   **1. Interactive UI Briefing (Hệ thống Briefing Trực quan):** Thay vì để khách hàng nhắn tin yêu cầu lủng củng, hệ thống ép khách hàng điền form thông qua các giao diện thẻ hình ảnh (image cards), thanh trượt (slider) và trắc nghiệm. Giao diện được thiết kế bằng React/TypeScript đảm bảo độ mượt mà, chuyên nghiệp. Kết quả đầu ra của bước này là hệ thống tự động sinh ra một bản Đặc tả yêu cầu phần mềm (SRS - Software Requirements Specification) mô tả cực kỳ chi tiết các chức năng cần có.
*   **2. AI Risk Radar (Cảnh báo Rủi ro Ngầm):** Hoạt động như một màng lọc bảo vệ cộng đồng. Ngay khi khách hàng điền xong form, AI chạy ngầm để đối chiếu giữa Ngân sách (Budget) và Thời hạn (Deadline). Ví dụ: Nếu khách hàng yêu cầu làm một app có 50 màn hình, thời gian 7 ngày nhưng ngân sách chỉ có $200, AI sẽ lập tức bật báo động đỏ cảnh báo rủi ro cho cả nền tảng và các Freelancer.

### GIAI ĐOẠN 2: ĐÀM PHÁN, GHÉP NỐI & NHẬN VIỆC (MATCHMAKING & NEGOTIATION)
Loại bỏ khâu viết Proposal dài dòng, đưa tốc độ chốt deal lên hàng tính bằng giây.

*   **3. AI Job Matcher & Instant Claim (Nhận việc 1 chạm & Độc quyền 5 phút):** 
    *   **Mai mối tức thì:** Khi Client đăng Job "Tuyển gấp", AI tự động quét và bốc ra 5 Dev PRO giỏi nhất đang online. Hệ thống lập tức bắn Push Notification/WebSocket cho 5 Dev này. Đồng thời, Client cũng nhìn thấy toàn bộ hồ sơ (kèm đánh giá của AI) của 5 Dev này trên Dashboard.
    *   **Tàng hình & Bắt tay 2 chiều (Mutual Handshake):** Bằng kỹ thuật On-the-fly Filtering, Job này sẽ hoàn toàn "tàng hình" khỏi Marketplace chung trong đúng 5 phút đầu tiên (ẩn cả danh sách lẫn xem trực tiếp — chỉ chủ job, admin và 5 Dev được AI chọn mới mở được chi tiết). Trong 5 phút vàng này, 5 Dev PRO có đặc quyền bấm "Claim" (Yêu cầu Chat), hoặc Client có thể chủ động bấm "Mời Chat". Bất kể bên nào chủ động, chỉ cần bên kia xác nhận (Accept), một phòng Chat Real-time sẽ lập tức được mở ra.
    *   **Hết 5 phút:** Đúng phút thứ 5, Job sẽ tự động public lên Marketplace. Các Dev bình thường khác bắt đầu nhìn thấy và phải nộp `Proposal` (hồ sơ dự thầu) theo cách truyền thống. Client lúc này có 2 luồng nhân sự để lựa chọn (VIP 1 chạm & Phổ thông nộp Proposal).
*   **4. Co-op Smart Contract (Chốt Deal Thời gian thực):** Ứng dụng công nghệ WebSockets, cho phép Client và Freelancer cùng xem và chỉnh sửa một bản hợp đồng/báo giá theo thời gian thực (Real-time). Bất kỳ thay đổi nào về con số (ví dụ: giá tiền, điều khoản) từ một phía sẽ ngay lập tức nảy lên màn hình của người kia mà không có độ trễ. Khi hai bên đồng thuận, sẽ tiến hành ký Xác nhận điện tử (Digital Approval). Chỉ Client được khởi tạo hợp đồng bằng AI; Freelancer chờ Client tạo. Ký đôi xong AI thẩm định lại toàn văn: đạt thì chốt, lệch thì 2 bên bấm "Giữ nguyên" mới chốt theo số trong hợp đồng, không thì sửa–ký–thẩm định lại.
*   **5. Nạp tiền khởi động & Ký quỹ một lần (Startup Funding):** Ký đôi xong, Job KHÔNG chạy ngay mà chuyển sang trạng thái `AWAITING_PAYMENT` (chờ nạp tiền). Hệ thống lấy `agreedAmount` trong hợp đồng làm tổng chuẩn, AI đọc lại toàn văn hợp đồng để đối chiếu con số — lệch là chặn nạp tiền, bắt sửa hợp đồng cho khớp. Sau đó:
    *   Client nạp **100%** giá trị hợp đồng qua VNPay (1 lần duy nhất).
    *   Freelancer đặt **cọc cam kết 2%** giá trị hợp đồng qua VNPay (1 lần duy nhất; xong việc đúng hạn được hoàn trả, bỏ job thì đền cho Client).
    *   Đủ tiền cả 2 bên, hệ thống tự chuyển Job sang `IN_PROGRESS`, dự án chính thức bắt đầu.
    *   Cuối dự án thanh toán **1 lần duy nhất**: Freelancer nhận **90%**, sàn giữ **10%** phí nền tảng. (Chi tiết đầy đủ xem mục `PHỤ LỤC A` cuối tài liệu.)

### GIAI ĐOẠN 3: THỰC THI & QUẢN LÝ TIẾN ĐỘ (EXECUTION & WORKFLOW)
Giúp Freelancer tập trung code, khách hàng dễ dàng theo dõi mà không cần hối thúc.

*   **6. Anti-Ghosting Mini-Kanban (Bảng Tiến độ Thời gian thực):** Ngay khi hợp đồng được tạo, hệ thống tự động sinh ra một Timeline công việc. Dự án được chia nhỏ thành các giai đoạn. Cứ kết thúc mỗi giai đoạn, Freelancer bắt buộc phải đưa sản phẩm (Demo/Deploy) cho khách hàng xem và test. Nếu khách hàng check OK, dự án mới được chuyển sang giai đoạn tiếp theo. Nếu không OK, hai bên sẽ sử dụng nút "Trao đổi" để chốt lại các thay đổi trước khi đi tiếp. Luồng công việc này bắt buộc phải có để hệ thống Escrow biết chính xác lúc nào nên giải ngân (nhả tiền).
*   **7. Smart Scope Shield (Lá chắn Yêu cầu Động - Tính năng trả phí cho Dev):** Bảo vệ Dev khỏi những khách hàng "độc hại" hay vòi vĩnh. Khi khách hàng nhắn tin đòi thêm tính năng nằm ngoài hợp đồng ban đầu, AI sẽ lập tức chặn tin nhắn lại, bóc tách yêu cầu và tự động tạo ra một bản Báo giá phát sinh (Change Request) gửi ngược lại cho khách. Dev sẽ không bao giờ lo cảnh làm việc không công.

### GIAI ĐOẠN 4: NGHIỆM THU, KIỂM TRA CHẤT LƯỢNG & GIẢI NGÂN (QA, REVIEW & CASH)
Bảo vệ mã nguồn của khách hàng và tự động hóa quy trình trả tiền cho Dev.

*   **8. 1-Click Sandbox Testing:** Cung cấp một môi trường ảo (đường link tạm thời) chỉ bằng 1 cú click để khách hàng tự do test thử ứng dụng/web trước khi quyết định nghiệm thu.
*   **9. AI Quality Check - Trọng tài Code (Tính năng trả phí cho Client):** Hệ thống gọi API kết nối thẳng vào kho lưu trữ GitHub của dự án. Khi Dev đẩy code (push) lên, AI tự động kéo mã nguồn về môi trường Docker cách ly để quét lỗi bảo mật, mã độc. Nếu code đạt chuẩn ("Xanh" / Pass), hệ thống tự động trích tiền từ quỹ Escrow bắn thẳng về ví của Dev ngay trong đêm. Khách hàng không có quyền giam tiền vô cớ.
*   **10. 1-Click Cloud Handover (Bàn giao Hạ tầng):** Khi hợp đồng kết thúc thành công, hệ thống thực hiện bàn giao mã nguồn (code sạch) và hạ tầng đám mây cho khách hàng chỉ với 1 thao tác.

---

## PHẦN II: MÔ HÌNH KINH DOANH (MONETIZATION & BUSINESS MODEL)
Đây là chiến lược dòng tiền thông minh, thu hút người dùng bằng tính năng Free nhưng kiếm doanh thu khổng lồ từ các dịch vụ giá trị gia tăng (Premium).

### 1. NGUỒN THU CỐT LÕI (Passive Income)
*   **Phí nền tảng (Platform Fee):** Thu **10%** trên tổng giá trị hợp đồng, quyết toán 1 lần duy nhất khi dự án hoàn thành và giải ngân (freelancer nhận 90%). Mức phí này dùng để duy trì Server và hệ thống Ký quỹ (Escrow).

### 2. CÁC TÍNH NĂNG MIỄN PHÍ (Bắt buộc để giữ chân người dùng)
Nếu thu tiền các tính năng này, người dùng sẽ lách luật rủ nhau ra Zalo làm việc:
*   Tạo SRS qua Interactive UI Briefing.
*   Cảnh báo rủi ro ngầm AI Risk Radar.
*   Chốt Deal bằng Co-op Smart Contract.
*   Ký quỹ Milestone Escrow (Chỉ thu phí phần trăm lúc hoàn thành).
*   Bảng tiến độ Anti-Ghosting Mini-Kanban.
*   Test môi trường ảo Sandbox Testing.
*   Bàn giao sản phẩm 1-Click Cloud Handover.

### 3. DÒNG DOANH THU ĐỘT PHÁ (Premium Features / Subscriptions)
Chia làm 2 tệp khách hàng B2B (Doanh nghiệp) và B2C (Freelancer) để tối ưu hóa Doanh thu định kỳ (ARR).

**A. Thu tiền từ Doanh nghiệp / Khách hàng thuê (B2B)**
Họ có tiền và sẵn sàng chi trả để đổi lấy "Tốc độ" và "Sự an tâm".
*   **Hạng BASE (Tài khoản Cơ bản - Trả phí theo lần xài):**
    *   Đăng việc tiêu chuẩn: MIỄN PHÍ.
    *   Nổi bật: Ghim dự án lên top tìm kiếm. Giá: **59.000 VNĐ / lần**.
    *   Tuyển gấp (AI Headhunter): Hệ thống AI quét và bốc chính xác 5 Dev giỏi nhất đang online, gửi Push Notification/SMS ép vào xem dự án. Giá: **99.000 VNĐ** (duy trì đến khi ký được hợp đồng).
    *   Trọng tài Code & Bảo hành AI QA: Giá **59.000 VNĐ / dự án**.
*   **Hạng BUSINESS (Đăng ký gói Tháng cho SME/Agency):**
    *   Giá thuê bao: **249.000 VNĐ / tháng**.
    *   Quyền lợi: Trọn quyền dùng chức năng đăng bài "Nổi bật" và "Tuyển gấp" trong tháng, tối ưu chi phí tìm nhân sự.
    *   Bảo hiểm mã nguồn rác (AI QA nâng cao): **299.000 VNĐ / tháng** (Hoặc thu 3% với dự án lớn). SME rất chuộng gói này để đảm bảo nhận code không mã độc.

**B. Thu tiền từ Lập trình viên (B2C)**
Đánh vào tâm lý lười làm Sales, muốn tự động tìm việc và ghét bị khách hàng "bào" công sức.
*   **Gói PRO DEV (Đăng ký Tháng):**
    *   Giá thuê bao: **149.000 VNĐ / tháng** (Mức giá rất "mềm", bằng một ly cafe hoặc gói Netflix, sinh viên hay Dev đều dễ dàng chi trả).
    *   Vũ khí giành Job: Kích hoạt tính năng **AI Job Matcher & Instant Claim**. Nhận thông báo việc làm độc quyền trước người khác 5 phút, bấm 1 nút nhận việc luôn không cần đấu thầu.
    *   Vũ khí bảo vệ: Kích hoạt **Smart Scope Shield**. Tự động chặn yêu cầu phát sinh và quăng báo giá cho khách, Dev cứ ung dung code.

---

## PHỤ LỤC A: LUỒNG NẠP TIỀN KHỞI ĐỘNG & THANH TOÁN MỘT LẦN (STARTUP FUNDING)

Luồng này thay thế mô hình ký quỹ chia đợt cũ. Mọi hợp đồng đều tuân thủ cùng một công thức tiền.

### A.1. Trạng thái Job liên quan
`NEGOTIATING` (đang chốt hợp đồng) → ký đôi xong → **AI thẩm định** → đạt thì `AWAITING_PAYMENT` (chờ nạp tiền, chưa chạy) → nạp đủ 2 bên → `IN_PROGRESS` (bắt đầu làm) → nghiệm thu xong → `COMPLETED`.

### A.2. Công thức tiền (tính trên `agreedAmount` của hợp đồng đã ký)
Ví dụ hợp đồng 10.000.000 VNĐ:
*   **Client chuyển: 100% = 10.000.000 VNĐ** (1 lần, qua VNPay, ngay sau khi ký).
*   **Freelancer đặt cọc cam kết: 2% = 200.000 VNĐ** (1 lần, qua VNPay, cùng thời điểm).
*   **Cuối dự án (1 lần duy nhất): Freelancer nhận 90% = 9.000.000 VNĐ, sàn giữ 10% = 1.000.000 VNĐ** (phí nền tảng).
*   **Số phận tiền cọc 2%:** freelancer hoàn thành đúng hợp đồng → hoàn trả 100% (cộng chung với 90%); bỏ job/vi phạm → đền cho Client.

### A.3. AI đối chiếu số tiền (bắt buộc trước khi nạp)
*   AI đọc toàn văn điều khoản, trích tổng giá trị hợp đồng và so với `agreedAmount` (lưu ý: `agreedAmount` lấy từ chính hợp đồng mà AI đã soạn và 2 bên đã cùng sửa).
*   Khớp → hiện badge xanh, mở nút chuyển tiền. Lệch (quá 1.000 VNĐ) → hiện cảnh báo đỏ, **chặn tạo thanh toán ở cả BE lẫn FE**, 2 bên phải sửa lại hợp đồng cho khớp số rồi đối chiếu lại.

### A.4. Thứ tự gọi API (Backend)
1.  Ký đôi xong **chưa chốt vội**: BE gửi toàn văn hợp đồng cho AI thẩm định (so số trong văn bản
    với `agreedAmount`, so với giá AI đề xuất ban đầu, soi điều khoản vô lý).
    *   AI báo OK → hợp đồng `ACTIVE`, Job sang `AWAITING_PAYMENT`, AI nhắn vào phòng chat báo hợp lệ.
    *   AI báo lệch → **xóa chữ ký 2 bên**, AI nhắn thẳng vào phòng chat cho cả 2 cùng đọc
        ("hợp đồng đã đổi ... so với ban đầu là ..., sai lệch ở ..., vẫn giữ nguyên chứ?").
        Tin AI mang danh nghĩa hệ thống (bubble tím riêng, icon 🤖 — không gắn tên người ký cuối).
        Người ký thứ 2 tự về phòng chat đợi kết quả. Mỗi bên bấm **"Giữ nguyên bản này"** (`/app/contracts/{id}/confirm`): cả 2 cùng giữ →
        chốt số tiền **theo đúng văn bản**, hợp đồng `ACTIVE`, Job sang `AWAITING_PAYMENT`.
        Không giữ → sửa lại → ký lại → AI thẩm định tiếp (vòng lặp cho tới khi đạt).
        AI lỗi mạng cũng không rollback chữ ký — rớt sang luồng xác nhận tay.
2.  Mở màn nạp tiền: `GET /payments/funding/{contractId}` — bảng tiền 2 bên (100% / 2% / dự kiến 90-10) và trạng thái đã chuyển/chưa.
3.  Đối chiếu AI: `POST /payments/contracts/{contractId}/verify-amount` — FE tự gọi 1 lần khi mở màn.
4.  Client bấm nạp: `POST /payments/fund` `{contractId, returnUrl}` → trả `vnpayUrl`, redirect sang VNPay.
5.  Freelancer bấm cọc: `POST /payments/deposit` `{contractId, returnUrl}` → tương tự.
6.  VNPay báo IPN về `POST /payments/vnpay-ipn` → tiền sang `HELD_IN_ESCROW`, bắn WS `PAYMENT_ESCROW_HELD` cho cả 2.
    IPN không tới được môi trường local thì FE tự báo thay: VNPay redirect về kèm `vnp_ResponseCode=00`,
    FE gọi `POST /payments/{paymentId}/confirm` kèm `txnRef` + `amountVnd` (BE đối chiếu rồi ghi nhận như IPN).
7.  Khoản thứ hai về đủ → BE tự chuyển Job sang `IN_PROGRESS`, bắn WS `PROJECT_STARTED` cho cả 2. Không bên nào phải bấm thêm nút "bắt đầu".
8.  Cuối dự án (luồng nghiệm thu — triển khai ở giai đoạn sau): giải ngân 1 lần, freelancer nhận 90%, sàn 10%, xử lý hoàn/đền cọc 2%.

### A.5. Quy tắc chặn (BE thực thi, FE chỉ hiển thị)
*   Chỉ Client được tạo hợp đồng AI; Freelancer gọi là `FORBIDDEN`.
*   Chỉ tạo được thanh toán khi hợp đồng `ACTIVE`.
*   Mỗi hợp đồng chỉ có đúng 1 khoản nạp 100% và 1 khoản cọc 2% (tạo trùng là lỗi).
*   AI đối chiếu lệch → mọi lệnh tạo thanh toán đều bị từ chối cho tới khi khớp.

---

## PH? L?C B: LU?NG PROPOSAL PH? TH�NG (PUBLIC JOBS)

D�nh cho job public (kh�ng tuy?n g?p, ho?c job g?p d� qua 5 ph�t d?c quy?n). Kh�ng chat ngay nhu lu?ng 1 ch?m.

### B.1. N?p h? so
*   Freelancer n?p 1 h? so/job: thu ch�o, gi� d? xu?t (> 0), s? ng�y d? ki?n, file PDF/DOC/DOCX (t?i da 10MB, upload qua POST /storage/upload-file l?y URL).
*   Ch? n?p khi job c�n OPEN; job g?p trong 5 ph�t d?c quy?n m� kh�ng du?c m?i th� b? ch?n.
*   Client nh?n WS PROPOSAL_RECEIVED ngay khi c� h? so m?i.

### B.2. Client duy?t (chua chat)
*   GET /proposals/job/{jobId} � client xem to�n b?: t�n, headline, gi�, thu ch�o, link file PDF.
*   Client b?m M?i h?p t�c: PENDING -> INVITED, freelancer nh?n WS PROPOSAL_INVITE. Client t? ch?i: PENDING -> REJECTED.

### B.3. Freelancer d?ng � m?i m? chat
*   Freelancer xem l?i m?i ngay tr�n trang chi ti?t job (GET /proposals/me/job/{jobId}) ho?c chu�ng.
*   B?m �?ng �: INVITED -> ACCEPTED, h? th?ng t?o (ho?c d�ng l?i) ph�ng chat v� tr? roomId, client nh?n WS PROPOSAL_ACCEPTED. T? d�y chat + h?p d?ng + n?p ti?n di chung lu?ng v?i job g?p.
*   T? ch?i: INVITED -> REJECTED, b�o cho client.

### B.4. ��ng tuy?n
*   Job sang IN_PROGRESS (d? ti?n kh?i d?ng) th� t? r?t kh?i danh s�ch chung (query ch? l?y OPEN), ch?n n?p h? so m?i, trang chi ti?t hi?n �� d�ng tuy?n thay n�t ?ng tuy?n.
