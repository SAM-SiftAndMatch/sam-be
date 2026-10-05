# PLAN — Tích hợp FE cho BE (FE-BE Integration)

> Căn cứ: `sam-be/AIRule.md` · `sam-be/docs/API_Specification_v1.md` ·
> `sam-be/docs/SYSTEM_SPECIFICATION.md` · `AGENTS.md` · Code BE/FE hiện có.
> Trạng thái: **ĐÃ CHỐT VI + VII (04/10/2026) — plan chi tiết từng commit ở mục VIII. Chưa sửa code, chờ duyệt plan commit này.**
> File trước đó: `PLAN_Client_Profile.md` (đã xong Auth/Profile), `REPORT_Client_Profile.md`.

## I. Hiện trạng đã verify (DRY — tìm kiếm trước khi tạo mới)

### 1. Backend (`sam-be/`) — đã xong phần lõi, thiếu Payment API

| Module | Controller | Spec | Trạng thái |
|---|---|---|---|
| Auth | `modules/auth/controller/AuthController.java` (`/api/v1/auth`: login/register/refresh/logout/me) | Ngoài v1, FE đã dùng | ✅ Xong |
| Profile | `modules/user/controller/ProfileController.java` (PF-03/04 + Freelancer) | `API_Spec §IV` | ✅ Xong, FE đã tích hợp (xem REPORT_Client_Profile) |
| AI | `modules/ai/controller/AiController.java` (`GET base-questions`, `POST ba-chat`, `POST risk-chat`) | `API-AI-01/02/03` | ✅ Xong, FE chưa gọi |
| Storage | `common/storage/controller/StorageController.java` (`POST upload-srs`) | `API-ST-01` | ✅ Xong, FE chưa gọi |
| Job | `modules/job/controller/JobController.java` (create, cancel, getById, client/me, recommendations, invite/accept/reject — đủ JB-01→07) | `API-JB-01→07` | ✅ Xong, FE chưa gọi |
| Chat | `modules/chat/controller/ChatController.java` (`GET rooms/{roomId}/messages` + WS `/app/chat/{roomId}/send`) | `API-CH-01` + WS `§IX` | ✅ Xong, FE chưa gọi |
| Contract | `modules/contract/controller/ContractController.java` (`POST chat/rooms/{roomId}/contracts/ai-draft`) + `ContractWsController` (sync/sign) | `API-CT-01` + WS `§IX` | ✅ Xong, FE chưa gọi |
| Notification | `modules/notification/service/NotificationService.java` (không có controller, bắn qua WS `/topic/users/{freelancerId}/notifications`) | WS `§IX` | ✅ BE xong, FE chưa subscribe |
| Payment | `modules/payment/entity/Payment.java` + repository + guard (chưa có controller) | `§VIII` ghi "Sprint tiếp theo" | ❌ Chưa có API |
| Subscription | `modules/subscription/entity/ServicePackage.java`, `UserSubscription.java` (chưa có controller) | `§VIII` + `SYSTEM_SPECIFICATION §II.3` | ❌ Chưa có API |
| Proposal/Review | chỉ entity + repository | Ngoài v1 | ❌ Chưa có API, để sau |

### 2. Frontend (`sam-fe/`) — mới xong Auth/Profile, còn lại là mock

| Item | Vị trí | Trạng thái |
|---|---|---|
| API đã wired BE thật | `src/api/auth.ts`, `src/api/profile.ts` | ✅ Xong |
| API chưa có | `src/api/` thiếu `ai.ts`, `storage.ts`, `job.ts`, `chat.ts`, `contract.ts`, `payment.ts` | ❌ Chưa tạo |
| Pages mock | `AIBriefPage.tsx`, `PostProjectPage.tsx`, `ClientProjectListPage.tsx`, `WorkspacePage.tsx`… đều có `// === MOCK DATA ===` | ❌ Chưa thay bằng gọi BE |
| WS client | `package.json` chưa có `@stomp/stompjs` / `sockjs-client` | ❌ Chưa cài (user đã duyệt Q0.3) |
| Barrel rule | `LoginPage.tsx:7`, `RegisterPage.tsx:7` deep-import `../features/auth/schemas/...` (lách luật vì dùng relative, chưa dùng `@/`) | ⚠️ Cần barrel `features/auth` |
| Line-ending | Repo dùng CRLF, Biome expect LF → `biome ci .` đỏ toàn repo (kể cả file chưa đụng như `App.tsx`) | ⚠️ Cần task riêng thống nhất LF |

## II. Hướng đã chốt với user (04/10/2026, qua thảo luận)

1. **Thứ tự: củng cố nền trước**, rồi mới tích hợp từng module.
2. **Payment: đặc tả ngay** (không để mock), dù BE chưa có controller.
3. **WS: cài `@stomp/stompjs` + `sockjs-client`** (chuẩn spec `/ws` SockJS/STOMP).

## III. Plan Phase 0 — Củng cố nền (làm trước, không đụng nghiệp vụ)

### 0.1. Thống nhất line-ending LF (commit riêng — Q0.1)

- **Tạo mới:** `sam-fe/.gitattributes` (`* text=auto eol=lf`).
- **Sửa:** convert CRLF→LF toàn repo 1 lần (1 commit riêng, không lẫn logic).
- **Vì sao:** `REPORT_Client_Profile.md §4` đã ghi — `biome ci .` (lệnh CI chạy) đỏ toàn repo do CRLF. Không fix thì mọi task sau đều nhiễu, không phân biệt được lỗi mới/cũ.
- **Verify:** `pnpm exec biome ci .` pass (hoặc chỉ còn lỗi thật).

### 0.2. Barrel cho `features/auth` (Q0.2)

- **Tạo mới:** `sam-fe/src/features/auth/index.ts` (re-export `login-schema`, `register-schema`).
- **Sửa (2 dòng):** `sam-fe/src/pages/LoginPage.tsx:7`, `sam-fe/src/pages/RegisterPage.tsx:7` sang `import { … } from '@/features/auth'`.
- **Vì sao:** `scripts/check-features-imports.sh` bắt import từ barrel (`@/features/<name>`). Code hiện tại lách bằng relative path — chuyển sang `@/` là fail ngay.
- **Verify:** `bash scripts/check-features-imports.sh` + `pnpm exec tsc --noEmit`.

### 0.3. Cài WS lib (Q0.3 — đã duyệt, cấm lib thừa)

- **Sửa:** `sam-fe/package.json` + `pnpm-lock.yaml` — `pnpm add @stomp/stompjs sockjs-client` + `pnpm add -D @types/sockjs-client`. Tuyệt đối không thêm lib khác.
- **Vì sao:** spec `§IX` bắt buộc SockJS/STOMP tại `/ws` cho 3 kênh (notifications, chat, contract).
- **Verify:** `pnpm install --frozen-lockfile` + `pnpm run build` pass.

### 0.4. Chuẩn hóa lỗi `ApiResponse`

- **Tạo mới:** `sam-fe/src/lib/api-error.ts` — 1 helper parse `{code, message, result}` (code 1000 = OK, 1016 = token compromised → redirect login, 401 → refresh đã có trong `axios.ts`).
- **Không sửa page cũ vội** (AIRule §4). Các api module mới (Phase 2) sẽ dùng helper này thay vì copy `if (!result) throw`.
- **Verify:** `pnpm exec tsc --noEmit` + `biome lint` file mới.

## IV. Plan Phase 1 — Đặc tả Payment (BE + FE chưa có gì, đã chốt Q1.1→Q1.4)

Quyết định đã chốt:

- **Q1.1:** FE chỉ gửi `contractId`. Tuyệt đối không gửi `amount` (chống can thiệp giá). BE query `agreedAmount` từ Contract. Cọc **50%** vào Escrow, còn lại trả sau nghiệm thu.
- **Q1.2:** Dùng **VNPAY Sandbox**. BE nhận **IPN (webhook)** → cập nhật DB → **push WS** cho FE (kênh notification hoặc kênh payment riêng). Không polling.
- **Q1.3:** `POST /api/v1/subscriptions/purchase` với `{ "packageId": "…" }`. Response `UserSubscription` gồm `id, packageId, status, startDate, endDate`. Giá giữ nguyên 59k/99k/149k/249k/299k như `SYSTEM_SPEC`.
- **Q1.4:** Đổi route FE sang **`/client/payment/:contractId`** (`contractId` duy nhất, truy xuất ngược ra project/freelancer).

Lưu ý phát hiện khi verify: seed `V4__Create_Subscription_Tables_And_Seed.sql` hiện có **5 gói** (59k Ghim, 99k Tuyển gấp, 59k Trọng tài Code, 249k BUSINESS, 149k PRO DEV) — **thiếu gói 299k AI QA nâng cao/tháng** trong `SYSTEM_SPEC`. Commit 7 mặc định giữ 5 gói, chỉ seed thêm 299k nếu bạn xác nhận (xem mục VIII, commit 7).

Output Phase 1: `API_Specification_Payment_v1.md` (file spec mới, không sửa v1) + migration mới `V10…/V11…` (không sửa V1–V9, Flyway `ddl-auto=validate`) + controller/service BE + api client FE (chưa UI — UI nối ở Phase 2).

## V. Plan Phase 2 — Tích hợp từng module (sau khi nền + Payment spec xong)

Thứ tự đề xuất (theo dependency BE):

```
AI (AI-01/02/03) + Storage (ST-01)
  → Job Client (JB-01/03/04/05: PostProjectPage, ClientProjectListPage, FindFreelancerPage)
  → Job Freelancer (JB-06/07 + Notif WS /topic/users/{id}/notifications: FreelancerJobsPage)
  → Chat (CH-01 REST + WS /topic/chat/{roomId}, /app/chat/{roomId}/send: WorkspacePage)
  → Contract (CT-01 REST + WS /topic/contracts/{id}, /app/contracts/{id}/sync|sign: WorkspacePage)
  → Payment UI (khi Phase 1 xong: ClientPaymentPage với :contractId)
```

Mỗi module 1 plan nhỏ kiểu `PLAN_Client_Profile.md`: sửa `src/api/<module>.ts` + `src/types/<module>.ts` + `src/features/<module>/` (schema/hook/barrel) + thay mock trong page tương ứng. Không refactor ngoài phạm vi (AIRule §4).

## VI. Quyết định Phase 0 — ĐÃ CHỐT (04/10/2026)

- [x] **Q0.1:** Làm đúng thứ tự 0.1→0.4. **Bắt buộc tách 0.1 (CRLF→LF) thành 1 commit riêng** `chore: normalize line endings to LF`, không lẫn logic (→ commit 1).
- [x] **Q0.2:** Tạo `src/features/auth/index.ts`, chuyển **toàn bộ** import trong `LoginPage/RegisterPage` sang `@/features/auth` (→ commit 2).
- [x] **Q0.3:** Cài `@stomp/stompjs` + `sockjs-client` (+ `@types/sockjs-client` dev). **Tuyệt đối không lib thừa** (→ commit 3).

## VII. Quyết định Phase 1 Payment — ĐÃ CHỐT (04/10/2026)

- [x] **Q1.1:** FE chỉ gửi `contractId`, BE tự lấy `agreedAmount`. Cọc **50%** Escrow, còn lại sau nghiệm thu (→ commit 5, 6).
- [x] **Q1.2:** **VNPAY Sandbox**. BE nhận IPN → update DB → **push WS** (không polling). Env `VNPAY_*` trong `.env`, không hardcode (→ commit 5, 6).
- [x] **Q1.3:** `POST /api/v1/subscriptions/purchase {packageId}` → `UserSubscription {id, packageId, status, startDate, endDate}`. Giữ giá SYSTEM_SPEC (→ commit 5, 7).
- [x] **Q1.4:** Route FE đổi sang **`/client/payment/:contractId`** (→ commit 8).

## VIII. Plan chi tiết từng commit (chờ duyệt để thực thi)

Quy ước: repo độc lập (`sam-fe/`, `sam-be/`), branch theo AGENTS.md (`<type>/<desc>`), message Conventional Commits. Mỗi commit xong đều verify trước khi sang commit tiếp theo. Thứ tự bắt buộc 1→9.

### Commit 1 — `chore: normalize line endings to LF` (repo `sam-fe`, branch `chore/normalize-line-endings`)

- **Tạo mới (1):** `.gitattributes` — nội dung `* text=auto eol=lf`.
- **Sửa:** convert CRLF→LF toàn repo (`git add --renormalize .`). Không đụng logic, không sửa code.
- **Verify:** `git diff --stat` (chỉ đổi ký tự xuống dòng), `pnpm exec biome ci .` (expect hết lỗi CRLF), `pnpm exec tsc --noEmit`.
- **Lưu ý review:** diff hiển thị lớn — review bằng `git diff -w` / `--ignore-cr-at-eol` để xác nhận không đổi logic.

### Commit 2 — `refactor(auth): expose barrel and use @ alias` (repo `sam-fe`, branch `refactor/auth-barrel`)

- **Tạo mới (1):** `src/features/auth/index.ts` — re-export `loginSchema`, `registerSchema`, `LoginFormData`, `RegisterFormData` (alias `@` đã có trong `vite.config.ts` + tsconfig, giữ thứ tự `organizeImports` của Biome).
- **Sửa đúng 2 dòng:** `src/pages/LoginPage.tsx:7` và `src/pages/RegisterPage.tsx:7` sang `from '@/features/auth'`. Không đụng logic form.
- **Verify:** `pnpm exec tsc --noEmit`, `pnpm exec biome lint` 3 files, `bash scripts/check-features-imports.sh` (expect ✅), test tay login/register 1 vòng.

### Commit 3 — `build(fe): add STOMP + SockJS for /ws realtime` (repo `sam-fe`, branch `build/add-ws-libs`)

- **Lệnh duy nhất:** `pnpm add @stomp/stompjs sockjs-client` + `pnpm add -D @types/sockjs-client`. Cấm mọi lib khác (Q0.3).
- **Files đổi:** `package.json`, `pnpm-lock.yaml` (do pnpm sinh). Chưa viết WS client vội (sang Phase 2).
- **Verify:** `pnpm install --frozen-lockfile`, `pnpm run build` pass.

### Commit 4 — `refactor(fe): add shared ApiResponse error helper` (repo `sam-fe`, branch `refactor/api-error-helper`)

- **Tạo mới (1):** `src/lib/api-error.ts` — `isSuccess(code===1000)`, `getApiErrorMessage`, `assertApiResult(result, message)` ném `Error(message)`. Không đụng `src/lib/axios.ts` (interceptor 401/1016 giữ nguyên), không sửa page cũ (AIRule §4). Các api module mới (commit 9, Phase 2) dùng helper này.
- **Verify:** `pnpm exec tsc --noEmit`, `pnpm exec biome lint` file mới.

### Commit 5 — `docs(payment): add Payment + Subscription API spec v1` (repo `sam-be`, branch `docs/payment-spec`)

- **Tạo mới (1):** `docs/API_Specification_Payment_v1.md` (không sửa `API_Specification_v1.md`), chốt theo Q1.1→Q1.3:
  - `POST /api/v1/payments/escrow` body `{ "contractId": "…" }` (không `amount`) → trả `{ paymentId, amount (50% agreedAmount do BE tính), vnpayUrl }`. Chỉ khi Contract `ACTIVE`; `PaymentAccessGuard` (chỉ Client/Freelancer của hợp đồng).
  - `POST /api/v1/payments/vnpay-ipn` (public, verify checksum Sandbox) → `HELD_IN_ESCROW` (`escrowHeldAt`) → push WS.
  - `POST /api/v1/payments/{paymentId}/release` (chỉ CLIENT chủ hợp đồng/Admin — `canRelease`; Freelancer gọi → 403) → `RELEASED` (`releasedAt`). Phần 50% còn lại trả sau nghiệm thu (cùng cơ chế, đợt 2).
  - `GET /api/v1/payments/contract/{contractId}` (xem trạng thái, guard như trên).
  - `POST /api/v1/subscriptions/purchase` body `{ "packageId": "…" }` → `UserSubscription { id, packageId, status, startDate, endDate }`; `GET /api/v1/subscriptions/me`.
  - **WS (Q1.2):** tái dùng `/topic/users/{userId}/notifications` với `type: PAYMENT_ESCROW_HELD | PAYMENT_RELEASED` + `{ contractId, paymentId, amount, timestamp }` (không mở kênh mới, không polling).
  - **Env (sandbox):** `VNPAY_TMN_CODE / VNPAY_HASH_SECRET / VNPAY_URL / VNPAY_RETURN_URL / VNPAY_IPN_URL` trong `.env` + `.env.example` (không hardcode — AIRule §5).
- **Verify:** không code nên chỉ cần bạn duyệt nội dung spec (commit 6, 7 bám đúng file này).

### Commit 6 — `feat(payment): escrow 50% + VNPay IPN + release + WS push` (repo `sam-be`, branch `feat/payment-escrow-vnpay`)

- **Tạo mới:** `modules/payment/controller/PaymentController.java` (4 endpoint commit 5), `dto/request/CreateEscrowRequest.java` (`contractId` duy nhất — Q1.1), `dto/response/PaymentResponse.java` + `VnpayRedirectResponse.java`, `service/PaymentService.java` + `service/impl/PaymentServiceImpl.java`, `infrastructure/thirdparty/vnpay/` (`VnpaySigner`, `VnpayClient` — checksum + tạo URL sandbox).
- **Sửa:** `.env.example` + `application.yaml` (khai báo `VNPAY_*`), migration mới **`V10__Create_Payments_And_Vnpay_Fields.sql`** (không sửa V1–V9).
- **Logic:** create → load Contract → `amount = agreedAmount × 50%` → `PENDING` + `vnpayUrl`; IPN verify checksum → `HELD_IN_ESCROW` (`escrowHeldAt`) → `NotificationService` push WS; release (`canRelease`) → `RELEASED` (`releasedAt`).
- **Verify:** `make fmt`, `./mvnw -B clean test` (cần Postgres+Redis live), Swagger `/swagger` test tay sandbox.

### Commit 7 — `feat(subscription): purchase + my-subscription` (repo `sam-be`, branch `feat/subscription-purchase`)

- **Tạo mới:** `modules/subscription/controller/SubscriptionController.java` (`POST /purchase`, `GET /me`), `dto/`, `service/` + impl (tính `endDate`: `PAY_PER_USE` vs `SUBSCRIPTION` theo tháng).
- **Response đúng Q1.3:** `{ id, packageId, status, startDate, endDate }`.
- **Seed:** mặc định giữ 5 gói V4. **Điểm mở duy nhất:** gói **299k AI QA nâng cao/tháng** có trong `SYSTEM_SPEC` nhưng chưa có trong V4 → nếu bạn xác nhận, thêm migration `V11__Seed_299k_AI_QA_Package.sql`; nếu không, bỏ dòng này khỏi commit.
- **Verify:** `make fmt`, `./mvnw -B clean test`.

### Commit 8 — `refactor(routes): payment route uses :contractId` (repo `sam-fe`, branch `refactor/payment-route-contract-id`)

- **Sửa:** `src/routes/paths.ts` — `PATH_CLIENT_PAYMENT = '/client/payment/:contractId'` (Q1.4); `src/pages/ClientPaymentPage.tsx` — `useParams` đọc `contractId` thay `projectId/freelancerId` (hiện page vẫn mock → giữ mock + `TODO` nối BE ở Phase 2, không viết UI mới để diff tối thiểu).
- **Verify:** `pnpm exec tsc --noEmit`, `pnpm exec biome lint`, test tay vào `/client/payment/<uuid>` (route không vỡ, guard CLIENT giữ nguyên trong `routes/index.tsx`).

### Commit 9 — `feat(fe): payment + subscription api clients` (repo `sam-fe`, branch `feat/payment-subscription-api`)

- **Tạo mới (4):** `src/types/payment.ts` (`CreateEscrowRequest{contractId}`, `PaymentResponse`, mirror `PaymentStatus`), `src/types/subscription.ts` (`PurchaseRequest{packageId}`, `UserSubscription{…}`), `src/api/payment.ts` (`createEscrow`, `getByContract`, `release` — dùng helper commit 4), `src/api/subscription.ts` (`purchase`, `mySubscriptions`).
- **Không sửa page nào** (UI `ClientPaymentPage/Pricing` nối ở Phase 2).
- **Verify:** `pnpm exec tsc --noEmit`, `pnpm exec biome lint` 4 files.

### Ma trận verify tổng (chạy sau commit 9)

| Repo | Lệnh | Expect |
|---|---|---|
| `sam-fe` | `pnpm exec biome ci .` | pass (nhờ commit 1) |
| `sam-fe` | `pnpm run build` (`tsc -b && vite build`) | pass |
| `sam-fe` | `bash scripts/check-features-imports.sh` | ✅ |
| `sam-be` | `make fmt` + `./mvnw -B clean verify` | pass (cần Postgres+Redis live) |
| Tay | VNPay sandbox: tạo escrow 50% → IPN → WS `PAYMENT_ESCROW_HELD` → release | UI cập nhật realtime, không polling |

## IX. Ghi chú tuân thủ

- AIRule §1 (không đoán mò): mọi quyết định trên đều từ câu trả lời của bạn; điểm mở duy nhất còn lại là seed gói 299k (commit 7).
- AIRule §3 (plan-first): thực thi đúng thứ tự commit 1→9, mỗi commit verify xong mới sang tiếp.
- AIRule §4 (tôn trọng code cũ): diff tối thiểu, giữ style, không reformat diện rộng (ngoại trừ commit 1 đã được duyệt riêng).
- AIRule §5 (bảo mật): không hardcode URL/token/key; dùng `VITE_API_URL` + `.env`; lib mới duy nhất là 2 lib WS đã duyệt.
