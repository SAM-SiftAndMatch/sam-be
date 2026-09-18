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

*   **3. AI Job Matcher & Instant Claim (Nhận việc 1 chạm - Tính năng trả phí cho Dev):** AI sẽ tự động quét các kỹ năng của Freelancer (Ví dụ: cứng Java, Spring Boot). Khi có một dự án mới đăng lên khớp 100% kỹ năng, AI lập tức bắn thông báo về điện thoại của Freelancer đó. Freelancer chỉ cần bấm nút "Claim" là nhận việc ngay trong 1 giây mà không cần phải viết hồ sơ đấu thầu (bidding).
*   **4. Co-op Smart Contract (Chốt Deal Thời gian thực):** Ứng dụng công nghệ WebSockets, cho phép Client và Freelancer cùng xem và chỉnh sửa một bản hợp đồng/báo giá theo thời gian thực (Real-time). Bất kỳ thay đổi nào về con số (ví dụ: số lần sửa đổi, giá tiền) từ một phía sẽ ngay lập tức nảy lên màn hình của người kia mà không có độ trễ. Khi hai bên đồng thuận, sẽ tiến hành ký Xác nhận điện tử (Digital Approval).
*   **5. Milestone Escrow (Ký quỹ & Thanh toán theo tiến độ):** Để dự án được bắt đầu, khách hàng bắt buộc phải nạp tiền cọc (30% - 50% giá trị hợp đồng) thông qua cổng thanh toán API MoMo/ZaloPay. Backend Spring Boot sẽ giữ khoản tiền này ở trạng thái đóng băng (Escrow) cực kỳ an toàn, đảm bảo Dev không bị quỵt tiền.

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
*   **Phí nền tảng (Platform Fee):** Thu từ 6-8% trên tổng giá trị dự án sau khi nghiệm thu và giải ngân thành công. Mức phí này được dùng để duy trì Server và hệ thống Ký quỹ (Escrow), cực kỳ cạnh tranh và hấp dẫn hơn rất nhiều so với mức "cắt máu" 10-20% của Upwork hay Fiverr.

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

## PHẦN III: DANH SÁCH TÀI KHOẢN KIỂM THỬ (SEED DATA)
Dữ liệu đã được nạp sẵn vào Database qua công cụ Flyway, các bảng dữ liệu đã được móc nối quan hệ hoàn chỉnh.

**Mật khẩu dùng chung cho toàn bộ tài khoản:** `123456`

| Vai trò (Role) | Cấp độ | Email Đăng nhập | Tên hiển thị | Ghi chú & Dữ liệu đi kèm |
| :--- | :--- | :--- | :--- | :--- |
| **ADMIN** | Quản trị | `admin@sam.com` | System Admin | Tài khoản quản trị cấp cao toàn hệ thống. |
| **CLIENT** | BASE | `client@fpt.com` | FPT Software | Khách hàng Doanh nghiệp. **Đã đăng 2 Job:** Job A (Backend) đang OPEN, Job B (Frontend) đang IN_PROGRESS. Đã nạp 800$ vào ví Escrow. |
| **FREELANCER** | PRO DEV | `long.be@gmail.com` | Long Dương (Backend) | 3 năm exp Java, 2 năm Spring Boot. **Được AI gợi ý Job A (Match 98%)**. Đã nộp Proposal chờ FPT duyệt. |
| **FREELANCER** | PRO DEV | `long.fe@gmail.com` | Quang Long (Frontend) | 2 năm exp React, TypeScript. **Đã ký hợp đồng làm Job B**. Được FPT đánh giá 5 sao. |