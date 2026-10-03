# Frontend Mapping Plan — giao diện mẫu → `quan tri`

## Scope and rules

Plan này chỉ map UI/UX mẫu vào React/Vite frontend thật. Frontend thật (`quan tri`) vẫn là nguồn kiến trúc; service/API Spring hiện hữu vẫn là contract. Không có thay đổi frontend/backend/database trong lần lập kế hoạch này. Route, token, Axios Bearer interceptor, `/users/me`, role guard và local logout được giữ lại. Role code chỉ dùng `ADMIN`, `ACCOUNTANT`, `EMPLOYEE`, `USER`; nhãn “Nhân viên” có thể thay cho “Staff”, không tạo `STAFF`.

Backend-missing functions phải dùng placeholder/unavailable rõ ràng, tuyệt đối không fallback mock mà không báo cho người dùng. `filePath`/MinIO object key không được hiển thị hoặc dùng trực tiếp; UI preview/download dùng service endpoint hiện có.

## Current frontend audit

| Area | Current real frontend |
|---|---|
| Framework/style | React + Vite + TypeScript; global CSS/Tailwind-style class usage. No context/hooks/state-manager, dark-mode system, toast, shared modal/table/skeleton/empty-state component found. |
| Shared shell | role-specific Admin/Accountant/Employee layouts and sidebars; legacy generic `Layout`, `Sidebar`, `Topbar`, `StatusBadge`, `StatCard` also exist. |
| Auth | `authService.ts`, `api.ts`, `ProtectedRoute`, `RoleGuard`; real login/register/me/password calls and localStorage token/user. |
| Real API services | auth, document, invoice, dashboard, classification, financial transaction, role/permission, AI test. |
| Mock | `mockData.ts` and `mockData.js` are duplicate mock modules; no current page imports were found. |
| Routes | `App.tsx` uses role layouts; includes genuine pages plus `UnavailableFeature` placeholders. Employee reuses accountant documents/detail and employee-only upload route. |

## Screen Mapping

| Sample screen | Real screen / route | Action |
|---|---|---|
| Login | `pages/Login.tsx` → `/login` | RESTYLE; retain `authService.login`. |
| Common document detail | `AccountantDocumentDetail.tsx` / legacy `DocumentDetail.tsx` → role document detail URLs | ADAPT_EXISTING_API; consolidate visual model later, not routes now. |
| Admin Dashboard | `AdminDashboard.tsx` → `/admin/dashboard` | RESTYLE + CONNECT_EXISTING_API. |
| Admin Users | `admin/UserManagement.tsx` → `/admin/users` | RESTYLE + CONNECT_EXISTING_API. |
| Admin Permissions | `RoleManagement.tsx` → `/admin/roles` | ADAPT_EXISTING_API; roles/permission-group UI, not handover matrix yet. |
| Admin Categories/document types | no routed page | MISSING_UI; backend categories/types exist, add UI only in approved F2. |
| Admin Suppliers | no page/API | BACKEND_MISSING. |
| Admin AI & OCR | `accountant/AiTest.tsx` is routed only for accountant | MISSING_UI; admin health/config distinctions required. |
| Admin Storage | `/accountant/storage` placeholder | PLACEHOLDER_KEEP; backend admin storage absent. |
| Admin Audit logs | no page/API | BACKEND_MISSING. |
| Admin Settings/company | `/admin/settings` placeholder | PLACEHOLDER_KEEP. |
| Accountant Dashboard | `AccountantDashboard.tsx` | RESTYLE + CONNECT_EXISTING_API. |
| Accountant Documents | `AccountantDocuments.tsx` | RESTYLE + ADAPT_EXISTING_API. |
| Accountant Upload | `AccountantUpload.tsx` | RESTYLE + CONNECT_EXISTING_API. |
| Accountant Classification | `/accountant/classification` placeholder | PLACEHOLDER_KEEP; classification API exists but no screen. |
| Accountant reconciliation | no routed page | MISSING_UI; adapt current reconciliation API, not bank statement mock. |
| Financial transactions | `FinancialTransactions.tsx` | RESTYLE + CONNECT_EXISTING_API. |
| Accountant reports | `/accountant/reports` placeholder | PLACEHOLDER_KEEP; reports API absent. |
| Employee Dashboard | `EmployeeDashboard.tsx` | RESTYLE + CONNECT_EXISTING_API. |
| Employee My Documents | reuses `AccountantDocuments.tsx` → `/employee/documents` | RESTYLE + ADAPT_EXISTING_API; owner-safe columns/actions. |
| Employee Upload | reuses `AccountantUpload.tsx` → `/employee/upload` | RESTYLE + ADAPT_EXISTING_API; employee flow only. |
| Employee Detail | reuses `AccountantDocumentDetail.tsx` | ADAPT_EXISTING_API; presentation/action gating required. |
| Notifications | no route/page/API | BACKEND_MISSING. |
| Global search | no component/route; server filename search only | MISSING_UI; limited API adapter later. |

## Component Mapping

| Sample component | Existing component | Action |
|---|---|---|
| Dashboard layout/sidebar/topbar | three role layouts/sidebars; legacy `Topbar`, `Sidebar`, `Layout` | RESTYLE role layouts; reuse one proven shell direction, remove no code during mapping. |
| Status badge | `StatusBadge.tsx` | ADAPT for derived processing/review display state; retain backend values. |
| KPI/stat card | `StatCard.tsx` | RESTYLE/reuse. |
| Data table/filter bar | no shared table/filter component found | MISSING_UI; create reusable presentation component only in F0 after audit of page duplication. |
| Modal | no shared modal found | MISSING_UI; reusable UI-only component candidate. |
| File dropzone/preview | upload page-local implementation | ADAPT_EXISTING_API; reuse logic, extract only if duplication proves need. |
| Global search | none | MISSING_UI; no full API support yet. |
| Charts | page-local rendering/none confirmed shared | RESTYLE; derive only from real dashboard API fields. |
| Loading/empty/error | page-local status handling | ADAPT; add consistent shared states in F0. |
| Toast | no shared toast found | MISSING_UI; map existing inline API feedback first. |
| Dark mode | no verified theme context/toggle | BACKEND_MISSING is not applicable; MISSING_UI (pure frontend optional UX). |

## API Mapping

| Screen | Existing service | Existing API | Action |
|---|---|---|---|
| Login/session | `authService` | auth login/register; users/me; password | KEEP; restyle UI only. |
| Documents list | `documentService.getDocumentPage` | GET `/api/v1/documents` | ADAPT_EXISTING_API; centralize 1-based UI ↔ 0-based API conversion in service/adapter. |
| Document detail | document + invoice + OCR + classification services | document detail, `/ocr`, `/invoice`, `/classification` | CONNECT_EXISTING_API; compose ViewModel with loading/partial-error/empty handling. |
| File preview/download | `documentService` blob methods | `/documents/{id}/preview`, `/download` | KEEP; never consume `filePath`. |
| Upload/process | `documentService.uploadDocument/processDocument` | multipart `/upload`, `/process` | CONNECT_EXISTING_API; disclose PDF/image constraints and errors. |
| Dashboard | `dashboardService`, `documentService` | statistics/financial/documents | ADAPT_EXISTING_API; cards/charts only when fields exist; label missing series unavailable. |
| Invoice editing | `invoiceService` | document invoice read; invoice update | ADAPT_EXISTING_API; role/action gating. |
| Classification | `classificationService` | current document classification endpoints | ADAPT_EXISTING_API; no approve/reject workflow facade. |
| Financial transactions | `financialTransactionService` | transactions/categories | CONNECT_EXISTING_API. |
| Roles/permissions | `roleService` | roles/role permissions/permissions/groups | CONNECT_EXISTING_API. |
| AI image test | `aiService` | `/api/v1/ai/test` | CONNECT_EXISTING_API; preserve actual error state. |

## Mock Mapping

| Mock/local asset | Real API available? | Action |
|---|---|---|
| `mockData.ts` documents/dashboard | yes for document basics/dashboard basics | MOCK_KEEP until its legacy/dead consumers are confirmed; do not silently use as fallback. |
| `mockData.js` duplicate | no direct consumer found | REMOVE_DUPLICATE candidate only after a separate import/build audit. |
| Sample handover demo accounts | yes, credential login | do not port behavior; use only visual styling. |
| Sample OCR/AI simulation | partial real API | show real request state/error; preserve unavailable UI if result data absent. |
| Sample workflow transitions/comments/notifications | no verified API | MOCK_KEEP only if explicitly labelled demo; otherwise placeholder. |
| Sample suppliers/settings/storage/backup | no verified API | PLACEHOLDER_KEEP / BACKEND_MISSING, never fake persistence. |

## Missing Backend

| UI feature | Backend status | UI action |
|---|---|---|
| Submit/resubmit/start-review/approve/reject/request-info/archive/void | service/model partial, no verified controller API | BACKEND_MISSING; hide/disable action with explanatory unavailable state. |
| Suppliers | no model/API verified | BACKEND_MISSING. |
| Notifications/comments/tags | no API verified | BACKEND_MISSING. |
| Audit list | entity but no API verified | BACKEND_MISSING. |
| Company/admin configuration, backup/restore, storage administration | server config only | PLACEHOLDER_KEEP; no fake save. |
| Classification rules | no domain API verified | BACKEND_MISSING. |
| Bank statements | no source domain | NEEDS_DECISION; use reconciliation terminology only. |
| Reports/export | no report API | PLACEHOLDER_KEEP. |
| Full global semantic search | filename search is limited | BACKEND_MISSING for unsupported fields. |

## Route Mapping

| Sample route | Real route | Action |
|---|---|---|
| `/` role home | `/` redirects to active real role dashboard | KEEP. |
| `/admin/dashboard`, users | `/admin/dashboard`, `/admin/users`, `/admin/roles` | KEEP URLs; map sample permissions UI to roles first. |
| `/accountant/dashboard`, documents, detail, upload | equivalent current accountant routes | KEEP. |
| `/staff/home`, documents, upload | `/employee/dashboard`, `/employee/documents`, `/employee/upload` | ADAPT label/navigation only; retain role code. |
| `/documents/:id` common detail | role-specific `/admin|accountant|employee/documents/:id` | KEEP role scope; do not create duplicate common route in F0–F6. |
| Sample admin settings/audit/supplier etc. | no current route/API | only placeholder route after a UI phase is authorized. |

## Role Mapping

| Screen/action | ADMIN | ACCOUNTANT | EMPLOYEE |
|---|---|---|---|
| Dashboard | admin dashboard | accountant dashboard | own-data dashboard |
| Document list/detail | current admin route; backend policy applies | company-scoped intent, unresolved server gap documented | own documents only; UI gate accountant controls |
| Upload/process | current route | current route | upload route is EMPLOYEE-only |
| Invoice/classification editing | policy/action gate required | allowed by current services | do not show accountant/editor actions by reuse alone |
| Financial transactions/reconciliation | admin policy | current screen/API | no route/action |
| Roles/users/permissions | yes | no | no |
| Settings/storage/reports/notifications | placeholders only where route exists | placeholders only where route exists | no unsupported action surfaced |

Frontend gating is presentation only; it does not resolve the existing backend accountant company-scope findings documented in `INTEGRATION_PLAN.md`.

## Implementation Order

| Phase | Goal | Constraint / one build check after phase |
|---|---|---|
| F0 — Shared UI | role layouts/sidebar/topbar, badges, table/filter primitives, loading/empty/error/toast visual system | preserve existing imports/services/routes; build once. |
| F1 — Auth | sample visual language for login; retain real auth service/redirects | no demo selector; build once. |
| F2 — Admin | dashboard/users/roles/permissions/documents; categories/types UI only if scope approved | real APIs only; unsupported pages remain clear placeholders; build once. |
| F3 — Accountant | dashboard/documents/detail/upload/financial restyle and adapters | no workflow endpoint simulation; build once. |
| F4 — Employee | dedicated dashboard/my-documents/detail/upload presentation and action gating | no accountant actions exposed by component reuse; build once. |
| F5 — Advanced existing APIs | OCR/AI/classification/reconciliation UI using existing service calls | processing/error states; no bank-statement invention; build once. |
| F6 — Missing backend UI | settings/storage/notifications/audit/reports/supplier shells only | label unavailable; no fake persistence; build once. |

## Highest-priority frontend issues before implementation

1. Employee routes reuse accountant list/detail/upload components, risking inappropriate actions in the UI.
2. `filePath` is returned by current document DTO; UI must keep it out of display/access flows.
3. Document detail issues four independent calls and needs a standard partial-failure/loading/empty model.
4. Existing document presentation must derive workflow labels from processing and review statuses without losing either.
5. Page-number normalization belongs in `documentService`/adapter, not page components.
6. No shared table/filter/modal/skeleton/empty/toast layer was found despite multiple screen needs.
7. Duplicate `mockData.ts`/`mockData.js` must be retained until an approved dead-code/import audit; never use as invisible API fallback.
8. Placeholder routes must remain visibly unavailable until backend domains exist.
9. Dashboard charts must distinguish real, derived, and unavailable values instead of filling gaps with mock data.
10. Existing backend accountant scope gaps must not be widened or masked by frontend route/parameter changes.

## Mapping status count

Screen primary classifications (one count per listed screen): **KEEP_UI 0; RESTYLE 8; CONNECT_EXISTING_API 5; ADAPT_EXISTING_API 5; MOCK_KEEP 0; PLACEHOLDER_KEEP 4; MISSING_UI 3; BACKEND_MISSING 3.** Some screens deliberately carry a secondary action (for example RESTYLE + API connection); counts use their primary migration action.

`FRONTEND_MAPPING_READY = YES`
