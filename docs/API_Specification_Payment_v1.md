# TÀI LIỆU ĐẶC TẢ API PAYMENT & SUBSCRIPTION
**Hệ thống Freelance Platform**
Dành cho Backend + Frontend Developer | Phiên bản 1.0 (Payment v1)

> Tài liệu này là phần nối tiếp của `API_Specification_v1.md` (không sửa v1).
> Mọi quy ước chung của v1 đều giữ nguyên: wrapper `ApiResponse` (`code: 1000` = thành công, dữ liệu trong `result`),
> Bearer Token (`SecurityUtils.getCurrentUserId()` — FE **không** gửi `userId`),
> DateTime ISO 8601, WS qua `/ws` (SockJS/STOMP, `/app` → server, `/topic` → client).
> Quyết định nghiệp vụ đã chốt với user (04/10/2026): xem `PLAN_FE_BE_Integration.md` mục VII.

---

## Mục lục

1. [Tiền đề & Luồng nghiệp vụ](#1-tiền-đề--luồng-nghiệp-vụ)
2. [API-PM-01 · Tạo ký quỹ Escrow + URL VNPay](#2-api-pm-01--tạo-ký-quỹ-escrow--url-vnpay)
3. [API-PM-02 · IPN VNPay (webhook)](#3-api-pm-02--ipn-vnpay-webhook)
4. [API-PM-03 · Giải ngân Escrow](#4-api-pm-03--giải-ngân-escrow)
5. [API-PM-04 · Xem giao dịch theo hợp đồng](#5-api-pm-04--xem-giao-dịch-theo-hợp-đồng)
6. [API-SB-01 · Mua gói dịch vụ](#6-api-sb-01--mua-gói-dịch-vụ)
7. [API-SB-02 · Xem gói đang sở hữu](#7-api-sb-02--xem-gói-đang-sở-hữu)
8. [WebSocket – Sự kiện thanh toán](#8-websocket--sự-kiện-thanh-toán)
9. [Từ điển trạng thái & Mã lỗi](#9-từ-điển-trạng-thái--mã-lỗi)
10. [Biến môi trường (VNPAY Sandbox)](#10-biến-môi-trường-vnpay-sandbox)
11. [Gợi ý triển khai cho commit 6/7](#11-gợi-ý-triển-khai-cho-commit-67)

---

## 1. Tiền đề & Luồng nghiệp vụ

```
ĐIỀU KIỆN TIÊN QUYẾT (mọi API payment):
  · Contract phải ở trạng thái ACTIVE (cả 2 bên đã ký qua WS sign, xem API-CT-01 v1).
  · DRAFT / CANCELLED / COMPLETED → lỗi CONTRACT_NOT_ACTIVE (đề xuất 1018).

LUỒNG KÝ QUỸ 2 ĐỢT — mỗi đợt = 50% agreedAmount (Q1.1: FE KHÔNG gửi amount):
  Đợt 1 (DEPOSIT):
    Client POST API-PM-01 {contractId}
      → BE query Contract.agreedAmount, tính amount = agreedAmount × 50%
      → tạo Payment(status = PENDING) + vnpayUrl → FE redirect user sang VNPay
    Client thanh toán xong → VNPay gọi IPN → API-PM-02
      → verify checksum → HELD_IN_ESCROW (escrowHeldAt) → push WS cho CẢ 2 bên
      → FE cập nhật UI realtime, KHÔNG polling (Q1.2)
    Freelancer thực hiện dự án…
    Client nghiệm thu → POST API-PM-03 → RELEASED (releasedAt) → push WS
  Đợt 2 (FINAL, 50% còn lại sau nghiệm thu đợt cuối):
    Cơ chế Y HỆT đợt 1 (tạo escrow → IPN → release).
    BE tự xác định installment: chưa có payment RELEASED nào cho contract → DEPOSIT,
    ngược lại → FINAL. Response luôn trả về installment để FE hiển thị "đợt 1/đợt 2".

CHỐNG TRÙNG: mỗi contract tại một thời điểm chỉ có tối đa 1 payment
  ở trạng thái PENDING hoặc HELD_IN_ESCROW. Tạo mới khi còn payment dở dang
  → lỗi ESCROW_PAYMENT_EXISTS (đề xuất 1019).

LUỒNG MUA GÓI (Q1.3):
  User POST API-SB-01 {packageId} (gói seed sẵn từ V4: 59k Ghim, 99k Tuyển gấp,
  59k Trọng tài Code, 249k BUSINESS, 149k PRO DEV)
    → tạo UserSubscription(status = ACTIVE, có startDate & endDate)
    → AI Headhunter quét PRO DEV còn hạn để ưu tiên Freelancer (xem API-JB v1).
```

---

## 2. API-PM-01 · Tạo ký quỹ Escrow + URL VNPay

```
POST /api/v1/payments/escrow
Role: CLIENT (phải là client của contract — kiểm tra qua contract, không chỉ role)
```

**Validation (bắt buộc):**
- `contractId`: không được null, phải là UUID hợp lệ, Contract phải tồn tại (không → `RESOURCE_NOT_FOUND` 1007).
- Contract phải `ACTIVE` (không → `CONTRACT_NOT_ACTIVE`, đề xuất 1018).
- Caller phải là client của contract (không → `FORBIDDEN_ACTION` 1003).
- Không có payment dở dang (`PENDING`/`HELD_IN_ESCROW`) cho contract (có → `ESCROW_PAYMENT_EXISTS`, đề xuất 1019).

**Request Body (Q1.1: chỉ `contractId`, tuyệt đối không `amount`):**
```json
{
  "contractId": "uuid-contract-id"
}
```

**Response Body:**
```json
{
  "code": 1000,
  "result": {
    "paymentId":   "uuid-payment-id",
    "contractId":  "uuid-contract-id",
    "installment": "DEPOSIT",
    "amount":      1000.00,
    "currency":    "VND",
    "status":      "PENDING",
    "vnpayUrl":    "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=100000000&..."
  }
}
```

> `amount` do BE tính = `Contract.agreedAmount × 50%` (làm tròn 2 decimals).
> `vnp_TxnRef` = `paymentId` bỏ dấu gạch (BE tự sinh, FE không quan tâm).
> 🎯 **Logic FE:** nhận `vnpayUrl` → `window.location.href = vnpayUrl` (rời app sang VNPay).
> Không poll trạng thái — chờ WS `PAYMENT_ESCROW_HELD` (mục 8).

---

## 3. API-PM-02 · IPN VNPay (webhook)

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
6. Thành công → trả `{"RspCode":"00","Message":"Confirm Success"}`.

> ⚠️ Môi trường hiện tại là **VNPAY Sandbox** (Q1.2). Không hardcode key/URL (mục 10).

---

## 4. API-PM-03 · Giải ngân Escrow

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

## 5. API-PM-04 · Xem giao dịch theo hợp đồng

```
GET /api/v1/payments/contract/{contractId}
Role: CLIENT hoặc FREELANCER của hợp đồng, hoặc Admin (PaymentAccessGuard.canAccess)
```

Trả về danh sách (đợt DEPOSIT + FINAL), sắp xếp theo thời gian tạo tăng dần.

**Response Body:**
```json
{
  "code": 1000,
  "result": [
    {
      "paymentId":   "uuid-payment-1",
      "contractId":  "uuid-contract-id",
      "installment": "DEPOSIT",
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

## 6. API-SB-01 · Mua gói dịch vụ

```
POST /api/v1/subscriptions/purchase
Role: CLIENT hoặc FREELANCER (BE tự trích userId — FE không gửi)
```

**Validation (bắt buộc):**
- `packageId`: không null, gói phải tồn tại và `isActive = true` (không → `PACKAGE_NOT_AVAILABLE`, đề xuất 1021).

**Request Body (Q1.3):**
```json
{
  "packageId": "f0000000-0000-0000-0000-000000000006"
}
```

**Response Body (Q1.3 — đủ 5 trường để FE hiển thị hạn dùng):**
```json
{
  "code": 1000,
  "result": {
    "id":        "uuid-subscription-id",
    "packageId": "f0000000-0000-0000-0000-000000000006",
    "status":    "ACTIVE",
    "startDate": "2026-10-04T10:00:00",
    "endDate":   "2026-11-04T10:00:00"
  }
}
```

**Quy tắc tính `endDate` (BE thực hiện):**
- `SUBSCRIPTION` (BUSINESS 249k, PRO DEV 149k — thuê bao tháng): `endDate = startDate + 1 tháng`.
- `PAY_PER_USE` (Ghim 59k, Tuyển gấp 99k, Trọng tài Code 59k — dùng 1 lần):
  ⚠️ **Chưa chốt cách tính `endDate`** (cột DB `nullable = false`, bắt buộc phải có giá trị).
  Đề xuất cho commit 7: `endDate = startDate + 30 ngày` (hạn dùng 1 lần trong 30 ngày).
  Nếu user muốn khác (VD: dùng xong hết ngay), báo trước khi làm commit 7.

> Giá giữ nguyên các mốc `SYSTEM_SPECIFICATION §II.3` (Q1.3).
> Lưu ý: seed `V4` hiện có 5 gói, **thiếu gói 299k AI QA nâng cao/tháng** trong SYSTEM_SPEC —
> commit 7 chỉ seed bổ sung nếu user xác nhận (xem `PLAN_FE_BE_Integration.md` mục VIII, commit 7).

---

## 7. API-SB-02 · Xem gói đang sở hữu

```
GET /api/v1/subscriptions/me
Role: CLIENT hoặc FREELANCER (BE tự trích userId)
```

Trả về danh sách subscription của user (FE dùng để hiển thị badge PRO/hạn gói;
BE dùng để AI Headhunter lọc PRO DEV còn `ACTIVE`).

**Response Body:** mảng object cùng cấu trúc `API-SB-01` (`id/packageId/status/startDate/endDate`).

---

## 8. WebSocket – Sự kiện thanh toán

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
| `PAYMENT_ESCROW_HELD` | IPN thành công (API-PM-02 bước 5) | Cập nhật badge payment → `HELD_IN_ESCROW`, hiện nút "Giải ngân" (nếu là Client) |
| `PAYMENT_RELEASED` | Giải ngân thành công (API-PM-03) | Cập nhật badge → `RELEASED`, toast chúc mừng |

> 🎯 **Logic FE:** subscribe kênh này ngay khi vào `ClientPaymentPage` (route `/client/payment/:contractId` — Q1.4).
> Lọc theo `contractId` trong payload vì kênh nhận mọi notification của user.

---

## 9. Từ điển trạng thái & Mã lỗi

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

## 10. Biến môi trường (VNPAY Sandbox)

Thêm vào `sam-be/.env` + `.env.example` (không hardcode — AIRule §5):

```bash
# VNPay Sandbox Configuration (IPN + tạo URL thanh toán)
VNPAY_TMN_CODE=your_tmn_code_here
VNPAY_HASH_SECRET=your_hash_secret_here
VNPAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:5173/client/payment/vnpay-return
VNPAY_IPN_URL=http://localhost:8080/api/v1/payments/vnpay-ipn
```

> `VNPAY_RETURN_URL` trỏ về FE để hứng redirect sau thanh toán
> (FE đọc query, sau đó chờ WS chứ không tự kết luận thành công — chống giả mạo kết quả ở client).
> Khi lên production chỉ đổi giá trị env, không sửa code.

---

## 11. Gợi ý triển khai cho commit 6/7 (không bắt buộc, BE được quyền điều chỉnh miễn giữ đúng contract trên)

**Commit 6 — `feat(payment)`:**
- Mới: `modules/payment/controller/PaymentController.java` (PM-01/03/04 + IPN PM-02),
  `dto/request/CreateEscrowRequest.java` (chỉ `contractId`), `dto/response/` (Payment + redirect),
  `service/PaymentService.java` + `impl`, `infrastructure/thirdparty/vnpay/` (`VnpaySigner` HMAC-SHA512, `VnpayClient` dựng URL),
  migration **`V10__Create_Payments_And_Vnpay_Fields.sql`** (không sửa V1–V9), 4 mã lỗi mới vào `ErrorCode.java`,
  `NotificationService.sendPaymentNotification(...)` (mở rộng `NotificationMessage` thêm 3 field nullable).
- Sửa: `.env.example` + `application.yaml` (khai báo `VNPAY_*`).
- Verify: `make fmt`, `./mvnw -B clean test` (cần Postgres+Redis live), Swagger test sandbox.

**Commit 7 — `feat(subscription)`:**
- Mới: `modules/subscription/controller/SubscriptionController.java` (SB-01/02), `dto/`, `service/` + impl,
  migration `V11__Seed_299k_AI_QA_Package.sql` (**chỉ khi user xác nhận** gói 299k).
- Verify: `make fmt`, `./mvnw -B clean test`.

---

*Tài liệu spec Payment v1 dành cho đội Backend (commit 6/7) + Frontend (commit 8/9) | Cập nhật: 10/2026*
