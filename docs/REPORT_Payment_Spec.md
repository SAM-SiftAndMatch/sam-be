# BÁO CÁO HOÀN THÀNH — Commit 5: `docs(payment): add Payment + Subscription API spec v1`

> Ngày: 04/10/2026 · Repo: **`sam-be` only** — file docs, không đụng code (FE giữ nguyên 100%).
> Căn cứ: `sam-be/AIRule.md` · `AGENTS.md` (BE — Conventional Commits, Flyway `validate`, Gitleaks) ·
> `sam-be/docs/API_Specification_v1.md` (quy ước chung §I, WS §IX, enum §X) ·
> `sam-be/docs/SYSTEM_SPECIFICATION.md` (§I.5 escrow 30–50%, §II giá gói) ·
> `sam-be/docs/PLAN_FE_BE_Integration.md` (mục VIII commit 5; Q1.1→Q1.4 đã chốt).
> Branch: `docs/payment-spec` (từ `develop`) ·
> Commit: `ebabe70 docs(payment): add Payment + Subscription API spec v1`.

## 1. Kết quả: ĐÃ LÀM XONG

Spec Payment v1 hoàn chỉnh, BE (commit 6/7) và FE (commit 8/9) bám đúng file này để code. Diff: 1 file mới, 347 insertions, 0 code change.

## 2. Chi tiết những gì đã làm

### 2.1. File TẠO MỚI (1 file duy nhất)

`sam-be/docs/API_Specification_Payment_v1.md` — 11 mục, mirror style `API_Specification_v1.md`:

| Mục | Nội dung (map quyết định đã chốt) |
|---|---|
| §1 Luồng 2 đợt 50% | `DEPOSIT` → `FINAL`, BE tự suy `installment` (chưa có payment RELEASED → DEPOSIT). Chống trùng: 1 payment dở dang/contract |
| §2 `API-PM-01` `POST /payments/escrow` | Body chỉ `{contractId}` (Q1.1 — không `amount`); BE tính `agreedAmount × 50%`; trả `{paymentId, installment, amount, currency VND, status PENDING, vnpayUrl}`; tiền đề Contract `ACTIVE` |
| §3 `API-PM-02` `POST /payments/vnpay-ipn` | PUBLIC + verify HMAC-SHA512; idempotent (`RspCode 02` khi đã HELD); fail giữ `PENDING` (enum không có `FAILED`); OK → `HELD_IN_ESCROW` + push WS |
| §4 `API-PM-03` `POST /payments/{id}/release` | Chỉ CLIENT chủ/Admin (`canRelease` có sẵn — Freelancer 403); `HELD → RELEASED` |
| §5 `API-PM-04` `GET /payments/contract/{id}` | List đợt 1+2 (`canAccess` có sẵn) |
| §6 `API-SB-01` `POST /subscriptions/purchase` | Body `{packageId}`; response đủ 5 trường Q1.3 (`id/packageId/status/startDate/endDate`); quy tắc `endDate` theo `PackageType` |
| §7 `API-SB-02` `GET /subscriptions/me` | Phục vụ badge PRO + AI Headhunter lọc PRO DEV |
| §8 WS payment | **Tái dùng** `/topic/users/{userId}/notifications` (không kênh mới, không polling — Q1.2); mở rộng `NotificationMessage` thêm 3 field nullable tương thích ngược; types `PAYMENT_ESCROW_HELD \| PAYMENT_RELEASED`; bắn cho cả 2 bên |
| §9 Enum + mã lỗi | Tái dùng `PaymentStatus/SubscriptionStatus/PackageType` + codes 1000/1001/1003/1004/1007/1005; **đề xuất 4 code mới** 1018–1021 (commit 6 bổ sung vào `ErrorCode.java`) |
| §10 Env sandbox | 5 biến `VNPAY_*` (placeholder, không hardcode — AIRule §5); `RETURN_URL` về FE nhưng FE chỉ tin WS |
| §11 Gợi ý commit 6/7 | Danh sách file BE sẽ tạo (controller/dto/service/VnpaySigner/VnpayClient/V10/V11), verify `make fmt` + `clean test` |

### 2.2. Không đụng

- Không sửa `API_Specification_v1.md` (file mới nối tiếp — AIRule §4).
- Không sửa Java/migration/`.env.example` nào (sang commit 6/7).
- Các file plan/report `??` untracked trong `sam-be/docs/` để nguyên — commit này chỉ stage đúng 1 file spec.
- Không secret thật (quét: chỉ placeholder; URL sandbox là public).
- **FE giữ nguyên 100%.**

## 3. DRY check (AIRule §2) — spec bám code có sẵn, không phát minh

- `PaymentAccessGuard.canAccess/canRelease`, `Contract.agreedAmount`, `PaymentStatus`, `UserSubscription` field, `NotificationService` (`SimpMessagingTemplate` → `/topic/users/{id}/notifications`), `ErrorCode` pattern, style `.env.example` — tất cả đọc từ code trước khi viết spec.
- Không có gateway nào tồn tại (grep `vnpay|ipn|momo` chỉ ra seed data chữ) → IPN là greenfield, spec định nghĩa từ đầu theo đúng Q1.2.

## 4. Điểm mở còn lại (trung thực — cần chốt trước/song song commit 6/7)

1. **`endDate` cho `PAY_PER_USE`** (cột DB `NOT NULL`): spec đề xuất `+30 ngày`; nếu muốn khác báo trước commit 7.
2. **Gói 299k AI QA nâng cao/tháng**: có trong SYSTEM_SPEC nhưng thiếu trong seed V4 — commit 7 chỉ seed `V11` khi được xác nhận (đã ghi trong spec §6 + §11).

## 5. Verify đã chạy (repo `sam-be`, branch `docs/payment-spec`)

| Check | Lệnh/kết quả |
|---|---|
| Hook commit-msg BE (`scripts/commit-msg.sh` pattern) | ✅ `docs(payment): …` pass, commit `ebabe70` thành công |
| Line-ending LF (BE pre-commit `mixed-line-ending --fix=lf`) | ✅ File ghi LF (CR-count = 0) |
| Không secret (Gitleaks) | ✅ Chỉ placeholder `your_*_here`; URL sandbox public |
| Phạm vi commit | ✅ `1 file changed, 347 insertions(+)` — đúng 1 file spec |
| Không cần `./mvnw test` | ✅ Không đụng code Java (docs-only) |

## 6. Trạng thái sau task

- `sam-be`: commit `ebabe70` trên branch `docs/payment-spec` (từ `develop`). Chưa push, chưa PR.
- Tiếp theo: **commit 6** `feat(payment)` (BE code: controller/service/VNPay/V10 + WS push) — cần Postgres+Redis live để `clean test`, và cần chốt 2 điểm mở ở mục 4.
