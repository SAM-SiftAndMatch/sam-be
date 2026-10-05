# PLAN — Func Profile cho CLIENT (FE là chính, BE đã sẵn)

> Tuân thủ tuyệt đối: `sam-be/AIRule.md` · `sam-be/docs/API_Specification_v1.md` (mục IV.B, API-PF-03/04) ·
> `sam-be/docs/SYSTEM_SPECIFICATION.md` · `AGENTS.md` (FE + BE) · Code BE/FE hiện có.
> Trạng thái: **PLAN-FIRST — chưa sửa code, chờ user xác nhận + thảo luận mục VII.**

## I. Kết luận khảo sát code hiện có (DRY — Tìm kiếm trước khi tạo mới)

### 1. Backend (`sam-be/`) — ĐÃ XONG, không cần sửa (chỉ verify)

| Item | Vị trí | Trạng thái |
|---|---|---|
| `GET /api/v1/profiles/client/me` (API-PF-03, Role `CLIENT`) | `modules/user/controller/ProfileController.java:49-58` | ✅ Đúng spec: `SecurityUtils.getCurrentUserId()`, trả `ApiResponse<ClientProfileResponse>` |
| `PUT /api/v1/profiles/client/me` (API-PF-04, Role `CLIENT`) | `modules/user/controller/ProfileController.java:60-70` | ✅ Đúng spec, `@Valid` + `companyName @NotBlank` |
| DTO req/res | `modules/user/dto/request/ClientProfileRequest.java`, `modules/user/dto/response/ClientProfileResponse.java` | ✅ Khớp spec: `companyName*` + `industry/websiteUrl/description` optional; res có `id/userId/fullName/email` |
| Entity | `modules/user/entity/ClientProfile.java` (`client_profiles`) | ✅ Upsert theo `user_id` unique |
| Service | `modules/user/service/impl/ProfileServiceImpl.java:127-160` | ✅ GET trả object rỗng (không lỗi) khi chưa có profile; PUT upsert; `fullName/email` lấy từ `User` (FE không gửi) |
| Auth tạo profile rỗng khi register | `modules/auth/service/impl/AuthServiceImpl.java:91-94` | ✅ Không cần seed thêm |
| Flyway | `ddl-auto=validate`, không sửa migration đã apply | ✅ Không đụng DB |

> Kết luận BE: **zero file sửa.** Chỉ cần verify bằng Swagger (`/swagger`) + `./mvnw -B clean test` (cần Postgres+Redis live theo AGENTS.md).

### 2. Frontend (`sam-fe/`) — THIẾU phần Client, đã có mẫu Freelancer để tái sử dụng

| Item | Vị trí | Trạng thái |
|---|---|---|
| API client | `src/api/profile.ts` | ❌ Chỉ có `getFreelancerProfile / updateFreelancerProfile / getSkills`. **Thiếu `getClientProfile / updateClientProfile`** |
| Types | `src/types/profile.ts` | ❌ Chỉ có Freelancer + `SkillOption`. **Thiếu `ClientProfileRequest/Response`** |
| Schema/Zod | `src/features/profile/schemas/freelancer-profile-schema.ts` | ❌ Chưa có `client-profile-schema.ts` |
| Barrel feature | `src/features/profile/` (chỉ có `schemas/`, **chưa có `index.ts`**) | ⚠️ Vi phạm tiềm ẩn quy tắc `scripts/check-features-imports.sh` (AGENTS.md: chỉ import từ barrel `@/features/<name>`) — `CreateFreelancerProfilePage.tsx:9-12` đang deep-import |
| Page | `src/pages/CreateFreelancerProfilePage.tsx` (801 dòng, mẫu UI chuẩn để clone) | ❌ Chưa có `ClientProfilePage.tsx` |
| Routes | `src/routes/paths.ts`, `src/routes/index.tsx` | ❌ Chưa có `PATH_CLIENT_PROFILE` + route `RoleGuard(CLIENT)` |
| State/auth | `src/stores/useAuthStore.ts` (`fullName/email/role`), `src/lib/axios.ts` (Bearer + auto-refresh, base `${VITE_API_URL}/api/v1`) | ✅ Tái sử dụng nguyên vẹn |
| Skill API `GET /skills?search=` | `SkillController.java` + `profileApi.getSkills` | ✅ Không liên quan Client (Client không có skills) — **không copy** |

## II. Plan triển khai (chỉ FE, BE giữ nguyên)

Tuân thủ AIRule §3 + §4: liệt kê file sửa/tạo, logic cốt lõi, không refactor ngoài phạm vi, giữ style hiện tại.

### Bước 1 — Types + API (`src/types/`, `src/api/`)

**1a. Sửa `src/types/profile.ts`** (append, không xóa type cũ):
```ts
export interface ClientProfileRequest {
  companyName: string;
  industry?: string | null;
  websiteUrl?: string | null;
  description?: string | null;
}
export interface ClientProfileResponse {
  id?: string | null;
  userId: string;
  fullName: string;
  email: string;
  companyName?: string | null;
  industry?: string | null;
  websiteUrl?: string | null;
  description?: string | null;
}
```
**1b. Sửa `src/api/profile.ts`** (append 2 hàm, copy pattern freelancer, throw khi `!result`):
- `getClientProfile(): Promise<ClientProfileResponse>` → `GET /profiles/client/me`
- `updateClientProfile(data: ClientProfileRequest)` → `PUT /profiles/client/me`
- Lưu ý: `apiClient` đã có base `/api/v1` nên path là `/profiles/client/me` (không prefix lại).

### Bước 2 — Zod schema + Barrel (`src/features/profile/`)

**2a. Tạo mới `src/features/profile/schemas/client-profile-schema.ts`:**
- `companyName: z.string().min(1,'Tên công ty không được để trống').max(255)` + `.trim()` ở submit (BE `@NotBlank` — FE trim trước gửi, mirror freelancer `headline`).
- `industry: z.string().max(255).optional()` (xem thảo luận VII.1).
- `websiteUrl: optional, refine http(s) giống `githubUrl/portfolioUrl`` (BE không validate format — chỉ FE).
- `description: z.string().max(2000).optional()` (DB `TEXT`, spec không giới hạn — cần chốt VII.3).
- Export `ClientProfileFormData = z.infer<...>`.

**2b. Tạo mới `src/features/profile/index.ts`** (barrel — bắt buộc theo `check-features-imports.sh`):
- Re-export freelancer schema + client schema (+ sau này là hooks/components).
- Các page **phải** đổi sang `import { X } from '@/features/profile'` (áp dụng cho cả page freelancer hiện tại nếu lọt vào file sửa — cần xác nhận vì AIRule §4 "không refactor ngoài phạm vi").

> Không cài thêm lib (AIRule §5: `zod`, `react-hook-form`, `@hookform/resolvers` đã có; không hardcode token/URL — dùng `VITE_API_URL`).

### Bước 3 — Page Client (`src/pages/ClientProfilePage.tsx`, tạo mới)

Clone cấu trúc `CreateFreelancerProfilePage.tsx`, **lược bỏ toàn bộ logic skills** (search/dropdown/duplicate-banner/`useFieldArray`/`NumericFormat`):
- Header identity: avatar chữ cái + `fullName/email` từ `useAuthStore` (read-only, BE là nguồn chân lý).
- Form (React Hook Form + `zodResolver(clientProfileSchema)`):
  - `companyName*` (input, error đỏ như headline).
  - `industry` (input text — chốt text vs select ở VII.1).
  - `websiteUrl` (input url + icon globe, copy pattern portfolio).
  - `description` (textarea 4 rows + hint counter).
- Mount: `GET → reset()` prefill; `id == null` = "chưa có profile" → giữ form trống, banner gợi ý (không báo lỗi — đúng semantics spec "object rỗng").
- Submit: trim + `'' → null`, `PUT → success banner + scroll top` (copy `serverError/successMessage` pattern + `axios.isAxiosError` handling).
- Bottom fixed action bar: `Quay lại / Lưu thay đổi` (copy CSS gradient hiện có).
- Hiển thị `RoleGuard` ngoài route nên trong page không cần check role lại.

### Bước 4 — Routes (`src/routes/`)

**4a. Sửa `src/routes/paths.ts`:** thêm `export const PATH_CLIENT_PROFILE = '/client/profile';`
**4b. Sửa `src/routes/index.tsx`:** thêm route trong `<RoleGuard allowedRoles={['CLIENT']}>`:
```tsx
<Route path={paths.PATH_CLIENT_PROFILE} element={<ClientProfilePage />} />
```
- Không thêm guard mới; dùng `RoleGuard` sẵn có (AGENTS.md: routes/paths/guards trong `src/routes/`).

### Bước 5 — Verify (theo AGENTS.md FE)

- `pnpm exec biome ci <các file đổi>` (CI chạy `biome ci .` — không dùng `pnpm check` auto-fix).
- `pnpm exec tsc --noEmit` (hoặc `pnpm run build` = `tsc -b && vite build`).
- Test tay: login role CLIENT → GET trống → PUT thiếu `companyName` (expect 400) → PUT đủ → reload prefill → check 401 auto-refresh (`axios.ts`).
- BE (không sửa): `./mvnw -B clean test` trong `sam-be/` khi cần (yêu cầu Postgres+Redis live).

## III. File sẽ SỬA (5 file)

1. `sam-fe/src/types/profile.ts` — append Client types.
2. `sam-fe/src/api/profile.ts` — append 2 hàm client.
3. `sam-fe/src/routes/paths.ts` — thêm 1 const.
4. `sam-fe/src/routes/index.tsx` — thêm 1 route + 1 import page.
5. (Có điều kiện) `sam-fe/src/pages/CreateFreelancerProfilePage.tsx` — chỉ đổi dòng import sang barrel **nếu** CI/pre-commit bắt lỗi deep-import; mặc định không đụng (AIRule §4).

## IV. File sẽ TẠO MỚI (3 file + 1 plan này)

1. `sam-fe/src/features/profile/schemas/client-profile-schema.ts`
2. `sam-fe/src/features/profile/index.ts` (barrel)
3. `sam-fe/src/pages/ClientProfilePage.tsx`
4. `sam-be/docs/PLAN_Client_Profile.md` (file này — vì `sam-fe/` chưa có thư mục `docs/`, đặt cạnh `API_Specification_v1.md` để dễ đối chiếu spec)

> Không tạo migration, entity, controller, store mới. Không cài package mới.

## V. Logic cốt lõi (mapping spec → code)

- Auth: Bearer qua `apiClient` interceptor; `userId` do BE tự trích (`SecurityUtils`) — FE không gửi `userId`.
- Wrapper: `result` mới là data (`ApiResponse`); `!result → throw Error(message)`.
- Upsert + empty-object: GET lần đầu `id/companyName = null` là bình thường → form trống, không redirect login.
- Validation duy nhất BE enforce: `companyName @NotBlank` → FE `min(1)` + trim; còn lại optional.
- `fullName/email`: read-only từ `useAuthStore` + response; không có input sửa (đúng `ProfileServiceImpl.mapToClientProfileResponse`).
- Error handling đầy đủ (AIRule §5): `try/catch`, `axios.isAxiosError`, banner đỏ/xanh, loading spinner khi GET.
- Tuyệt đối không hardcode URL/token (dùng `VITE_API_URL` + `token-manager`).

## VI. Rủi ro đã thấy (chỗ chưa ăn khớp giữa spec và code)

1. **Barrel rule vs code hiện tại:** `CreateFreelancerProfilePage` deep-import `../features/profile/schemas/...` — nếu `check-features-imports.sh` bật, cả code cũ cũng fail. Plan đã gồm barrel + đổi import, nhưng đó là chạm vào file cũ (cần user duyệt).
2. **Không dùng React Query:** AGENTS.md nói "query hooks live in `src/api/`" + có `queryClient.ts`, nhưng page freelancer gọi `profileApi` trực tiếp trong `useEffect`. Plan giữ pattern cũ để đồng nhất (không refactor sang `useQuery` trừ khi user yêu cầu).
3. **`GET /skills` không auth-guard ở BE?** `SkillController` không có `@PreAuthorize` — freelancer page gọi được, client page không cần gọi nên không ảnh hưởng.
4. **Spec FE logic cho Client = rỗng:** API spec chỉ định nghĩa payload, không có luồng UI (không như AI/Job/Contract có sơ đồ) — UI đề xuất ở plan là suy từ mẫu freelancer, cần user chốt.

## VII. Cần thảo luận / quyết định trước khi code (AIRule §1 — không đoán mò)

1. **Industry — text hay select?** Spec ví dụ tự do ("Fintech", "IT Outsourcing"), không có enum. Đề xuất: text input 255 ký tự (giữ nguyên BE). Anh/chị có muốn dropdown cố định không? Nếu có, cho danh sách.
2. **Website URL — enforce `http(s)://`?** BE không validate. Đề xuất FE refine như `portfolioUrl` (rỗng hoặc bắt đầu http). Có đồng ý? Có tự prepend `https://` khi user quên không, hay báo lỗi?
3. **Description giới hạn bao nhiêu?** DB `TEXT`, spec không giới hạn. Đề xuất `max 2000` + counter. Chốt số?
4. **Route + entry point:** `/client/profile` có OK không? Link vào từ đâu (header dashboard `ClientDashboardHeader`, avatar menu)? Có cần thêm mục "Hồ sơ công ty" trên dashboard không?
5. **UX tạo mới vs chỉnh sửa:** Giữ 1 form upsert duy nhất như freelancer (GET trống → điền → PUT), hay tách 2 mode xem/sửa? Đề xuất 1 form (đúng semantics PUT upsert).
6. **fullName/email:** Khóa read-only (đúng BE). Có muốn cho đổi `fullName` ở đây không? Nếu có thì phải thêm API user-update (ngoài scope PF-03/04).
7. **Dùng React Query hay giữ `useEffect` trực tiếp?** Đề xuất giữ trực tiếp (đồng nhất freelancer). Nếu muốn chuẩn hóa `useClientProfile` hook trong `src/api/` + `features/profile/hooks/`, báo để mở rộng plan.
8. **Barrel có áp dụng ngược cho freelancer page không?** (sửa 2 dòng import cũ). Đồng ý chạm file cũ hay để nguyên?
9. **Vị trí file plan này:** `sam-fe/` chưa có `docs/`. Tạm đặt ở `sam-be/docs/PLAN_Client_Profile.md`. Có muốn tôi tạo thêm `sam-fe/docs/` và copy qua không, hay chuyển hẳn sang đó?
10. **Industry/website có bắt buộc theo nghiệp vụ không?** Spec + BE đều optional. Giữ optional hay có field nào bắt buộc thêm theo product?

## VIII. Quyết định đã chốt với user (04/10/2026)

- Industry: **text tự do** (giữ đúng BE/spec).
- Route: **`/client/profile`**, gắn trong `RoleGuard(CLIENT)`.
- Barrel: **được phép sửa file freelancer cũ** để đổi sang import barrel cho qua CI.
- Vị trí plan: **giữ ở `sam-be/docs/`**.

Còn mở (mặc định theo đề xuất nếu không phản hồi thêm): website refine `http(s)` như portfolioUrl;
description `max 2000` + counter; 1 form upsert duy nhất; `fullName/email` read-only;
giữ pattern `useEffect + profileApi` trực tiếp (không tách React Query vội).
