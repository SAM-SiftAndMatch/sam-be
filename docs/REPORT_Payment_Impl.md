# BÁO CÁO HOÀN THÀNH — Commit 6: `feat(payment): escrow 50 percent plus VNPay IPN plus release`

> Ngày: 05/10/2026 · Repo: **`sam-be` only** (FE giữ nguyên 100%).
> Căn cứ: `sam-be/AIRule.md` · `AGENTS.md` (BE) · `API_Specification_v1.md` ·
> `API_Specification_Payment_v1.md` (spec commit 5) · `PLAN_FE_BE_Integration.md` (mục VIII, commit 6).
> Branch: `feat/payment-escrow-vnpay` (từ `develop`) ·
> Commit: `943695e` (20 files, +605/−1).

## 1. Kết quả: ĐÃ LÀM XONG + TEST LIVE 22/22 PASS

Escrow 2 đợt 50% + IPN VNPay (ký thật bằng key sandbox của user) + release + WS push realtime.
Test trên BE live (PG native + redis docker): **FAILURES: 0**.

## 2. Chi tiết những gì đã làm

### 2.1. File TẠO MỚI (9)

| File | Nội dung |
|---|---|
| `modules/payment/controller/PaymentController.java` | PM-01 (`CLIENT`), PM-02 IPN (public, trả `RspCode` thô đúng chuẩn VNPay), PM-03 (`@paymentAccessGuard.canRelease`), PM-04 |
| `modules/payment/dto/request/CreateEscrowRequest.java` | Chỉ `contractId` (`@NotNull`) — không `amount` (Q1.1) |
| `modules/payment/dto/response/PaymentResponse.java` | `paymentId/contractId/installment/amount/currency/status/vnpayUrl/escrowHeldAt/releasedAt` |
| `modules/payment/service/PaymentService.java` + `impl/PaymentServiceImpl.java` | Logic: ACTIVE-check, client-check, chống trùng, `installment` suy từ lịch sử, `agreedAmount×50%`, IPN (verify→đối chiếu tiền→idempotent→HELD+WS), release (HELD→RELEASED+WS), list sort theo đợt |
| `infrastructure/thirdparty/vnpay/VnpayProperties.java` | Mirror `JwtProperties` (`@ConfigurationProperties("vnpay")`), 7 field khớp `.env` thật |
| `infrastructure/thirdparty/vnpay/VnpaySigner.java` | HMAC-SHA512, sort+encode theo chuẩn VNPay, `verify` tách `vnp_SecureHash` |
| `infrastructure/thirdparty/vnpay/VnpayClient.java` | Dựng `vnpayUrl` (2.1.0/pay/VND/`TxnRef`=paymentId bỏ gạch/`ExpireDate`+15p, múi giờ `Asia/Ho_Chi_Minh`) |
| `db/migration/V11__Create_Payments_Escrow_Fields.sql` | `installment_no` + unique `(contract_id, installment_no)` (xem §4 vì sao V11) |

### 2.2. File SỬA (11, toàn additive)

`ErrorCode` (+1018→1021) · `Payment.installmentNo` · `PaymentRepository` (+2 query) ·
`NotificationMessage` (+3 field nullable, tương thích ngược) + `sendPaymentNotification` (bắn cả 2 bên) ·
`ContractService.getContractById` (đúng luật AGENTS: qua Service interface, không chọc repository module khác) ·
`SecurityConfig` (+1 dòng mở IPN public — bắt buộc vì apiChain đòi JWT) ·
`application.yaml` (khối `vnpay`, fail-fast như jwt) ·
`PaymentAccessGuard` (+`@Transactional(readOnly)` — fix §3).

### 2.3. Không đụng

- Không sửa V1–V9, không sửa spec v1, không sửa logic module khác (revert 2 lần reflow `SwaggerSecurityConfig` của spotless).
- Không secret trong code (key chỉ trong `.env` untracked). Không lib mới (chỉ JDK + Spring có sẵn).
- **FE giữ nguyên 100%.**

## 3. Bug thật tìm thấy khi test (đã fix hết trước commit)

| # | Triệu chứng | Gốc | Fix |
|---|---|---|---|
| 1 | `PM-01` 500 `ObjectOptimisticLockingFailureException` | `builder().id(randomUUID)` trong khi entity `@GeneratedValue` → Hibernate `merge` nhầm thành UPDATE row không tồn tại | Để Hibernate sinh ID khi `save()`, gán `paymentGatewayId` sau trên entity managed |
| 2 | `release` 500 thay vì 400/403 | `PaymentAccessGuard` (SpEL, ngoài transaction) chạm `contract.getClient()` lazy → `LazyInitializationException` | +`@Transactional(readOnly)` cho cả 2 method guard (đồng thời chữa latent bug ở `canAccess`) |
| 3 | (Quan sát, ngoài scope) `ContractAccessGuard`/`JobAccessGuard` cùng pattern lazy-trong-guard, chưa có `@Transactional` | Chưa sập vì chưa gặp case — **không đụng** (AIRule §4), ghi nhận để review riêng | — |

Các FAIL còn lại trong lần chạy đầu đều là bug harness test (null cascade, body mã hóa sai, kỳ vọng amount cũ 1750 thay vì 1600 sau sync 3200) — đã sửa script, lần 2 xanh hết.

## 4. Hạ tầng & migration (trung thực)

- DB native đã tồn tại **V10 cũ** (file mất khỏi repo) với đúng nội dung `installment_no` + unique → đổi file mình thành **V11** (không sửa migration đã apply), `flyway repair` chính thức đánh DELETED V10, boot log: `now at version v11`.
- Lần boot đầu sau repair fail do JVM hết commit-charge (máy 29GB/30GB) — đã reboot quan hệ: chạy lại với `-Xmx1g`, boot sạch 9–10s.
- `./mvnw -B clean test`: BUILD SUCCESS (143 files). `spotless:apply` sạch.

## 5. Verify live (BE `:8080`, key VNPay sandbox thật của user, chữ ký tính độc lập bằng node)

PM-01 200 (`1600.00/DEPOSIT/vnpayUrl` ký 128 hex) · dup 1019 · DRAFT 1018 · freelancer 403 ·
PM-04 list · release-PENDING 1005 · IPN thật `RspCode 00` + WS `PAYMENT_ESCROW_HELD` + DB `HELD` ·
IPN giả `97` · release `RELEASED` + WS `PAYMENT_RELEASED` · đợt 2 `FINAL` trọn vòng ·
đợt 3 chặn 1019 (`maxedOut`) · PM-04 cuối 2 `RELEASED`. **22/22 PASS.**

## 6. Trạng thái sau task

- `git status` sạch (trừ các file plan/report untracked có sẵn). Chưa push, chưa PR.
- BE đã tắt sau test (cleanup trong script). Muốn test tay: chạy lại BE + dùng các endpoint trên.
- Tiếp theo: **commit 7** `feat(subscription)` — còn chờ chốt `endDate` PAY_PER_USE + seed 299k.
