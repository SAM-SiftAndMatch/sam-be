# TÀI LIỆU ĐẶC TẢ API PAYMENT & SUBSCRIPTION
**Hệ thống Freelance Platform**
Dành cho Backend + Frontend Developer | Phiên bản 1.1 (Funding mới — thay thế ký quỹ 2 đợt cũ từ 10/2026)

> Cập nhật 10/2026: bỏ mô hình ký quỹ 2 đợt × 50%. Mô hình mới "nạp đủ mới chạy":
> client nạp 100% + freelancer cọc 2% trước khi dự án bắt đầu, cuối dự án freelancer nhận 90%,
> sàn giữ 10%. Chi tiết nghiệp vụ xem `SYSTEM_SPECIFICATION.md` PHỤ LỤC A.

> Tài liệu này là phần nối tiếp của `API_Specification_v1.md` (không sửa v1).
> Mọi quy ước chung của v1 đều giữ nguyên: wrapper `ApiResponse` (`code: 1000` = thành công, dữ liệu trong `result`),
> Bearer Token (`SecurityUtils.getCurrentUserId()` — FE **không** gửi `userId`),
> DateTime ISO 8601, WS qua `/ws` (SockJS/STOMP, `/app` → server, `/topic` → client).
> Quyết định nghiệp vụ đã chốt với user (04/10/2026): xem `PLAN_FE_BE_Integration.md` mục VII.

---

## Mục lục

1. [Tiền đề & Luồng nghiệp vụ](#1-tiền-đề--luồng-nghiệp-vụ)
2. [API-PM-01 · Client nạp 100% + URL VNPay](#2-api-pm-01--client-nạp-100--url-vnpay)
3. [API-PM-01b · Freelancer cọc 2% + URL VNPay](#3-api-pm-01b--freelancer-cọc-2--url-vnpay)
4. [API-PM-01c · AI đối chiếu số tiền](#4-api-pm-01c--ai-đối-chiếu-số-tiền)
5. [API-PM-01d · Bảng nạp tiền khởi động](#5-api-pm-01d--bảng-nạp-tiền-khởi-động)
6. [API-PM-01e · FE tự báo đã chuyển (chữa cháy thay IPN)](#6-api-pm-01e--fe-tự-báo-đã-chuyển-chữa-cháy-thay-ipn)
7. [API-PM-02 · IPN VNPay + tự mở dự án](#7-api-pm-02--ipn-vnpay--tự-mở-dự-án)
8. [API-PM-03 · Giải ngân Escrow](#8-api-pm-03--giải-ngân-escrow)
9. [API-PM-04 · Xem giao dịch theo hợp đồng](#9-api-pm-04--xem-giao-dịch-theo-hợp-đồng)
10. [API-SB-01 · Mua gói dịch vụ](#10-api-sb-01--mua-gói-dịch-vụ)
11. [API-SB-02 · Xem gói đang sở hữu](#11-api-sb-02--xem-gói-đang-sở-hữu)
12. [WebSocket – Sự kiện thanh toán](#12-websocket--sự-kiện-thanh-toán)
13. [Từ điển trạng thái & Mã lỗi](#13-từ-điển-trạng-thái--mã-lỗi)
14. [Biến môi trường (VNPAY Sandbox)](#14-biến-môi-trường-vnpay-sandbox)
15. [Gợi ý triển khai cho commit 6/7](#15-gợi-ý-triển-khai-cho-commit-67)

---

## 1. Tiền đề & Luồng nghiệp vụ

```
ĐIỀU KIỆN TIÊN QUYẾT (mọi API payment):
  · Contract phải ở trạng thái ACTIVE (cả 2 bên đã ký qua WS sign, xem API-CT-01 v1).
  · DRAFT / CANCELLED / COMPLETED → lỗi CONTRACT_NOT_ACTIVE (đề xuất 1018).

LUỒNG NẠP TIỀN KHỞI ĐỘNG (thay thế ký quỹ 2 đợt cũ — FE KHÔNG gửi amount):
  Ký đôi xong → Job sang AWAITING_PAYMENT (chờ nạp tiền, chưa chạy).
  0. FE mở màn nạp tiền: GET API-PM-01d (bảng tiền 2 bên + trạng thái) và POST API-PM-01c
     (AI đọc văn bản, đối chiếu với agreedAmount). Lệch → badge đỏ, CHẶN nút chuyển tiền.
  1. Client POST API-PM-01 {contractId}
       → BE đối chiếu AI lần nữa (lệch → từ chối), tính amount = agreedAmount × 100%
       → tạo Payment(type = CONTRACT_FUND, status = PENDING) + vnpayUrl → FE redirect VNPay.
     Freelancer POST API-PM-01b {contractId}
       → tương tự, amount = agreedAmount × 2% (làm tròn tới đồng).
  2. Mỗi bên trả VNPay xong → VNPay gọi IPN → API-PM-02
       → verify checksum → HELD_IN_ESCROW (escrowHeldAt) → push WS cho CẢ 2 bên
       → FE cập nhật UI realtime, KHÔNG polling.
  3. Khoản thứ hai về đủ → BE TỰ chuyển Job AWAITING_PAYMENT → IN_PROGRESS,
     push WS PROJECT_STARTED cho cả 2. Không ai phải bấm thêm nút "bắt đầu".
  4. Cuối dự án (luồng nghiệm thu — giai đoạn sau): thanh toán 1 lần duy nhất,
     freelancer nhận 90%, sàn 10%; cọc 2% hoàn trả khi xong, đền client khi bỏ job.

CHỐNG TRÙNG: mỗi contract chỉ có đúng 1 khoản CONTRACT_FUND và 1 khoản SECURITY_DEPOSIT
  (trừ hàng đã REFUNDED). Tạo trùng → lỗi ESCROW_PAYMENT_EXISTS (1019).

LUỒNG MUA GÓI (Q1.3 + business chốt 05/10/2026):
  User POST API-SB-01 {packageId, projectId?} (6 gói: 59k Ghim, 99k Tuyển gấp,
  59k Trọng tài Code, 249k BUSINESS, 149k PRO DEV, 299k AI QA Nâng cao)
    → gói lẻ: bắt buộc projectId của chính caller, endDate = NULL (theo vòng đời project)
    → gói tháng: endDate = startDate + 30 ngày, không gắn project
    → UserSubscription(status = ACTIVE)
    → AI Headhunter quét PRO DEV còn hạn để ưu tiên Freelancer (xem API-JB v1).
```

---

## 2. API-PM-01 · Client nạp 100% + URL VNPay

```
POST /api/v1/payments/fund
Role: CLIENT (phải là client của contract)
```

**Validation (bắt buộc):**
- `contractId`: không được null, Contract phải tồn tại (không → `RESOURCE_NOT_FOUND` 1007).
- Contract phải `ACTIVE` (không → `CONTRACT_NOT_ACTIVE` 1018).
- Caller phải là client của contract (không → `FORBIDDEN_ACTION` 1003).
- AI đối chiếu số trong văn bản với `agreedAmount`: lệch → `REQUEST_FAILED` 1005 (sửa hợp đồng cho khớp rồi làm lại).
- Chưa có khoản `CONTRACT_FUND` nào (trừ REFUNDED) cho contract (có → `ESCROW_PAYMENT_EXISTS` 1019).

**Request Body (chỉ `contractId` + `returnUrl` tùy chọn, tuyệt đối không `amount`):**
```json
{
  "contractId": "uuid-contract-id",
  "returnUrl": "https://app.../workspace/{roomId}/contract"
}
```

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "paymentId":   "uuid-payment-id",
    "contractId":  "uuid-contract-id",
    "paymentType": "CONTRACT_FUND",
    "amount":      10000000.00,
    "currency":    "VND",
    "status":      "PENDING",
    "vnpayUrl":    "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=1000000000&..."
  }
}
```

> `amount` do BE tính = `Contract.agreedAmount × 100%`.
> 🎯 **Logic FE:** nhận `vnpayUrl` → `window.location.href = vnpayUrl` (rời app sang VNPay).
> Không poll trạng thái — chờ WS `PAYMENT_ESCROW_HELD` (mục 8).

---

## 3. API-PM-01b · Freelancer cọc 2% + URL VNPay

```
POST /api/v1/payments/deposit
Role: FREELANCER (phải là freelancer của contract)
```

Mọi validation giống API-PM-01, khác duy nhất: `amount = agreedAmount × 2%` (làm tròn tới đồng),
`paymentType = SECURITY_DEPOSIT`. Xong việc đúng hạn được hoàn trả, bỏ job thì đền cho Client
(xử lý ở luồng nghiệm thu — giai đoạn sau).

---

## 4. API-PM-01c · AI đối chiếu số tiền

```
POST /api/v1/payments/contracts/{contractId}/verify-amount
Role: CLIENT hoặc FREELANCER của hợp đồng
```

AI đọc toàn văn điều khoản, trích tổng giá trị và so với `agreedAmount` (lệch quá 1.000 VNĐ là lệch).

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "extractedAmount": 10000000,
    "matches": true,
    "note": "Số trong văn bản khớp giá thỏa thuận."
  }
}
```

> 🎯 **Logic FE:** gọi 1 lần khi mở màn nạp tiền. `matches = false` → badge đỏ + disable nút chuyển tiền.
> BE cũng đối chiếu lại ngay trong API-PM-01/01b nên FE không thể lách.

---

## 5. API-PM-01d · Bảng nạp tiền khởi động

```
GET /api/v1/payments/funding/{contractId}
Role: CLIENT hoặc FREELANCER của hợp đồng
```

Trả về số tiền mỗi bên phải chuyển, dự kiến cuối dự án và trạng thái đã chuyển/chưa:

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "contractId": "uuid",
    "jobId": "uuid",
    "jobStatus": "AWAITING_PAYMENT",
    "agreedAmount": 10000000.00,
    "clientAmount": 10000000.00,
    "depositAmount": 200000.00,
    "freelancerPayout": 9000000.00,
    "platformFee": 1000000.00,
    "fundStatus": "HELD_IN_ESCROW",
    "depositStatus": null,
    "fundPaid": true,
    "depositPaid": false,
    "allPaid": false
  }
}
```

---

## 6. API-PM-01e · FE tự báo đã chuyển (chữa cháy thay IPN)

```
POST /api/v1/payments/{paymentId}/confirm
Role: đúng người trả của khoản đó (fund chỉ client, deposit chỉ freelancer)
```

Giống `confirm-payment` của mua gói: VNPay redirect về FE (`vnp_ResponseCode=00`), FE lưu `paymentId`
vào localStorage trước khi redirect và gọi API này kèm `txnRef` + `amountVnd` trên URL return.

**Request Body:**
```json
{
  "txnRef": "vnp_TxnRef trên URL return",
  "amountVnd": 1000000000
}
```

**Quy tắc BE:** đúng thành viên + đúng bên trả + payment đang `PENDING` + `txnRef` khớp
`paymentGatewayId` + `amountVnd` khớp `amount × 100` → chuyển `HELD_IN_ESCROW`, push WS
`PAYMENT_ESCROW_HELD`, chạy auto-start như IPN. Đã `HELD` rồi thì trả về luôn (idempotent với IPN).

---

## 7. API-PM-02 · IPN VNPay + tự mở dự án

```
POST /api/v1/payments/vnpay-ipn
Role: PUBLIC (không Bearer — VNPay server gọi; bảo mật bằng checksum HMAC-SHA512)
```

VNPay gọi với query params chuẩn (`vnp_TxnRef`, `vnp_Amount`, `vnp_ResponseCode`, `vnp_TransactionNo`, `vnp_SecureHash`, …).

**Xử lý BE:**
1. Verify `vnp_SecureHash` bằng `VNPAY_HASH_SECRET`. Sai → trả `{"RspCode":"97","Message":"Invalid signature"}` (lỗi `INVALID_PAYMENT_SIGNATURE`, đề xuất 1020).
2. Tìm payment theo `vnp_TxnRef` (= `paymentGatewayId`). Không thấy → `RspCode 01` (Order not found).
3. Đối chiếu `vnp_Amount` với `payment.amount × 100`. Lệch → `RspCode 04` (Invalid amount).
4. Nếu payment đã `HELD_IN_ESCROW` → `RspCode 02` (Order already confirmed — idempotent, không xử lý lại).
5. Nếu `vnp_ResponseCode == "00"` → `HELD_IN_ESCROW` (`escrowHeldAt = now`) → push WS `PAYMENT_ESCROW_HELD` cho cả client + freelancer.
   Ngược lại (thanh toán thất bại/hủy) → giữ nguyên `PENDING` (enum `PaymentStatus` không có `FAILED`).
6. **Tự mở dự án:** sau mỗi IPN thành công, BE kiểm tra nếu cả `CONTRACT_FUND` và `SECURITY_DEPOSIT`
   đều đã giữ → chuyển Job `AWAITING_PAYMENT` → `IN_PROGRESS` và push WS `PROJECT_STARTED` cho cả 2.
7. Thành công → trả `{"RspCode":"00","Message":"Confirm Success"}`.

> ⚠️ Môi trường hiện tại là **VNPAY Sandbox** (Q1.2). Không hardcode key/URL (mục 10).

---

## 8. API-PM-03 · Giải ngân Escrow

```
POST /api/v1/payments/{paymentId}/release
Role: CLIENT chủ hợp đồng hoặc Admin (PaymentAccessGuard.canRelease — Freelancer gọi → 403)
```

Không có Request Body. Chỉ release được payment đang `HELD_IN_ESCROW` (trạng thái khác → `REQUEST_FAILED` 1005).

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "paymentId":  "uuid-payment-id",
    "contractId": "uuid-contract-id",
    "status":     "RELEASED",
    "releasedAt": "2026-10-05T10:00:00"
  }
}
```

> Sau release → BE push WS `PAYMENT_RELEASED` cho cả 2 bên (mục 8).
> 🎯 **Logic FE:** chỉ hiển thị nút "Giải ngân" khi caller là Client và payment đang `HELD_IN_ESCROW`
> (ẩn với Freelancer — guard BE đã chặn, FE ẩn để UX đúng).

---

## 9. API-PM-04 · Xem giao dịch theo hợp đồng

```
GET /api/v1/payments/contract/{contractId}
Role: CLIENT hoặc FREELANCER của hợp đồng, hoặc Admin (PaymentAccessGuard.canAccess)
```

Trả về danh sách (1 khoản `CONTRACT_FUND` 100% + 1 khoản `SECURITY_DEPOSIT` 2%), sắp xếp theo thời gian tạo tăng dần.

**Response Body:**
```json
{
  "code": 1000,
  "result": [
    {
      "paymentId":   "uuid-payment-1",
      "contractId":  "uuid-contract-id",
      "paymentType": "CONTRACT_FUND",
      "amount":      1000.00,
      "currency":    "VND",
      "status":      "RELEASED",
      "escrowHeldAt": "2026-10-04T10:00:00",
      "releasedAt":   "2026-10-05T10:00:00"
    }
  ]
}
```

---

## 10. API-SB-01 · Mua gói dịch vụ

```
POST /api/v1/subscriptions/purchase
Role: CLIENT hoặc FREELANCER (BE tự trích userId — FE không gửi)
```

**Validation (bắt buộc):**
- `packageId`: không null, gói phải tồn tại và `isActive = true` (không → `PACKAGE_NOT_AVAILABLE` 1021).
- Gói `PAY_PER_USE` (Ghim 59k, Tuyển gấp 99k, Trọng tài Code 59k): `projectId` bắt buộc
  (thiếu → `VALIDATION_ERROR` 1001); project phải tồn tại (không → `RESOURCE_NOT_FOUND` 1007)
  và thuộc về caller; chỉ role `CLIENT` được mua (Freelancer → `FORBIDDEN_ACTION` 1003).
- Gói `SUBSCRIPTION`: không dùng `projectId` (gửi kèm sẽ bị bỏ qua).

**Request Body (Q1.3 + business chốt 05/10/2026):**
```json
{
  "packageId": "f0000000-0000-0000-0000-000000000006",
  "projectId": "uuid-job-id // BẮT BUỘC với PAY_PER_USE, bỏ trống với SUBSCRIPTION"
}
```

**Response Body (5 trường Q1.3 + `targetProjectId`):**
```json
{
  "code": 1000,
  "result": {
    "id":              "uuid-subscription-id",
    "packageId":       "f0000000-0000-0000-0000-000000000006",
    "status":          "ACTIVE",
    "startDate":       "2026-10-04T10:00:00",
    "endDate":         "2026-11-04T10:00:00",
    "targetProjectId": null
  }
}
```

**Quy tắc `endDate` / `targetProjectId` (BE thực hiện, đã chốt):**
- `SUBSCRIPTION` (BUSINESS 249k, PRO DEV 149k, AI QA Nâng cao 299k — thuê bao tháng):
  `endDate = startDate + 30 ngày`, `targetProjectId = null`.
- `PAY_PER_USE` (Ghim 59k, Tuyển gấp 99k, Trọng tài Code 59k — đúng 1 project):
  `endDate = null` (sống theo vòng đời project, không hạn ngày),
  `targetProjectId = projectId` đã mua.

> 6 gói trong DB (5 gói seed `V4` + 299k seed `V12`). Giá giữ nguyên `SYSTEM_SPECIFICATION §II.3` (Q1.3).

---

## 11. API-SB-02 · Xem gói đang sở hữu

```
GET /api/v1/subscriptions/me
Role: CLIENT hoặc FREELANCER (BE tự trích userId)
```

Trả về danh sách subscription của user (FE dùng để hiển thị badge PRO/hạn gói;
BE dùng để AI Headhunter lọc PRO DEV còn `ACTIVE`).

**Response Body:** mảng object cùng cấu trúc `API-SB-01`
(`id/packageId/status/startDate/endDate/targetProjectId`).

---

## 12. WebSocket – Sự kiện thanh toán

Tái dùng kênh notification sẵn có — **không mở kênh mới, không polling** (Q1.2):

| Kênh (Subscribe) | Payload |
|---|---|
| `/topic/users/{userId}/notifications` (gửi cho **cả** `clientId` và `freelancerId` của contract) | `NotificationMessage` mở rộng (dưới đây) |

Mở rộng `NotificationMessage` (tương thích ngược — luồng `1_TOUCH_INVITE` giữ nguyên, các field mới nullable):

```json
{
  "type":       "PAYMENT_ESCROW_HELD",
  "contractId": "uuid-contract-id",
  "paymentId":  "uuid-payment-id",
  "amount":     1000.00,
  "jobId":      null,
  "jobTitle":   null,
  "matchScore": null,
  "message":    "Tiền ký quỹ đợt 1 (1,000.00 VND) đã vào Escrow.",
  "timestamp":  "2026-10-04T10:05:00"
}
```

| `type` | Khi nào bắn | Hành động FE gợi ý |
|---|---|---|
| `PAYMENT_ESCROW_HELD` | IPN thành công (API-PM-02 bước 5) | Cập nhật badge khoản tiền → đã chuyển |
| `PROJECT_STARTED` | Đủ tiền 2 bên (API-PM-02 bước 6) | Hiện banner "Dự án bắt đầu", tải lại bảng nạp tiền |
| `PAYMENT_RELEASED` | Giải ngân thành công (API-PM-03) | Cập nhật badge → `RELEASED`, toast chúc mừng |

### PaymentType (mới — `common/constant/enums/PaymentType.java`)

`CONTRACT_FUND` (client nạp 100%) · `SECURITY_DEPOSIT` (freelancer cọc 2%) — lưu ở cột `payments.payment_type` (migration V18).

### JobStatus mới

`AWAITING_PAYMENT` (ký đôi xong, chờ nạp tiền) — xem `SYSTEM_SPECIFICATION.md` PHỤ LỤC A.

> 🎯 **Logic FE:** subscribe kênh này ngay khi vào `ClientPaymentPage` (route `/client/payment/:contractId` — Q1.4).
> Lọc theo `contractId` trong payload vì kênh nhận mọi notification của user.

---

## 13. Từ điển trạng thái & Mã lỗi

### PaymentStatus (đã có trong BE — `common/constant/enums/PaymentStatus.java`)

| Giá trị | Ý nghĩa | FE hiển thị |
|---|---|---|
| `PENDING` | Khởi tạo, chờ Client trả VNPay | Badge xám + nút "Thanh toán" (mở `vnpayUrl`) |
| `HELD_IN_ESCROW` | Tiền đã vào, đang đóng băng (`escrowHeldAt`) | Badge vàng + nút "Giải ngân" (chỉ Client) |
| `RELEASED` | Đã trả Freelancer (`releasedAt`) | Badge xanh |
| `REFUNDED` | Hoàn trả Client (ngoài scope v1 — không có endpoint, xử lý Admin/DB khi cần) | Badge đỏ |

### SubscriptionStatus / PackageType (đã có trong BE)

`ACTIVE | EXPIRED | CANCELLED` và `PAY_PER_USE | SUBSCRIPTION` — giữ nguyên như `API_Specification_v1.md §X.E`.

### Mã lỗi tái dùng (đã có trong `common/exception/ErrorCode.java`)

`1000 Success` · `1001 Validation` · `1003 FORBIDDEN_ACTION` (sai bên/khác role) ·
`1004 DUPLICATE_RESOURCE` · `1007 RESOURCE_NOT_FOUND` · `1005 REQUEST_FAILED` (sai trạng thái khi release).

### Mã lỗi mới đề xuất (commit 6 bổ sung vào `ErrorCode.java`)

| Code | Tên | HTTP | Khi nào |
|---|---|---|---|
| 1018 | `CONTRACT_NOT_ACTIVE` | 400 | Tạo escrow khi Contract chưa `ACTIVE` |
| 1019 | `ESCROW_PAYMENT_EXISTS` | 409 | Tạo escrow khi còn payment dở dang |
| 1020 | `INVALID_PAYMENT_SIGNATURE` | 400 | IPN sai checksum (trả VNPay `RspCode 97`) |
| 1021 | `PACKAGE_NOT_AVAILABLE` | 400 | Mua gói không tồn tại / `isActive = false` |

---

## 14. Biến môi trường (VNPAY Sandbox)

Thêm vào `sam-be/.env` + `.env.example` (không hardcode — AIRule §5):

```bash
# VNPay Sandbox Configuration (IPN + tạo URL thanh toán)
VNPAY_TMN_CODE=your_tmn_code_here
VNPAY_HASH_SECRET=your_hash_secret_here
VNPAY_API_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_API_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
VNPAY_ALLOWED_RETURN_URLS=http://localhost:5173/client/payment/vnpay-return,https://sam-main.vercel.app/client/payment/vnpay-return,https://sam-develop.vercel.app/client/payment/vnpay-return
VNPAY_IPN_URL=http://localhost:8080/api/v1/payments/vnpay-ipn
VNPAY_DEFAULT_CLIENT_IP=127.0.0.1
```

> `VNPAY_ALLOWED_RETURN_URLS` là danh sách các URL Frontend được phép nhận redirect sau thanh toán (phân cách bằng dấu phẩy).
> FE có thể chủ động truyền `returnUrl` trong Request Body khi tạo thanh toán; BE sẽ validate theo whitelist này để hỗ trợ nhiều môi trường (local, develop, main). Nếu FE không truyền, BE sẽ dùng URL đầu tiên làm mặc định.

---

## 15. Gợi ý triển khai cho commit 6/7 (không bắt buộc, BE được quyền điều chỉnh miễn giữ đúng contract trên)

**Commit 6 — `feat(payment)`:**
- Mới: `modules/payment/controller/PaymentController.java` (PM-01/03/04 + IPN PM-02),
  `dto/request/CreateEscrowRequest.java` (chỉ `contractId`), `dto/response/` (Payment + redirect),
  `service/PaymentService.java` + `impl`, `infrastructure/thirdparty/vnpay/` (`VnpaySigner` HMAC-SHA512, `VnpayClient` dựng URL),
  migration **`V11__Create_Payments_Escrow_Fields.sql`** (V10 cũ đã tồn tại trong DB nên dùng V11; không sửa V1–V9),
  4 mã lỗi mới vào `ErrorCode.java` (1018→1021),
  `NotificationService.sendPaymentNotification(...)` (mở rộng `NotificationMessage` thêm 3 field nullable).
- Sửa: `.env.example` + `application.yaml` (khai báo `VNPAY_*`).
- Verify: `make fmt`, `./mvnw -B clean test` (cần Postgres+Redis live), Swagger test sandbox.

**Commit 7 — `feat(subscription)` (đã xong commit `e9ece35`):**
- Mới: `modules/subscription/controller/SubscriptionController.java` (SB-01/02), `dto/`,
  `service/` + impl, `repository/ServicePackageRepository.java`,
  migration `V12__Subscription_PayPerUse_And_299k.sql`
  (`end_date` nullable + `target_project_id → jobs(id)` + seed 299k).
- Verify: `make fmt`, `./mvnw -B clean test`, test live purchase/me 13/13 pass.

---

*Tài liệu spec Payment v1 dành cho đội Backend (commit 6/7) + Frontend (commit 8/9) | Cập nhật: 10/2026*
