# BÁO CÁO HOÀN THÀNH — Commit 4: `refactor(fe): add shared ApiResponse error helper`

> Ngày: 04/10/2026 · Repo: **`sam-fe` only** (BE không đụng).
> Căn cứ: `sam-be/AIRule.md` · `AGENTS.md` (FE) · `sam-be/docs/API_Specification_v1.md` (§I.1 — `ApiResponse`, code 1000) ·
> `sam-be/docs/PLAN_FE_BE_Integration.md` (mục VIII, commit 4).
> Branch: `refactor/api-error-helper` (stack trên `chore/add-ws-libs`) ·
> Commit: `5c43fbf refactor(fe): add shared ApiResponse error helper`.

## 1. Kết quả: ĐÃ LÀM XONG

Helper lỗi `ApiResponse` dùng chung đã có; các api module mới (commit 9, Phase 2) dùng thay vì copy `if (!result) throw`. Diff: 1 file mới, 51 insertions, 0 deletions.

## 2. Chi tiết những gì đã làm

### 2.1. File TẠO MỚI (1 file duy nhất)

`sam-fe/src/lib/api-error.ts` — 2 hằng + 5 hàm, không import thêm lib nào (duck-typing, zero dependency):

| Export | Mục đích (map spec/code cũ) |
|---|---|
| `API_SUCCESS_CODE = 1000` | Code thành công theo `API_Spec §I.1` (trước đây là số magic rải rác, `axios.ts` cũng không đặt tên) |
| `API_TOKEN_COMPROMISED_CODE = 1016` | Đặt tên cho magic number đã có trong `axios.ts` (không sửa `axios.ts` — AIRule §4) |
| `isSuccessResponse(res)` | `res?.code === 1000` |
| `getApiErrorMessage(res, fallback)` | `message` của BE, fallback khi rỗng; kèm chi tiết `errors` map (field có sẵn trong `src/types/api.ts` nhưng chưa ai dùng) |
| `assertApiSuccess(res, fallback)` (`asserts`) | Cho endpoint void — VD `API-JB-05` invite trả `result: null` khi thành công → chỉ check code, không check result |
| `requireApiResult(res, fallback)` | Cho endpoint data — check code + `result != null`, trả về result đã narrow type |
| `extractApiResponse(error)` | Lấy body `ApiResponse` từ axios failure trong `catch` (thay vì mỗi page tự mò `error.response?.data`) |

### 2.2. Không đụng (đúng plan + AIRule §4)

- `src/lib/axios.ts` (interceptor 401/1016) giữ nguyên — helper không redirect trùng (tránh double-redirect `/login`).
- `src/api/auth.ts`, `src/api/profile.ts`, mọi page giữ nguyên — refactor dần ở Phase 2, không refactor lan man.
- `src/lib/README.md` là mẫu minh họa chung (còn ghi ví dụ `firebase.ts` không tồn tại) → không sửa.
- Không cài package mới (AIRule §5). Không hardcode secret/URL (AIRule §5).
- **BE giữ nguyên 100%.**

### 2.3. Quyết định kỹ thuật (ghi rõ để Phase 2 dùng đúng)

- `requireApiResult` check **nullish** (`=== null || === undefined`), chặt hơn falsy (`!result`) của code cũ — cố ý: `''`/`0` là giá trị hợp lệ (VD: `result` string rỗng), chỉ `null/undefined` mới là thiếu dữ liệu. Code cũ (`auth/profile`) chưa đổi theo để giữ diff tối thiểu.
- Tách `assertApiSuccess` (void) riêng khỏi `requireApiResult` (data) vì spec có endpoint thành công mà `result: null` (`API-JB-05`).

## 3. DRY check (AIRule §2)

- Quét `src/**`: chưa tồn tại helper nào (`assertApiResult|getApiErrorMessage|isSuccess|api-error` = 0 hit) → tạo mới là đúng.
- Type `ApiResponse` lấy từ `src/types/api.ts` có sẵn (không định nghĩa lại shape response).
- Style JSDoc + format theo đúng Biome repo (single quote, semicolons, width 100).

## 4. Verify đã chạy (trong `sam-fe/`, branch `refactor/api-error-helper`)

| Check | Lệnh | Kết quả |
|---|---|---|
| Lint (đúng lệnh CI) | `pnpm exec biome ci src/lib/api-error.ts` | ✅ Pass (lần đầu báo format signature multi-line → đã `biome check --write` gộp 1 dòng rồi pass lại; `tsc` chạy lại sau format vẫn pass) |
| Typecheck | `pnpm exec tsc --noEmit` | ✅ Pass (exit 0, chạy 2 lần: trước và sau format) |
| Hook pre-commit | biome staged + `tsc` + `check-features-imports.sh` | ✅ Pass |
| Hook commit-msg | `commitlint` | ✅ Pass |

## 5. Trạng thái sau task

- `git status` clean. `git log`: `5c43fbf` trên `0d54ab6` (commit 3) trên branch `refactor/api-error-helper`.
- Phase 0 (nền) **hoàn tất cả 4 commit** (1→4). Tiếp theo: **Phase 1a — commit 5** `docs(payment): add Payment + Subscription API spec v1` (repo `sam-be`, file spec mới `docs/API_Specification_Payment_v1.md`, chưa code).
- Chưa push, chưa tạo PR (chờ chỉ đạo).
