# BÁO CÁO HOÀN THÀNH — Func Profile cho CLIENT

> Ngày: 04/10/2026 · Phạm vi: FE là chính, BE giữ nguyên (đã đúng spec).
> Căn cứ: `sam-be/AIRule.md` · `sam-be/docs/API_Specification_v1.md` (mục IV.B, API-PF-03/04) ·
> `sam-be/docs/SYSTEM_SPECIFICATION.md` · `AGENTS.md` · Plan `sam-be/docs/PLAN_Client_Profile.md` (+ chốt 4 điểm với user).

## 1. Kết quả: ĐÃ LÀM XONG theo plan

Func Client Profile (xem + cập nhật hồ sơ công ty) đã implement đầy đủ, map 1-1 với API-PF-03/04.
BE không sửa dòng nào (đã verify đúng spec từ trước).

## 2. Chi tiết những gì đã làm

### 2.1. File SỬA (5 file, diff tối thiểu — đúng AIRule §4)

| # | File | Thay đổi |
|---|---|---|
| 1 | `sam-fe/src/types/profile.ts` | Append `ClientProfileRequest` (`companyName*` + `industry/websiteUrl/description` optional) và `ClientProfileResponse` (`id/userId/fullName/email` + 4 field company). Không xóa type cũ. |
| 2 | `sam-fe/src/api/profile.ts` | Append `getClientProfile()` → `GET /profiles/client/me` và `updateClientProfile(data)` → `PUT /profiles/client/me`. Copy đúng pattern freelancer: check `!res.data.result → throw Error(message)`. Base URL giữ nguyên từ `apiClient` (`${VITE_API_URL}/api/v1`). |
| 3 | `sam-fe/src/routes/paths.ts` | Thêm 1 const: `PATH_CLIENT_PROFILE = '/client/profile'` (theo chốt với user). |
| 4 | `sam-fe/src/routes/index.tsx` | Thêm 1 import `ClientProfilePage` + 1 route `<Route path={PATH_CLIENT_PROFILE} element={<ClientProfilePage />} />` trong `<RoleGuard allowedRoles={['CLIENT']}>`. Không thêm guard mới. |
| 5 | `sam-fe/src/pages/CreateFreelancerProfilePage.tsx` | Chỉ đổi dòng import schema sang barrel `@/features/profile` (theo chốt "sửa luôn" + luật `check-features-imports.sh`). Không đụng logic. |

### 2.2. File TẠO MỚI (3 file)

| # | File | Nội dung |
|---|---|---|
| 1 | `sam-fe/src/features/profile/schemas/client-profile-schema.ts` | Zod `clientProfileSchema`: `companyName` min(1)/max(255) (mirror BE `@NotBlank`); `industry` text tự do max(255) (theo chốt); `websiteUrl` optional + refine `http(s)://` (mirror `portfolioUrl`, BE không validate — chỉ FE); `description` max(2000) + counter. Export `ClientProfileFormData`. |
| 2 | `sam-fe/src/features/profile/index.ts` | Barrel: re-export freelancer schema + client schema. Mọi page profile import từ `@/features/profile`. |
| 3 | `sam-fe/src/pages/ClientProfilePage.tsx` | Page upsert 1 form (đúng semantics PUT upsert): header identity read-only (`fullName/email` từ `useAuthStore`), form RHF + `zodResolver`, GET → `reset()` prefill (`id == null` → banner chào mừng, không báo lỗi — đúng spec "object rỗng"), submit trim + `'' → null`, banner đỏ/xanh + spinner + bottom fixed action bar (copy style freelancer). Không có logic skills. Route đã guard nên page không check role lại. |

### 2.3. Quyết định đã chốt được áp dụng

- Industry: text tự do (không dropdown).
- Route: `/client/profile` trong guard CLIENT.
- Barrel: đã fix deep-import ở page freelancer cũ.
- Plan giữ ở `sam-be/docs/` (file này đặt cạnh đó).
- Mặc định còn lại: website refine `http(s)` (báo lỗi, không auto-prepend); description max 2000; 1 form upsert; `fullName/email` read-only; giữ pattern `useEffect + profileApi` (không tách React Query).

### 2.4. BE: không sửa (đã khớp spec)

- `ProfileController` (`GET/PUT /api/v1/profiles/client/me`, `PreAuthorize CLIENT`, `SecurityUtils.getCurrentUserId()`), DTOs, entity `client_profiles`, `ProfileServiceImpl` (GET object rỗng, PUT upsert, `fullName/email` từ `User`) — tất cả đã đúng API-PF-03/04. Không tạo migration (Flyway `validate`).

## 3. Verify đã chạy (trong `sam-fe/`)

| Check | Lệnh | Kết quả |
|---|---|---|
| Typecheck | `pnpm exec tsc --noEmit` | ✅ Pass (no output) |
| Build | `pnpm run build` (`tsc -b && vite build`) | ✅ Pass — `dist/` built in ~3.6s, 232 modules (chỉ warning chunk >500kB có sẵn) |
| Lint | `pnpm exec biome lint <8 file đổi/mới>` | ✅ Pass — `Checked 8 files`, no errors |
| Import-barrel mới | `biome ci` trên 2 file schema/barrel + `ClientProfilePage` | ✅ Pass (sau khi fix thứ tự `organizeImports`: barrel `@/features/profile` đứng đầu) |
| Check script `check-features-imports.sh` | Không chạy được trên Windows (không có `bash`), đã grep tay | ✅ 2 page profile import từ barrel; còn `LoginPage/RegisterPage` deep-import `auth/schemas` là **có sẵn, ngoài scope** (không đụng theo AIRule §4) |
| `biome ci` (format) tổng | `pnpm exec biome ci <files>` | ⚠️ **Fail có sẵn, không do task này**: repo dùng CRLF, Biome expect LF — kể cả file chưa đụng (`src/App.tsx`) cũng fail y hệt. Diff của task vẫn tối thiểu (46 insertions, 4 deletions), không reformat diện rộng để tránh vi phạm AIRule §4. CI `biome ci .` sẽ đỏ cho tới khi repo thống nhất line-ending — cần task riêng. |

## 4. Còn mở / đề xuất tiếp (không chặn nghiệm thu)

1. **Entry-point link**: ~~chưa gắn~~ → **đã xong (xem mục 5)**: nút "Hồ sơ công ty" trong dropdown avatar + menu mobile của `ClientDashboardHeader`.
2. **Line-ending repo**: `biome ci .` đỏ toàn repo do CRLF (có sẵn). Đề xuất task riêng: thống nhất LF + chạy `biome check --write` một lần.
3. **Auth barrel**: `LoginPage/RegisterPage` vẫn deep-import `features/auth/schemas` — để nguyên theo AIRule §4; nếu muốn sạch CI-check thì mở task barrel cho `features/auth`.
4. **Test tay cần BE live**: login role CLIENT → GET trống → PUT thiếu `companyName` (expect 400) → PUT đủ → reload prefill → 401 auto-refresh. Chưa chạy vì cần Postgres+Redis + tài khoản CLIENT.
5. Không cài package mới, không hardcode secret/URL (dùng `VITE_API_URL` + `token-manager`) — đúng AIRule §5.
## 5. Bổ sung sau nghiệm thu (04/10/2026)

- Thêm nút **"Hồ sơ công ty"** vào `ClientDashboardHeader` (file `sam-fe/src/components/ClientDashboardHeader.tsx`):
  dropdown avatar desktop + menu mobile → navigate `/client/profile`. Copy đúng style/icon nút
  "Hồ sơ của tôi" phía freelancer. Verify: `tsc --noEmit` pass, `biome lint` pass.
- Trang profile freelancer có sẵn từ trước tại **`/freelancer/profile/create`**
  (`CreateFreelancerProfilePage`), đã có nút "Hồ sơ của tôi" trong `Header` (dropdown + mobile).
- Về báo lỗi "acc freelancer vẫn vào được `/client/profile`": code guard đúng
  (route nằm trong `RoleGuard(['CLIENT'])` — freelancer bị redirect về `/freelancer`;
  BE có `@PreAuthorize("hasRole('CLIENT')")` nên API cũng 403).
  Khả năng cao là: (a) acc test thực chất có role CLIENT trong DB — kiểm tra badge role
  trong dropdown avatar; (b) chưa logout acc CLIENT cũ trước khi login acc freelancer
  (`GuestGuard` chặn vào `/login` khi đang authenticated); (c)icloud vào lúc token hết hạn —
  mở DevTools → Network, xem `GET /profiles/client/me`: 403 = BE đã chặn đúng,
  200 với freelancer token = acc đó thực chất là CLIENT.

## 6. Mirror luồng freelancer sang client (04/10/2026, theo yêu cầu)

Freelancer có 2 cơ chế bắt buộc hoàn thiện profile: (a) đăng ký xong đẩy thẳng tới trang
tạo profile, (b) banner cảnh báo trên trang chính khi profile trống. Client đã được làm giống vậy:

| # | File | Thay đổi |
|---|---|---|
| 1 | `sam-fe/src/pages/RegisterPage.tsx` | CLIENT đăng ký xong navigate tới `/client/profile` (trước đây về dashboard) — giống freelancer về `/freelancer/profile/create`. 1 dòng. |
| 2 | `sam-fe/src/pages/ClientDashboardPage.tsx` | Fetch `getClientProfile()` khi mount; nếu `!companyName` (và đã load xong) → banner vàng cảnh báo "Hồ sơ công ty của bạn chưa hoàn thiện" + CTA về `/client/profile`. Copy đúng style banner freelancer (amber-50, icon tam giác, nút gradient). Trang này đã nằm trong `RoleGuard(CLIENT)` nên không lo guest. |

Verify: `tsc --noEmit` pass, `biome lint` pass.
