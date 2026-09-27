# TÀI LIỆU ĐẶC TẢ API
**Hệ thống Freelance Platform**  
Dành cho Frontend Developer | Phiên bản 1.0

---

## Mục lục

1. [Quy ước chung](#i-quy-ước-chung)
2. [Module AI – Phân tích Yêu cầu & Sinh tài liệu SRS](#ii-module-ai--phân-tích-yêu-cầu--sinh-tài-liệu-srs)
3. [Module Storage – Lưu trữ Tài liệu SRS](#iii-module-storage--lưu-trữ-tài-liệu-srs)
4. [Module Profile – Quản lý Hồ sơ Người dùng](#iv-module-profile--quản-lý-hồ-sơ-người-dùng)
5. [Module Job – Quản lý Công việc & Tuyển dụng AI](#v-module-job--quản-lý-công-việc--tuyển-dụng-ai)
6. [Module Chat – Nhắn tin Real-time](#vi-module-chat--nhắn-tin-real-time)
7. [Module Contract – Thương lượng & Ký kết Hợp đồng](#vii-module-contract--thương-lượng--ký-kết-hợp-đồng)
8. [Module Payment – Ký quỹ & Thanh toán](#viii-module-payment--ký-quỹ--thanh-toán)
9. [Tổng hợp tất cả kênh WebSocket](#ix-tổng-hợp-tất-cả-kênh-websocket)
10. [Từ điển Trạng thái Hệ thống](#x-từ-điển-trạng-thái-hệ-thống-enum-dictionary)
11. [Hạ tầng & Cấu hình Hệ thống](#xi-hạ-tầng--cấu-hình-hệ-thống)

---

## I. Quy ước chung

### 1. Cấu trúc Response chuẩn (ApiResponse Wrapper)

Toàn bộ response từ Backend đều được bọc trong cấu trúc `ApiResponse`. Dữ liệu thực tế nằm trong field `result`.

```json
{
  "code":    1000,        // 1000 = Thành công, các code khác = Lỗi
  "message": "Success",
  "result":  { ... }     // Dữ liệu thực tế ở đây
}
```

### 2. Xác thực & Phân quyền

- Mọi API đều yêu cầu **Bearer Token** trong header `Authorization`.
- Backend tự động trích xuất `userId` hiện tại qua `SecurityUtils.getCurrentUserId()` — FE **không cần** gửi `userId` trong body.
- **Role `FREELANCER`**: chỉ được gọi các API thuộc scope Freelancer.
- **Role `CLIENT`**: chỉ được gọi các API thuộc scope Client.

### 3. WebSocket – Cổng kết nối

| Thông số | Giá trị |
|---|---|
| Endpoint kết nối | `/ws` (hỗ trợ SockJS/STOMP) |
| Tiền tố Client → Server | `/app` |
| Tiền tố Server → Client | `/topic` hoặc `/queue` |

> ⚠️ **Lưu ý chung cho FE khi tích hợp WebSocket:**
> 1. Tất cả kênh `/topic/...` là kênh phát thanh (broadcast) – nhiều người có thể nhận cùng lúc.
> 2. Dùng **debounce** khi gửi dữ liệu real-time (sync hợp đồng) để tránh spam server.
> 3. Khi nhận được payload `type === "COMPLETED"` trên kênh hợp đồng → **khóa toàn bộ UI nhập liệu ngay lập tức**.

---

## II. Module AI – Phân tích Yêu cầu & Sinh tài liệu SRS

### Luồng nghiệp vụ

```
LUỒNG 1 – Khởi động:
  Client lấy Base Questions → FE hiển thị form thu thập thông tin ban đầu.

LUỒNG 2 – Chat với AI BA (Stateful – có bộ nhớ):
  · FE tự sinh sessionId (UUID) và gửi kèm trong mọi tin nhắn.
  · Backend lưu toàn bộ lịch sử chat trong Redis, TTL = 24 giờ.
  · Khi AI đủ thông tin → tự động sinh file SRS (.txt) → upload lên Cloudinary.
  · Response trả về trường currentSrsUrl → FE dùng URL này điền vào Job khi đăng tuyển.
  · Khi status = "COMPLETED" và currentSrsUrl có giá trị
    → hiển thị nút "Chuyển sang bước Đăng tin tuyển dụng".

LUỒNG 3 – Đánh giá Rủi ro AI (Stateless – không có bộ nhớ):
  · Client gửi kèm toàn bộ nội dung SRS (currentSrsContent) trong mỗi request.
  · AI phân tích và trả về cảnh báo rủi ro (riskLevel) cùng gợi ý điều chỉnh.
  · AI có thể cập nhật lại SRS và trả về URL mới qua currentSrsUrl.

📌 Lưu ý: Các tính năng AI như Trích xuất Kỹ năng, Đề xuất Ứng viên, Sinh Hợp đồng Nháp
   được gọi ngầm ở module Job và Contract. FE KHÔNG cần gọi trực tiếp.
```

---

### `API-AI-01` · Lấy Danh sách Câu hỏi Ban đầu (Base Questions)

```
GET /api/v1/ai/base-questions
Role: CLIENT
```

Gọi **1 lần** khi Client bắt đầu tạo dự án mới. Kết quả dùng để FE dựng form động thu thập thông tin ban đầu.

**Response Body:**
```json
{
  "code": 1000,
  "result": [
    {
      "id":              "q1",
      "type":            "MULTIPLE_CHOICE",
      "questionText":    "Dự án của bạn thuộc lĩnh vực nào?",
      "options":         ["E-commerce", "EdTech", "Fintech"],
      "allowCustomInput": true,
      "hint":            "Chọn một lĩnh vực chính"
    }
  ]
}
```

---

### `API-AI-02` · Chat với AI BA – Giao tiếp & Sinh tài liệu SRS

```
POST /api/v1/ai/ba-chat
Role: CLIENT
```

Gọi mỗi lần Client gửi một tin nhắn trong phiên chat. Backend tự động duy trì ngữ cảnh hội thoại qua `sessionId`.

**Validation (bắt buộc):**
- `sessionId`: FE tự sinh UUID cho mỗi phiên dự án, gửi kèm trong suốt cuộc hội thoại.
- `userMessage`: Nội dung tin nhắn của Client (không được để trống).

**Request Body:**
```json
{
  "sessionId":   "uuid-phien-chat-du-an-a",
  "userMessage": "Tôi muốn làm app giao đồ ăn giống ShopeeFood, tích hợp VNPay."
}
```

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "status":        "ASKING",       // Hoặc "COMPLETED" khi AI đủ thông tin
    "aiMessage":     "Bạn muốn app cho iOS, Android hay cả hai?",
    "questions":     [],             // Danh sách câu hỏi follow-up nếu có
    "srsContent":    null,           // Nội dung SRS dạng text (nếu đã sinh xong)
    "currentSrsUrl": null,           // URL file SRS trên Cloudinary (nếu đã upload)
    "riskLevel":     null
  }
}
```

> 🎯 **Logic FE:** Sau mỗi response, kiểm tra:
> - `status = "COMPLETED"` **VÀ** `currentSrsUrl != null` → hiển thị nút "Đăng tin tuyển dụng", truyền URL vào form tạo Job.
> - `status = "ASKING"` → tiếp tục hiển thị ô chat cho Client nhập liệu.

---

### `API-AI-03` · Chat Đánh giá Rủi ro (AI Risk Assessor)

```
POST /api/v1/ai/risk-chat
Role: CLIENT
```

Cuộc hội thoại **đơn lượt (Stateless)**. Mỗi request phải gửi kèm toàn bộ nội dung SRS hiện tại.

**Validation (bắt buộc):**
- `sessionId`: UUID định danh phiên.
- `userMessage`: Yêu cầu điều chỉnh của Client.
- `currentSrsContent`: Toàn bộ nội dung tài liệu SRS hiện tại (bắt buộc).

**Request Body:**
```json
{
  "sessionId":         "uuid-phien-chat-du-an-a",
  "userMessage":       "Tôi muốn ngân sách 1000$ và xong trong 2 tuần.",
  "currentSrsContent": "Tài liệu SRS chi tiết của app giao đồ ăn..."
}
```

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "status":        "NEGOTIATING",
    "aiMessage":     "Với tích hợp VNPay và real-time GPS, 2 tuần là rủi ro rất cao. Đề xuất tăng lên 1 tháng.",
    "questions":     [],
    "srsContent":    "Tài liệu SRS đã được AI điều chỉnh...",
    "currentSrsUrl": "https://res.cloudinary.com/.../srs-ten-du-an.txt",
    "riskLevel":     "HIGH_RISK"    // LOW_RISK | MEDIUM_RISK | HIGH_RISK
  }
}
```

---

## III. Module Storage – Lưu trữ Tài liệu SRS

### Luồng nghiệp vụ

```
· Client chỉnh sửa nội dung SRS thủ công (hoặc lấy từ AI) → FE cần lưu thành file vật lý trên Cloud.
· FE gọi API upload, Backend nhận chuỗi nội dung, chuyển thành mảng byte UTF-8
  và upload lên Cloudinary dưới dạng file .txt thô.
· File được lưu vào thư mục srs/ trên Cloudinary.
· Backend trả về secure_url → FE dùng URL này điền vào trường srsDocumentUrl khi tạo Job.
```

---

### `API-ST-01` · Upload Tài liệu SRS lên Cloud

```
POST /api/v1/storage/upload-srs
Role: CLIENT
```

Chuyển đổi chuỗi nội dung SRS thành file `.txt` và upload lên Cloudinary. Trả về URL dưới dạng **string trực tiếp** (không phải object).

**Validation (bắt buộc):**
- `content`: Nội dung file SRS dạng text/Markdown (không được để trống).
- `fileName`: Tên file (không bao gồm đuôi `.txt`, hệ thống tự thêm).

**Request Body:**
```json
{
  "content":  "# Tài liệu Yêu cầu Hệ thống (SRS)\n\n1. Chức năng đăng nhập...",
  "fileName": "srs-du-an-app-ban-hang-123"
}
```

**Response Body:**
```json
{
  "code":    1000,
  "message": "Success",
  "result":  "https://res.cloudinary.com/sam-be/raw/upload/v123/srs/srs-du-an-app-ban-hang-123.txt"
             // result là string URL trực tiếp, KHÔNG phải object
}
```

---

## IV. Module Profile – Quản lý Hồ sơ Người dùng

### Luồng nghiệp vụ

```
· GET Profile: Nếu người dùng chưa từng cập nhật profile (chưa có bản ghi trong DB),
  Backend KHÔNG báo lỗi mà trả về một đối tượng Profile rỗng (các field = null).

· PUT Profile: Cập nhật bản ghi hiện có, hoặc tạo mới nếu chưa tồn tại (Upsert).
```

> ⚠️ **ĐẶC BIỆT – Cơ chế "Ghi đè hoàn toàn" (Replace-All) cho danh sách Kỹ năng Freelancer:**
> - Mỗi lần PUT, Backend **XÓA SẠCH** toàn bộ kỹ năng cũ trong DB rồi lưu mới lại toàn bộ danh sách mới.
> - FE **BẮT BUỘC** phải gửi lên toàn bộ danh sách kỹ năng hiện có mỗi lần update.
> - Nếu gửi thiếu → kỹ năng không gửi sẽ **bị xóa vĩnh viễn**.
> - Backend tự động kiểm tra `skillId` có tồn tại trong hệ thống không.

---

### A. Profile Freelancer

#### `API-PF-01` · Lấy thông tin Profile Freelancer

```
GET /api/v1/profiles/freelancer/me
Role: FREELANCER
```

Trả về thông tin hồ sơ của Freelancer đang đăng nhập. Nếu chưa có profile, trả về object rỗng (không lỗi).

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "id":           "uuid-profile-id",
    "userId":       "uuid-user-id",
    "fullName":     "Nguyễn Văn A",
    "email":        "nguyenvana@example.com",
    "headline":     "Chuyên gia Frontend React",
    "bio":          "Mô tả bản thân...",
    "hourlyRate":   20.50,
    "githubUrl":    "https://github.com/username",
    "portfolioUrl": "https://portfolio.com",
    "skills": [
      {
        "skillId":           1,
        "skillName":         "React",
        "yearsOfExperience": 3
      }
    ]
  }
}
```

---

#### `API-PF-02` · Cập nhật Profile Freelancer

```
PUT /api/v1/profiles/freelancer/me
Role: FREELANCER
```

Cập nhật (hoặc khởi tạo lần đầu) hồ sơ Freelancer. Toàn bộ danh sách `skills` được **ghi đè hoàn toàn** mỗi lần gọi.

**Validation (bắt buộc):**
- `headline`: Không được null hoặc trống.
- `skills[].skillId`: Không được null, phải tồn tại trong hệ thống.
- `skills[].yearsOfExperience`: Không được null, không được âm.

**Request Body:**
```json
{
  "headline":     "Chuyên gia Frontend React",   // BẮT BUỘC
  "bio":          "Mô tả bản thân...",
  "hourlyRate":   20.50,
  "githubUrl":    "https://github.com/username",
  "portfolioUrl": "https://portfolio.com",
  "skills": [                                    // BẮT BUỘC GỬI ĐỦ TOÀN BỘ
    {
      "skillId":           1,    // BẮT BUỘC
      "yearsOfExperience": 3     // BẮT BUỘC, >= 0
    }
  ]
}
```

**Response Body:** Trả về đối tượng Profile sau khi cập nhật – cấu trúc giống `API-PF-01`.

---

### B. Profile Client

#### `API-PF-03` · Lấy thông tin Profile Client

```
GET /api/v1/profiles/client/me
Role: CLIENT
```

Trả về thông tin hồ sơ của Client đang đăng nhập. Nếu chưa có profile, trả về object rỗng (không lỗi).

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "id":          "uuid-profile-id",
    "userId":      "uuid-user-id",
    "fullName":    "Trần Thị B",
    "email":       "tranthib@example.com",
    "companyName": "Công ty ABC",
    "industry":    "Fintech",
    "websiteUrl":  "https://company.com",
    "description": "Mô tả công ty..."
  }
}
```

---

#### `API-PF-04` · Cập nhật Profile Client

```
PUT /api/v1/profiles/client/me
Role: CLIENT
```

**Validation (bắt buộc):**
- `companyName`: Không được null hoặc trống.

**Request Body:**
```json
{
  "companyName": "Công ty ABC",    // BẮT BUỘC
  "industry":    "IT Outsourcing",
  "websiteUrl":  "https://company.com",
  "description": "Mô tả công ty..."
}
```

**Response Body:** Trả về đối tượng Profile sau khi cập nhật – cấu trúc giống `API-PF-03`.

---

## V. Module Job – Quản lý Công việc & Tuyển dụng AI

### Luồng nghiệp vụ

```
LUỒNG 1 – Tạo Công việc:
  · Client POST /api/v1/jobs với srsDocumentUrl.
  · Backend tự động tải file SRS từ URL → gọi AI bóc tách danh sách Skills cần thiết.
  · Job được tạo với trạng thái OPEN.
  · Nếu isUrgentHiring = true → Backend kích hoạt luồng AI Headhunter (xem Luồng 2).

LUỒNG 2 – AI Headhunter (chỉ khi isUrgentHiring = true):
  · BE quét danh sách Freelancer có gói PRO DEV đang còn hạn (SubscriptionStatus = ACTIVE).
  · Tính điểm sơ bộ dựa trên độ khớp kỹ năng → chọn Top 5.
  · Ẩn danh (mã hóa UUID → A, B, C...) và gửi AI chấm điểm chi tiết, sinh nhận xét.
  · Lưu kết quả vào bảng AI Job Recommendation (RecommendationStatus = PENDING).

LUỒNG 3 – Client Mời Ứng viên (1-Touch Invite):
  · Client xem danh sách Top 5 qua API-JB-04, bấm "Mời" → gọi API-JB-05.
  · Backend chuyển trạng thái Recommendation: PENDING → INVITED.
  · Backend bắn thông báo WebSocket đến Freelancer: /topic/users/{freelancerId}/notifications.

LUỒNG 4 – Freelancer Phản hồi Lời mời:
  · Từ chối: RecommendationStatus → REJECTED. Không có side-effect.
  · Chấp nhận: RecommendationStatus → ACCEPTED. Job VẪN GIỮ NGUYÊN trạng thái OPEN.
    → Backend tự động tạo (hoặc lấy ra) ChatRoom giữa Client & Freelancer.
    → Trả về roomId để FE chuyển hướng vào màn hình Chat & bắt đầu thương lượng hợp đồng.

LUỒNG 5 – Hủy Job:
  · Chỉ được hủy khi Job ở trạng thái OPEN.
  · Chỉ Client chủ sở hữu mới được hủy (JobAccessGuard kiểm tra).
```

---

### `API-JB-01` · Tạo Công việc Mới (Create Job)

```
POST /api/v1/jobs
Role: CLIENT
```

Tạo bài đăng tuyển dụng mới. Backend tự động gọi AI để trích xuất kỹ năng từ file SRS. Nếu `isUrgentHiring = true`, hệ thống kích hoạt luồng AI Headhunter tìm Top 5 Freelancer phù hợp.

**Validation (bắt buộc):**
- `title`: Không được trống.
- `description`: Không được trống.
- `budgetMin` / `budgetMax`: Không được null.
- `deadline`: Phải là thời điểm trong tương lai.
- `srsDocumentUrl`: Không được trống – URL file SRS đã upload lên Cloudinary.

**Request Body:**
```json
{
  "title":          "Hệ thống ví điện tử",
  "description":    "Cần chuyên gia hệ thống tích hợp VNPay",
  "budgetMin":       1000.00,
  "budgetMax":       3000.00,
  "deadline":       "2027-01-01T00:00:00",
  "srsDocumentUrl": "https://res.cloudinary.com/.../srs-ten-du-an.txt",  // BẮT BUỘC
  "isFeatured":      false,
  "isUrgentHiring":  true,    // true → kích hoạt AI Headhunter tìm Top 5
  "requiresAiQa":    false
}
```

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "jobId":  "uuid-job-id",
    "title":  "Hệ thống ví điện tử",
    "status": "OPEN",
    "extractedSkills": [
      { "skillId": 5,  "skillName": "Java" },
      { "skillId": 12, "skillName": "VNPay Integration" }
    ]
    // ... các field khác của Job
  }
}
```

---

### `API-JB-02` · Hủy Công việc (Cancel Job)

```
PATCH /api/v1/jobs/{jobId}/cancel
Role: CLIENT (chỉ chủ bài đăng – JobAccessGuard)
```

Chuyển trạng thái Job sang `CANCELLED`. Chỉ áp dụng khi Job đang ở trạng thái `OPEN`. Nếu Job đang `NEGOTIATING`, `IN_PROGRESS` hoặc đã `CANCELLED`/`COMPLETED` → hệ thống trả lỗi.

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "jobId":  "uuid-job-id",
    "status": "CANCELLED"
  }
}
```

---

### `API-JB-03` · Lấy Danh sách Công việc của Client (My Jobs)

```
GET /api/v1/jobs/client/me
Role: CLIENT
```

Lấy toàn bộ danh sách các Job đã đăng của Client đang đăng nhập.

**Response Body:**
```json
{
  "code": 1000,
  "result": [
    {
      "jobId":    "uuid-job-id",
      "title":    "Hệ thống ví điện tử",
      "status":   "OPEN",
      "deadline": "2027-01-01T00:00:00"
      // ... các field khác
    }
  ]
}
```

---

### `API-JB-04` · Lấy Danh sách Ứng viên AI Đề xuất (Recommendations)

```
GET /api/v1/jobs/{jobId}/recommendations
Role: CLIENT (chỉ chủ bài đăng mới xem được)
```

Xem danh sách Top 5 Freelancer mà AI đề xuất cho Job, bao gồm điểm match, nhận xét AI, trạng thái lời mời.

**Response Body:**
```json
{
  "code": 1000,
  "result": [
    {
      "id":           "uuid-recommendation-id",
      "freelancerId": "uuid-freelancer",
      "fullName":     "Nguyễn Văn A",
      "headline":     "Senior Backend Developer",
      "matchScore":   95.5,
      "aiComment":    "Ứng viên rất phù hợp vì có kinh nghiệm tích hợp VNPay",
      "status":       "PENDING",    // PENDING | INVITED | ACCEPTED | REJECTED
      "skills": [
        { "skillName": "Java", "yearsOfExperience": 5 }
      ]
    }
  ]
}
```

---

### `API-JB-05` · Client Gửi Lời mời cho Ứng viên (1-Touch Invite)

```
POST /api/v1/jobs/{jobId}/recommendations/{recId}/invite
Role: CLIENT
```

Gửi lời mời làm việc đến Freelancer được AI đề xuất. `recId` là `id` của bản ghi Recommendation lấy từ `API-JB-04`. **Không có Request Body.** Sau khi gọi thành công, hệ thống tự động bắn thông báo WebSocket đến Freelancer.

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "recommendationId": "uuid-rec-id",
    "status":           "INVITED"
  }
}
```

---

### `API-JB-06` · Freelancer Chấp nhận Lời mời

```
POST /api/v1/jobs/{jobId}/recommendations/{recId}/accept
Role: FREELANCER (chỉ tài khoản được mời mới gọi được)
```

Freelancer chấp nhận lời mời. Job **VẪN GIỮ NGUYÊN** trạng thái `OPEN` (chưa chốt). Backend tự động tạo (hoặc lấy ra) phòng Chat giữa Client và Freelancer, trả về `roomId` để FE chuyển hướng vào màn hình thương lượng.

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "roomId": "uuid-chat-room-id"    // FE dùng roomId này để mở màn hình Chat
  }
}
```

> 🎯 **Logic FE:** Sau khi nhận `roomId` → lưu lại và navigate đến màn hình Chat. Đây là điểm khởi đầu của luồng thương lượng hợp đồng.

---

### `API-JB-07` · Freelancer Từ chối Lời mời

```
POST /api/v1/jobs/{jobId}/recommendations/{recId}/reject
Role: FREELANCER (chỉ tài khoản được mời mới gọi được)
```

Freelancer từ chối lời mời. `RecommendationStatus` chuyển sang `REJECTED`. Không có side-effect.

**Response Body:**
```json
{
  "code":    1000,
  "message": "Success",
  "result":  null    // Void – chỉ cần kiểm tra code = 1000 là đủ
}
```

---

### WebSocket – Module Job (Notification)

#### 🔔 LẮNG NGHE · `/topic/users/{freelancerId}/notifications`

Kênh riêng cho từng Freelancer. Client bấm "Mời" → hệ thống push thông báo real-time về kênh này. FE dùng trường `type` để hiển thị popup, dùng `jobId` để điều hướng xem chi tiết Job.

**Payload nhận được:**
```json
{
  "type":       "1_TOUCH_INVITE",
  "jobId":      "uuid-job-id",
  "jobTitle":   "Hệ thống ví điện tử",
  "matchScore": 95.5,
  "message":    "Khách hàng vừa chọn bạn cho dự án này. Bấm nhận việc ngay!",
  "timestamp":  "2026-09-27T22:00:00"
}
```

---

## VI. Module Chat – Nhắn tin Real-time

### Luồng nghiệp vụ

```
· ChatRoom được Backend tự động tạo khi Freelancer chấp nhận lời mời (API-JB-06).
  FE KHÔNG gọi API tạo phòng chat thủ công.
· Một phòng chat gắn liền với 1 Job, 1 Client, 1 Freelancer.
· Bảo mật: Nếu người dùng gọi API không phải Client/Freelancer của phòng đó → lỗi FORBIDDEN_ACTION.

THỨ TỰ KHỞI TẠO MÀN HÌNH CHAT (FE thực hiện tuần tự):
  Bước 1: Gọi REST API GET lịch sử tin nhắn (API-CH-01) để load tin nhắn cũ.
  Bước 2: Kết nối WebSocket và Subscribe vào /topic/chat/{roomId} để nhận tin mới.
  Bước 3: Sẵn sàng gửi tin qua WebSocket /app/chat/{roomId}/send.
```

---

### `API-CH-01` · Lấy Lịch sử Tin nhắn (Load Chat History)

```
GET /api/v1/chat/rooms/{roomId}/messages
Role: CLIENT hoặc FREELANCER (phải là thành viên của phòng chat roomId)
```

Gọi **duy nhất 1 lần** khi user vừa mở màn hình chat, để load toàn bộ tin nhắn cũ. Danh sách được sắp xếp theo thời gian **tăng dần** (tin cũ ở trên, tin mới ở dưới).

**Response Body:**
```json
{
  "code": 1000,
  "result": [
    {
      "id":         "uuid-tin-nhan-1",
      "roomId":     "uuid-phong-chat",
      "senderId":   "uuid-nguoi-gui",
      "senderName": "Nguyễn Văn A",
      "content":    "Chào bạn, mình bắt đầu trao đổi về dự án nhé!",
      "createdAt":  "2026-09-27T10:00:00"
    },
    {
      "id":         "uuid-tin-nhan-2",
      "roomId":     "uuid-phong-chat",
      "senderId":   "uuid-nguoi-gui-2",
      "senderName": "Trần Thị B",
      "content":    "Vâng, bạn gửi mình xem chi tiết nhé.",
      "createdAt":  "2026-09-27T10:05:00"
    }
  ]
}
```

---

### WebSocket – Module Chat

#### 🔔 LẮNG NGHE · `/topic/chat/{roomId}`

Kênh nhận tất cả tin nhắn của phòng chat. FE của cả 2 bên đều subscribe vào đây. Payload nhận được có cấu trúc giống hệt object trong mảng của `API-CH-01` (`ChatMessageDto`).

> 📌 Lưu ý: Các tin nhắn **hệ thống** (thông báo tạo hợp đồng, thay đổi thông số, ký kết) cũng được gửi qua kênh này.

**Payload nhận được:**
```json
{
  "id":         "uuid-tin-nhan-moi",
  "roomId":     "uuid-phong-chat",
  "senderId":   "uuid-nguoi-gui",
  "senderName": "Nguyễn Văn A",
  "content":    "Đây là tin nhắn realtime!",
  "createdAt":  "2026-09-27T10:19:00"
}
```

#### 📤 GỬI ĐI · `/app/chat/{roomId}/send`

Kênh gửi tin nhắn mới. Dùng khi người dùng bấm nút "Gửi". FE đẩy payload qua STOMP, **KHÔNG gọi HTTP API**.

**Payload gửi lên:**
```json
{
  "senderId": "uuid-cua-user-hien-tai",
  "content":  "Nội dung tin nhắn người dùng vừa gõ..."
}
```

---

## VII. Module Contract – Thương lượng & Ký kết Hợp đồng

### Luồng nghiệp vụ

```
LUỒNG 1 – Khởi tạo Hợp đồng Nháp bằng AI:
  · FE gọi API POST (API-CT-01) để yêu cầu sinh hợp đồng cho một roomId.
  · BE kiểm tra quyền truy cập, kiểm tra Job đã có hợp đồng chưa
    (nếu có → lỗi DUPLICATE_RESOURCE).
  · BE chuyển trạng thái Job: OPEN → NEGOTIATING (khóa Job, không nhận thêm ứng viên).
  · AI đọc file SRS + ngân sách → sinh bản nháp. Hợp đồng lưu với ContractStatus = DRAFT.
  · BE tự động gửi tin nhắn hệ thống vào phòng Chat thông báo cho người còn lại.

LUỒNG 2 – Cò kè Ngã giá (Sync Contract) qua WebSocket:
  · Khi một bên chỉnh sửa agreedAmount, revisionLimit hoặc termsAndConditions,
    FE gửi toàn bộ payload qua WebSocket /app/contracts/{contractId}/sync.
  · Cơ chế chống lật lọng: Backend nhận payload → lưu vào DB →
    reset cả 2 cờ chữ ký (clientAgreed = false, freelancerAgreed = false).
  · BE phát thanh dữ liệu hợp đồng mới (type: "SYNC") về /topic/contracts/{contractId}.
  · BE đồng thời gửi tin nhắn hệ thống vào phòng Chat thông báo thay đổi.

LUỒNG 3 – Ký duyệt & Chốt Hợp đồng (Sign Contract) qua WebSocket:
  · User bấm "Đồng ý" hoặc "Hủy đồng ý" → FE gửi payload qua /app/contracts/{contractId}/sign.
  · BE ghi nhận chữ ký cho đúng role (Client hoặc Freelancer).
  · Cơ chế chốt: Nếu CẢ 2 BÊN đều isAgreed = true → BE thực hiện:
      1. ContractStatus → ACTIVE
      2. JobStatus → IN_PROGRESS
      3. Phát thanh payload với type = "COMPLETED" về /topic/contracts/{contractId}.
      4. Gửi tin chúc mừng vào phòng Chat.
```

---

### `API-CT-01` · Khởi tạo Hợp đồng Nháp bằng AI (AI Draft)

```
POST /api/v1/chat/rooms/{roomId}/contracts/ai-draft
Role: CLIENT hoặc FREELANCER (phải là thành viên phòng chat)
```

Kích hoạt AI sinh bản nháp hợp đồng. **Không có Request Body.** Sau khi gọi thành công, Job chuyển sang `NEGOTIATING` và hợp đồng được tạo với trạng thái `DRAFT`.

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "contractId":        "uuid-contract-id",
    "jobId":             "uuid-job-id",
    "agreedAmount":       2000.00,
    "revisionLimit":      3,
    "termsAndConditions": "# HỢP ĐỒNG PHÁT TRIỂN...\n\nĐiều 1..."
  }
}
```

> ⚠️ **FE lưu lại `contractId`** từ response này để dùng cho các kênh WebSocket Contract.  
> Sau khi API thành công → subscribe ngay vào `/topic/contracts/{contractId}`.

---

### WebSocket – Module Contract

#### 🔔 LẮNG NGHE · `/topic/contracts/{contractId}`

Kênh đồng bộ trạng thái hợp đồng real-time. FE dùng data này để re-render ô giá, số lần sửa, trạng thái chữ ký.

> ⚠️ **Đặc biệt:** Kiểm tra trường `type` sau mỗi payload nhận về:
> - `type = "COMPLETED"` → **khóa toàn bộ UI nhập liệu và nút bấm** ngay lập tức.

**Payload nhận được:**
```json
{
  "type":               "SYNC",    // "SYNC" | "SIGN" | "COMPLETED"
  "agreedAmount":        1800.00,
  "revisionLimit":       3,
  "termsAndConditions":  "# Hợp đồng...",
  "clientAgreed":        false,
  "freelancerAgreed":    false,
  "contractStatus":      "DRAFT"   // "DRAFT" | "ACTIVE"
}
```

#### 📤 GỬI ĐI · `/app/contracts/{contractId}/sync`

Gửi yêu cầu cập nhật thông số ngã giá. Dùng khi user thay đổi giá tiền, số lần sửa hoặc nội dung hợp đồng.

> 💡 Nên dùng **debounce 300–500ms** để tránh spam server.

**Payload gửi lên:**
```json
{
  "senderId":           "uuid-user-hien-tai",
  "agreedAmount":        1500.00,
  "revisionLimit":       2,
  "termsAndConditions":  "# Văn bản hợp đồng mới..."
}
```

#### 📤 GỬI ĐI · `/app/contracts/{contractId}/sign`

Gửi trạng thái ký duyệt. Dùng khi user bấm nút "Đồng ý" (`isAgreed = true`) hoặc "Hủy đồng ý" (`isAgreed = false`). Backend tự động nhận biết role của `senderId` để ghi vào đúng cờ `clientAgreed` hay `freelancerAgreed`.

**Payload gửi lên:**
```json
{
  "senderId": "uuid-user-hien-tai",
  "isAgreed":  true    // true = Đồng ý | false = Hủy đồng ý
}
```

---

## VIII. Module Payment – Ký quỹ & Thanh toán

### Luồng nghiệp vụ

```
LUỒNG 1 – Thanh toán Ký quỹ (Escrow):
  · Sau khi Hợp đồng có trạng thái ACTIVE, hệ thống chờ Client thanh toán.
  · Client thanh toán qua VNPay/Gateway → hệ thống tạo bản ghi Payment
    với status = HELD_IN_ESCROW.
  · Mốc thời gian ký quỹ được ghi tại trường escrowHeldAt.
  · Chỉ Client thanh toán hoặc Freelancer thực hiện hợp đồng mới được xem
    chi tiết giao dịch (PaymentAccessGuard).

LUỒNG 2 – Giải ngân (Release Escrow):
  · Khi dự án hoàn thành và Client xác nhận nghiệm thu → Client gọi API giải ngân.
  · TUYỆT ĐỐI: Freelancer KHÔNG có quyền gọi API giải ngân (canRelease Guard kiểm tra).
  · Chỉ Client chủ hợp đồng (hoặc Admin) mới được release tiền.
  · PaymentStatus chuyển sang RELEASED, thời gian ghi vào releasedAt.

LUỒNG 3 – Gói Dịch vụ (Service Packages):
  · Admin định nghĩa các gói dịch vụ (PRO DEV cho Freelancer, Đăng tin VIP cho Client).
  · Hai cơ chế tính phí:
      - Fixed Price:   isDynamicPrice = false → lấy giá ở cột price (VD: 59,000 VND).
      - Dynamic Price: isDynamicPrice = true  → tính theo percentageFee (VD: 3.0 = 3% hợp đồng).
  · Khi User mua gói thành công → tạo bản ghi UserSubscription
    (status = ACTIVE, có startDate & endDate).
  · AI Headhunter tự động quét bảng này để ưu tiên Freelancer
    đang có gói PRO DEV còn hiệu lực.
```

> ⚠️ **Lưu ý:** Các REST API endpoint cụ thể cho tích hợp VNPay và mua gói sẽ được đặc tả chi tiết trong Sprint tiếp theo. FE cần phối hợp với BE để thống nhất Payload khi tích hợp.

---

## IX. Tổng hợp tất cả kênh WebSocket

Cổng kết nối: **`/ws`** (hỗ trợ SockJS/STOMP)

| Kênh (Destination) | Loại | Module | Payload |
|---|---|---|---|
| `/topic/users/{freelancerId}/notifications` | 🔔 Subscribe | Notification | `NotificationMessage` (type, jobId, jobTitle, matchScore, message, timestamp) |
| `/topic/chat/{roomId}` | 🔔 Subscribe | Chat | `ChatMessageDto` (id, roomId, senderId, senderName, content, createdAt) |
| `/app/chat/{roomId}/send` | 📤 Publish | Chat | `SendMessagePayload` (senderId, content) |
| `/topic/contracts/{contractId}` | 🔔 Subscribe | Contract | `ContractBroadcastData` (type, agreedAmount, revisionLimit, termsAndConditions, clientAgreed, freelancerAgreed, contractStatus) |
| `/app/contracts/{contractId}/sync` | 📤 Publish | Contract | `ContractSyncPayload` (senderId, agreedAmount, revisionLimit, termsAndConditions) |
| `/app/contracts/{contractId}/sign` | 📤 Publish | Contract | `ContractSignPayload` (senderId, isAgreed) |

---

## X. Từ điển Trạng thái Hệ thống (Enum Dictionary)

FE dùng bộ enum này để đồng bộ logic hiển thị: màu sắc badge, vô hiệu hóa nút bấm, bộ lọc tìm kiếm.

### A. Trạng thái Công việc – `JobStatus`

| Giá trị | Ý nghĩa | Hành động FE gợi ý |
|---|---|---|
| `OPEN` | Job vừa đăng, đang tìm kiếm ứng viên | Badge xanh \| Cho phép hủy Job \| Hiển thị danh sách đề xuất |
| `NEGOTIATING` | Đang thương lượng hợp đồng (Job bị khóa) | Badge vàng \| Ẩn nút hủy \| Chuyển hướng vào màn hình Chat |
| `IN_PROGRESS` | Hợp đồng đã ký, dự án đang thực hiện | Badge xanh lam \| Hiển thị tiến độ dự án |
| `COMPLETED` | Dự án đã nghiệm thu thành công | Badge tím \| Kích hoạt nút Giải ngân |
| `CANCELLED` | Job bị hủy bỏ | Badge đỏ \| Không cho thao tác thêm |

### B. Trạng thái Hợp đồng – `ContractStatus`

| Giá trị | Ý nghĩa | Hành động FE gợi ý |
|---|---|---|
| `DRAFT` | Bản nháp, đang cò kè ngã giá | Cho phép chỉnh sửa, Sync \| Hiển thị nút Đồng ý / Hủy ký |
| `ACTIVE` | Cả 2 bên đã ký xong | Khóa toàn bộ ô nhập liệu \| Hiển thị "Đã có hiệu lực" |
| `COMPLETED` | Hoàn thành toàn bộ nghĩa vụ (đã thanh toán) | Hiển thị lịch sử, không cho thao tác |
| `CANCELLED` | Hợp đồng bị hủy | Badge đỏ \| Không cho thao tác thêm |

### C. Trạng thái Đề xuất AI – `RecommendationStatus`

| Giá trị | Ý nghĩa | Hành động FE gợi ý |
|---|---|---|
| `PENDING` | AI vừa đề xuất, Client chưa có hành động | Hiển thị nút "Mời" |
| `INVITED` | Client đã bấm mời, đang chờ Freelancer phản hồi | Disable nút Mời \| Hiển thị "Đang chờ phản hồi" |
| `ACCEPTED` | Freelancer đồng ý (phòng Chat đã mở) | Hiển thị nút "Vào phòng Chat" |
| `REJECTED` | Freelancer từ chối lời mời | Badge xám / đỏ \| Không cho thao tác |

### D. Trạng thái Thanh toán – `PaymentStatus`

| Giá trị | Ý nghĩa |
|---|---|
| `PENDING` | Khởi tạo hóa đơn, chờ Client chuyển tiền |
| `HELD_IN_ESCROW` | Tiền đã vào hệ thống, đang bị đóng băng ký quỹ |
| `RELEASED` | Tiền đã được giải ngân cho Freelancer |
| `REFUNDED` | Tiền được hoàn trả lại cho Client |

### E. Loại & Trạng thái Gói Dịch vụ

**`PackageType`**

| Giá trị | Ý nghĩa |
|---|---|
| `PAY_PER_USE` | Dịch vụ mua lẻ một lần (VD: Ghim tin nổi bật) |
| `SUBSCRIPTION` | Thuê bao theo thời gian (VD: Gói PRO DEV hàng tháng) |

**`SubscriptionStatus`**

| Giá trị | Ý nghĩa |
|---|---|
| `ACTIVE` | Gói đang còn hiệu lực |
| `EXPIRED` | Gói đã hết hạn |
| `CANCELLED` | Gói bị hủy giữa chừng |

---

## XI. Hạ tầng & Cấu hình Hệ thống

### 1. Cấu hình chung

- **Jackson ObjectMapper:** Hỗ trợ `JavaTimeModule` (LocalDateTime). DateTime trả về dưới dạng chuỗi ISO 8601 (VD: `"2026-09-27T10:00:00"`), **KHÔNG phải Unix timestamp**.
- **Cloudinary:** Lưu trữ file SRS (`.txt` thô) tại thư mục `srs/`. Config qua biến môi trường `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.
- **Swagger UI:** Xem tại `/swagger`, `/swagger-ui` hoặc `/docs`. Hỗ trợ JWT Bearer Auth và API Key (Header `X-API-Key`).

### 2. Tích hợp AI (Gemini)

- **Model:** Gemini – gọi qua `RestClient` đến endpoint `generateContent`.
- **Retry:** Tự động thử lại tối đa **4 lần** nếu Gemini API lỗi, với thời gian chờ **10 giây** giữa mỗi lần.
- **Làm sạch JSON:** Hệ thống tự động loại bỏ cú pháp Markdown thừa (` ```json `) từ response AI trước khi parse.

### 3. WebSocket Infrastructure

- **Endpoint:** `/ws` – hỗ trợ SockJS, CORS `*`
- **STOMP Broker:** Tiền tố `/topic` và `/queue` cho server → client; tiền tố `/app` cho client → server.

---

*Tài liệu được tổ chức lại và đặc tả đầy đủ dành cho đội Frontend | Cập nhật: 09/2026*
