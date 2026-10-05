# BÁO CÁO TEST FULL — API + Browser E2E (04/10/2026)

> Trả lời câu hỏi user: "test các api chưa, đã trả về đúng kết quả chưa, còn gì chưa test".
> Môi trường: BE live `:8080` (native PG17 + docker redis), FE dev `:5173`, Chrome hệ thống.
> Test data: acc `flow*@test.local`, `e2e*@test.local`, `shape/unicode/c2_*@test.local` (dữ liệu test, không chạm seed).

## 1. Thay đổi config trong đợt này (theo yêu cầu user)

- Commit `9611b4a docs(payment): declare VNPay env vars in example` (repo `sam-be`, branch `docs/payment-spec`):
  `.env.example` += 7 biến `VNPAY_*` (placeholder, không secret) + đồng bộ spec §10
  (`VNPAY_API_URL`, `VNPAY_API_QUERY_URL`, `VNPAY_DEFAULT_CLIENT_IP` theo đúng key user đã bỏ vào `.env`).
- User đã bỏ key VNPay thật vào `sam-be/.env` (đã kiểm tra: đủ 7 tên biến, không đọc/in giá trị).
  Key dùng để test escrow thật ở commit 6 (endpoint PM-* chưa tồn tại nên hiện chưa test được).

## 2. Kết quả API — PASS toàn bộ (22 case)

| # | API | Kỳ vọng | Thực tế |
|---|---|---|---|
| 1 | `POST /auth/register` CLIENT/FREELANCER | 200 + token | ✅ 200, `role` đúng (`CLIENT`/`FREELANCER`) |
| 2 | `POST /auth/login` | 200 | ✅ 200 |
| 3 | `POST /auth/refresh` (rotation) | 200 + token mới | ✅ 200 |
| 4 | `GET /auth/me` | 200 `{userId,email,accountType,sessionId}` | ✅ 200 |
| 5 | `GET /profiles/client/me` lần đầu | 200 object rỗng | ✅ 200, `companyName` null |
| 6 | `PUT /profiles/client/me` thiếu `companyName` | 400 | ✅ 400 (`@NotBlank`) |
| 7 | `PUT` đủ + `GET` prefill (ASCII) | 200 + prefill | ✅ 200 |
| 8 | `PUT` tiếng Việt (UTF-8 bytes) + đối chiếu DB | lưu đúng UTF-8 | ✅ `Công ty ABC \| Fintech` trong PG |
| 9 | Freelancer `GET /profiles/client/me` | 403 | ✅ 403 (dứt điểm nghi vấn REPORT_Client_Profile §5) |
| 10 | `GET/PUT /profiles/freelancer/me` (+skills Java/Spring) | 200 | ✅ 200 |
| 11 | `GET /ai/base-questions` | 200 list | ✅ 200, 6 câu (key Gemini thật hoạt động) |
| 12 | `POST /ai/ba-chat` | `ASKING` + câu hỏi tiếp | ✅ 200 `ASKING`, tiếng Việt |
| 13 | `POST /ai/risk-chat` ($1000/2 tuần) | `HIGH` | ✅ 200 `NEGOTIATING`, `HIGH_RISK` — AI đánh giá đúng |
| 14 | `POST /storage/upload-srs` | URL string | ✅ 200 `https://res.cloudinary.com/…/srs-flow-….txt` (key Cloudinary thật) |
| 15 | `POST /jobs` (thường + URGENT) | 200 `OPEN` + skills AI | ✅ 200, AI bóc 5 skills từ SRS ngắn |
| 16 | `GET /jobs/client/me`, validation deadline quá khứ | list / 400 | ✅ 200 count đúng / 400 |
| 17 | `PATCH /jobs/{id}/cancel` (OPEN → lại) | `CANCELLED` → 400/1017 | ✅ đúng cả 2 |
| 18 | `GET /jobs/{id}/recommendations` (urgent) | Top5 + score | ✅ 5 rec (`95/75/75/70/50`), đúng thứ tự |
| 19 | `POST …/invite` + WS `/topic/users/{fid}/notifications` | 200 + push `1_TOUCH_INVITE` | ✅ cả 2 (payload đúng spec: jobId/jobTitle/matchScore/message/timestamp) |
| 20 | `POST …/accept` → `roomId`; `…/reject` → `REJECTED` | đúng cả 2 | ✅ accept trả roomId; reject 200, rec → `REJECTED` |
| 21 | `GET /chat/rooms/{room}/messages` (2 role) + WS `/app/chat/{room}/send` | 200 + broadcast đủ field | ✅ 200 cả 2; broadcast có `senderName` (BE tự resolve) |
| 22 | `POST …/contracts/ai-draft` + WS sync/sign | `DRAFT` → `SYNC` → `SIGN` → `COMPLETED/ACTIVE`, job `IN_PROGRESS` | ✅ đúng toàn bộ, cờ reset `false/false` sau sync (chống lật lọng chạy) |

Hạ tầng: `./mvnw -B clean test` ✅ BUILD SUCCESS; SockJS `/ws/info` ✅; STOMP CONNECT (lib FE) ✅.

## 3. Browser E2E bằng Chrome hệ thống — PASS (2/2, Playwright + `executablePath`)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | CLIENT register → `/client/profile` → điền form → success → reload prefill | ✅ (chứng minh barrel commit 2 chạy thật trên browser) |
| 2 | FREELANCER register → gõ `/client/profile` → đá về `/freelancer`; profile headline-only → success → reload prefill | ✅ |

Ghi chú: download browser Playwright bị chặn trong môi trường này nên dùng Chrome có sẵn
(`C:\Program Files\Google\Chrome\Application\chrome.exe`); script e2e nằm ngoài repo
(`Temp\opencode\e2e`), repo không dính file test nào.

## 4. Lệch SPEC-vs-CODE phát hiện (quan trọng cho Phase 2, chưa sửa — chờ quyết)

| # | Spec viết | Code chạy thật | Hướng xử lý đề xuất |
|---|---|---|---|
| M1 | JB-01 response `{jobId, extractedSkills[{skillId,skillName}]}` | Thật là `{id, skills[{id,name}]}` (+`clientId/clientName/riskLevel`) | Phase 2 FE bám field thật; sau đó sửa spec hoặc code cho khớp |
| M2 | `GET /jobs/{id}` mô tả "public" | Không Bearer → **401** (`1006`) | FE `JobDetailPage` luôn gửi token; hoặc BE mở `permitAll` (quyết sau) |
| m3 | Chat WS broadcast như `ChatMessageDto` | `createdAt: null` trong broadcast (REST history OK) | BE set timestamp trước broadcast (sửa nhỏ, để commit sau) |

Đã xác minh **không lệch**: `role` (login/register) vs `accountType` (/me) — FE types khớp cả 2.

## 5. Còn CHƯA test (rõ ràng)

1. **Payment PM-*/SB-***: endpoint chưa tồn tại (commit 6/7) — key VNPay đã sẵn sàng để test escrow thật ngay khi code xong.
2. **Logout + auto-refresh 401 trên browser** (interceptor `axios.ts`): cần case token hết hạn — để vào E2E Phase 2.
3. **Đợt escrow 2/hoàn tiền** (`FINAL`, `REFUNDED`): sau commit 6.
4. **Rate-limit 1009, proposal/review**: không có API / ngoài scope v1.
5. `GET /skills?search=` có dùng ở UI freelancer (page gọi được — BE không gắn guard, đã biết từ trước).

## 6. Dọn dẹp & trạng thái

- BE vẫn live `:8080` (test data trong PG native); FE dev `:5173` (tiến trình sẵn có).
- Temp scripts (`ws-*.js`, e2e project) nằm ngoài repo — không ảnh hưởng `git status` 2 repo.
- Không sửa code app nào trong đợt test này (chỉ 1 commit docs `.env.example` ở mục 1).
