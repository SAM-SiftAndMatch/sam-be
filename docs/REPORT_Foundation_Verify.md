# BÁO CÁO KIỂM THỬ — Verify foundation (commit 1→5)

> Ngày: 04/10/2026 · Phạm vi: toàn bộ 5 commit đã làm (4 FE + 1 BE docs).
> Trả lời câu hỏi của user: "đã test những gì đã làm xem có chạy chính xác không chưa".
> Kết luận ngắn: **đã test tất cả những gì test được mà không cần hạ tầng live; phần cần BE live + browser thì chưa — liệt kê rõ ở mục 3.**

## 1. ĐÃ TEST & PASS (6 nhóm)

| # | Cái gì | Lệnh / cách làm | Kết quả |
|---|---|---|---|
| 1 | Lint toàn repo FE (đúng lệnh CI) | `pnpm exec biome ci .` trên branch `feat/client-profile-and-fe-foundation` (gồm cả 4 commit) | ✅ `Checked 66 files. No fixes applied.` |
| 2 | Typecheck + build production | `pnpm run build` (`tsc -b && vite build`) — bundle gồm `LoginPage/RegisterPage` qua barrel mới (commit 2) | ✅ 233 modules, ~3.6s (warning chunk >500kB là có sẵn) |
| 3 | Runtime 2 lib WS (commit 3) | `node --input-type=module`: `new Client({})` từ `@stomp/stompjs@7.3.0` + `SockJS` từ `sockjs-client@1.6.1` | ✅ Cả 2 load và khởi tạo được (`activate`/`function` OK) |
| 4 | Logic `api-error.ts` (commit 4) | Compile file ra thư mục temp ngoài repo (`tsc --ignoreConfig`), chạy **15 assertions** bằng `node assert`: success/void/nullish/error-code/message-fallback/`errors` map/`extractApiResponse`/non-object | ✅ `ALL 15 ASSERTIONS PASSED` (ghi chú: lần chạy đầu fail 1 case do **test viết sai** — helper trả `message` thay vì `fallback` là đúng thiết kế; đã sửa test, không sửa code) |
| 5 | Serve bundle thật (commit 1→4) | `vite preview` + `Invoke-WebRequest /login` | ✅ `HTTP=200`, có `#root` (chứng minh bundle + barrel + LF serve được) |
| 6 | BE còn build được (commit 5 docs-only) | `.\mvnw.cmd -B -q compile -DskipTests` + `git diff --stat` (rỗng) | ✅ exit 0; **0 tracked file BE bị đụng** |

## 2. CHƯA TEST (trung thực — cần hạ tầng/người, không giấu)

| # | Cái gì | Vì sao chưa | Cần gì để test |
|---|---|---|---|
| 1 | Render + submit Login/Register trên browser, guard/redirect theo role | Không có browser automation; preview chỉ trả static shell | Chạy `pnpm dev` + mở browser tay, hoặc thêm Playwright (ngoài scope plan) |
| 2 | WS connect thật tới `/ws` (chat/contract/notification/payment) | BE chưa chạy (cần Postgres 16 + Redis 7 live theo AGENTS.md) | `make infra-up` + `./mvnw spring-boot:run` rồi FE `pnpm dev` |
| 3 | Luồng auth/profile/payment end-to-end (login CLIENT → escrow sandbox → IPN → WS → release) | Cần BE live + tài khoản test + key VNPAY Sandbox trong `.env` | Đủ hạ tầng + key (key chưa có — đang placeholder) |
| 4 | `./mvnw -B clean test` (BE) | Cần Postgres+Redis live; commit 5 là docs-only nên compile là đủ | Hạ tầng live (chạy ở commit 6 khi có code Java) |
| 5 | Unit test framework FE | Repo chưa có vitest/jest (ngoài scope Phase 0) | Mở task riêng nếu muốn (hiện chỉ có throwaway asserts, đã xóa temp) |

## 3. Đánh giá

- Mức độ tin cậy hiện tại: **nền FE chắc** (lint/type/build/serve/runtime-lib/runtime-helper đều xanh).
- Rủi ro còn lại nằm ở **tích hợp live** (WS handshake, JWT/refresh, VNPay IPN) — đúng bản chất Phase 2, sẽ test khi có BE chạy + key sandbox.
- Không phát hiện regression nào từ 5 commit.

## 4. BỔ SUNG — Test live BE + REST + WS (04/10/2026, theo yêu cầu "test hết đi")

Hạ tầng dựng thật: `docker compose up -d postgres redis` (cả 2 healthy) + `./mvnw spring-boot:run`
(BE lên `:8080`, healthz `{"status":"UP"}`).

⚠️ **Phát hiện hạ tầng:** BE log ghi `PostgreSQL 17.10`, trong khi container là postgres:16 —
máy có **native PostgreSQL 17** (`postgresql-x64-17`) đang chiếm port 5432, BE nối vào đó
(Flyway validate đủ 9 migrations). Test vẫn hợp lệ (PG thật, schema thật), nhưng cần biết
để không nhầm DB khi debug. Docker postgres:16 hiện idle.

| # | Test live | Kết quả |
|---|---|---|
| 1 | Register CLIENT + FREELANCER (`POST /auth/register`) | ✅ 200 cả 2, trả `accessToken` |
| 2 | `GET /auth/me` (Bearer) | ✅ 200 |
| 3 | `GET /profiles/client/me` lần đầu (CLIENT mới) | ✅ 200, object rỗng (`companyName` null) — đúng semantics spec "không lỗi" |
| 4 | `PUT /profiles/client/me` thiếu `companyName` | ✅ 400 (đúng `@NotBlank`) |
| 5 | `PUT` đủ field + `GET` prefill | ✅ 200, prefill đúng (`Cong ty ABC / Fintech / https://abc.test`) |
| 6 | Unicode `Công ty ABC / Mô tả công ty` (gửi UTF-8 bytes) | ✅ 200; verify trực tiếp trong PG native (UTF8): `Công ty ABC \| Fintech` lưu đúng |
| 7 | Freelancer `GET /profiles/client/me` | ✅ **403** (guard chặn đúng — dứt điểm nghi vấn ở `REPORT_Client_Profile.md` §5: acc nào vào được là do nó mang role CLIENT) |
| 8 | Freelancer `GET /profiles/freelancer/me` | ✅ 200 |
| 9 | SockJS info `GET /ws/info` | ✅ 200 `{"websocket":true, …}` |
| 10 | STOMP CONNECT bằng **đúng lib FE** (`@stomp/stompjs@7.3.0` + `sockjs-client@1.6.1`, script ở temp ngoài repo) | ✅ `CONNECTED, version=1.2` (chưa auth — BE cho CONNECT ẩn danh, authorize theo destination/guard ở tầng message) |
| 11 | `./mvnw -B clean test` (đúng lệnh PR CI) | ✅ `BUILD SUCCESS` (Tests run: 1, Failures: 0, Errors: 0, Skipped: 1) |
| 12 | `./mvnw compile` (sanity BE không bị đụng) | ✅ exit 0, `git diff` BE rỗng |

Ghi chú trung thực:
- Lần PUT full đầu tiên bị 400 là **lỗi harness test của mình** (PowerShell 5.1 mã hóa sai body có dấu),
  không phải lỗi BE — đã chứng minh bằng T6a/T6c + đối chiếu DB.
- Test data đã tạo 3 acc `*@test.local` + 2 client_profiles trong PG native — dữ liệu test, không ảnh hưởng seed.
- BE vẫn đang **chạy** để user click-test tay (PID/log trong `Temp\opencode`); lệnh dừng và `infra-down` xem mục 5.

## 5. Môi trường test còn giữ (để user dùng tiếp)

- BE live: `http://localhost:8080` (log: `Temp\opencode\be-run.log`, restart: `Temp\opencode\run-be.cmd`).
  Dừng: `taskkill /PID <pid> /F` (pid trong `Temp\opencode\be-pid.txt`).
- DB dùng thực tế: **native PostgreSQL 17** (`localhost:5432/sam_db`), không phải container.
- Redis: container `sam-redis`. Dừng hạ tầng: `docker compose stop postgres redis` trong `sam-be/`.
- FE dev đã có sẵn trên `:5173` (tiến trình node của user/môi trường, serve đúng code mới) → mở browser test tay ngay.

## 6. Playwright — đã thử, KHÔNG chạy được trong môi trường này (04/10/2026)

- User duyệt cài Playwright để auto test browser. Đã cài `@playwright/test@1.63.0` trong
  **thư mục temp ngoài repo** (`Temp\opencode\e2e` — repo không dính thêm file nào).
- Download browser thất bại ở cả 2 nguồn (CDN chính `cdn.playwright.dev` và mirror `npmmirror`,
  timeout 30s × 5 lần): môi trường chặn download file lớn, trong khi npm registry vẫn thông.
  Kiểm tra máy: không có Chrome/Edge/Firefox nào (`where.exe`, Program Files, cache playwright đều trống).
- Kết luận: **không thể auto browser tại đây**. Script e2e chưa viết (viết cũng không chạy được) —
  sẽ viết khi có browser (máy khác hoặc mạng mở), hoặc chuyển sang checklist tay dưới đây.

## 7. Checklist click-test tay (user làm trên browser, 10 phút — BE + FE đều live sẵn)

1. Mở `http://localhost:5173/register` → đăng ký CLIENT mới → expect vào `/client/profile`
   (đúng REPORT_Client_Profile §6: CLIENT về trang profile sau đăng ký).
2. Bỏ trống Tên công ty → Lưu → expect lỗi đỏ (BE 400 đã chứng minh ở mục 4-T5).
3. Điền đủ (nhập tiếng Việt có dấu) → Lưu → expect banner xanh; reload → prefill còn (T6a–T6c đã pass ở API).
4. Đăng xuất → đăng ký FREELANCER mới → gõ tay `/client/profile` → expect bị đá về `/freelancer`
   (guard đã pass ở API T7 — đây là bản browser của cùng case).
5. Vào `/freelancer/profile/create` → điền headline + skills → Lưu → reload prefill còn.
6. DevTools → Network: `GET /profiles/client/me` với token freelancer phải **403**;
   với token client phải **200**.

## 8. Còn chờ user: key VNPAY Sandbox

User xác nhận sẽ cung cấp `VNPAY_TMN_CODE` + `VNPAY_HASH_SECRET` → bỏ vào `sam-be/.env`
(tuyệt đối không paste vào chat — AIRule §5). Có key mới test được escrow end-to-end
(tạo payment → redirect sandbox → IPN → WS) ở commit 6.
