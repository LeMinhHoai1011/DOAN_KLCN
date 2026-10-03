# Page Mapping Detailed

## 1. Scope

Source audited: `FRONTEND_HANDOVER.md`, current frontend pages/services, and the verified source-contract findings in `INTEGRATION_PLAN.md`. This is a specification only: no code/API/route/backend/database change is proposed by this document. Existing behavior is `KEEP_BEHAVIOR` unless explicitly marked presentation-only.

## 2. Auth Exclusion

`AUTH = KEEP_AS_IS / OUT_OF_SCOPE`. Login, register, auth service, token storage, Axios interceptor, `/users/me`, guards, logout and password screens are not audited or targeted here.

## 3. Admin Pages

### A1 — Admin Dashboard (`pages/admin/AdminDashboard.tsx`, `/admin/dashboard`)

Sample UI: title/description; KPI cards for documents, processing, need-info, users, monthly finance/storage; charts for month/type/department; recent documents and audit activity. Real UI: title, four cards (`totalDocuments`, `totalInvoices`, `totalClassified`, `totalReviewRequired`), chart-unavailable card, recent-documents table. API: `dashboardService.getDashboardStatistics()` → GET `/api/v1/dashboard/statistics`; `documentService.getDocuments()` → GET `/api/v1/documents`.

| UI field | Current frontend | API field | Source | Status | Action |
|---|---|---|---|---|---|
| Total documents/invoices/classified/review | exists | four statistics fields | dashboard | KEEP | RESTYLE only |
| User/storage/monthly KPI, charts, audit activity | missing | absent | handover | BACKEND_MISSING | unavailable explanatory card |
| Recent id/name/type/date/status | exists | document id/original filename/mime/createdAt/status | document list | ADAPT_FRONTEND | use mapper; no mock |

Target: retain four real KPI cards and recent table; show charts/audit as unavailable, not generated values. Loading/error exist; replace later with shared F0 states. No breadcrumb/modal/filter.

### A2 — Users (`pages/admin/UserManagement.tsx`, `/admin/users`)

Sample UI: searchable/filterable users table, create/edit modal, role, active/locked control and document count. Real UI: header/add button; modal create (`username,password,fullName,email,role`); table full name/username, email, role select, status, lock/unlock; loading/empty/error. APIs: direct GET/POST `/api/v1/users`, PUT `/api/v1/users/{id}`, PUT `/api/v1/users/{id}/roles`; `roleService.getRoles()`.

| UI field/action | Current | API | Status | Target |
|---|---|---|---|---|
| username/full name/email/role/status | exists | User response / Role | KEEP | RESTYLE |
| company, department, user doc counts | missing | companyId only; no count endpoint | BACKEND_MISSING | omit |
| create | exists | POST users | KEEP | retain modal |
| edit identity | missing | PUT may exist but shape not verified | NEEDS_DECISION | no new control |
| assign role/lock | exists | roles PUT / user PUT status | KEEP | retain |
| search/filter/pagination | missing | no verified paged user API | BACKEND_MISSING | omit |

### A3 — Roles & Permissions (`pages/admin/RoleManagement.tsx`, `/admin/roles`)

Sample: role-permission matrix. Real: role list, create inline form (`code,name,description,active`), selected-role permission-group checkboxes. APIs: GET/POST `/roles`, GET `/permissions/groups`, PUT `/roles/{id}/permissions`. Target: preserve grouped RBAC model—do not flatten role, permission group, permission, role-permission into a sample matrix. Delete/update exist in service but not current UI. Coverage FULL for current screen.

### A4 — Admin Documents/Upload (shared Accountant pages, `/admin/documents`, `/admin/upload`)

Use the accountant table/upload mapping below with ADMIN destination/action gates. APIs are existing document services. Target has no separate admin-only data model; `KEEP_BEHAVIOR`, restyle shared pages later. Company selector is `BACKEND_PARTIAL`: list API supports companyId but frontend must not send arbitrary scope without approved admin policy.

### A5 — System Statistics (`pages/admin/SystemStatistics.tsx`, `/admin/statistics`)

Real page is a static mock: KPI values, bar trend, department load, top-user ranking. Sample requires real admin dashboard aggregates/charts. **REMOVE_FROM_TARGET mock data**; target is placeholder/unavailable until real aggregate API exists. No service/API calls, coverage NONE.

### A6 — Categories/Document Types/Settings

No routed categories/types page. Existing API: GET `/api/v1/documents/types`, GET `/api/v1/accounting-categories`; no audited UI service for admin CRUD. Status `MISSING_UI + EXISTING_API` for read display, `BACKEND_PARTIAL` for intended admin management. `/admin/settings` is `PLACEHOLDER`; settings persistence is server-only/absent.

## 4. Accountant Pages

### AC1 — Accountant Dashboard (`AccountantDashboard.tsx`)

Real: four statistics cards, three financial cards, expense-by-category list, recent document table; loading/error. APIs: statistics, GET `/dashboard/financial`, document list. Sample asks pending/VAT/error/expense KPI and several chart series. Target: retain real KPI and financial values; expense category list is `DERIVED_FROM_REAL_DATA`; VAT/status/month chart data is `BACKEND_MISSING`, placeholder only. Recent table same source limitations as AC2.

### AC2 — Documents (`AccountantDocuments.tsx`)

Sample: header, search, status/type/category/uploader/date/amount filters, sortable table/card, 1-based paging, view/download/review. Real: header/upload, server search, processing-status/date filters; client mime filter; static pagination controls; table id/name/mime/date/amount/confidence/status/actions. API GET `/documents` supports `search,processingStatus,reviewStatus,dateFrom,dateTo,companyId,typeId,uploaderId,page,size,sort`; service always begins `page:0,size:50`.

| Sample column | Real column | API source | Status | Target |
|---|---|---|---|---|
| Document ID/file name/type/date | exists | DocumentResponse id/originalFileName/documentType/createdAt | FULL | KEEP/RESTYLE |
| Processing/review status | processing only rendered | status + reviewStatus | PARTIAL | ADAPT_FRONTEND status mapper |
| Supplier/invoice number/amount | supplier/amount placeholders | only separate Invoice API | DATA_NOT_AVAILABLE_IN_LIST | omit; no N+1 |
| confidence | placeholder | only OCR/classification detail | DATA_NOT_AVAILABLE_IN_LIST | omit |
| uploader/company/category/tags | missing | uploaderId/companyId/typeId only | PARTIAL | show only approved IDs/labels; no invented filters |

| Action | Current | Existing API | Authorization | Status | Target |
|---|---|---|---|---|---|
| View | yes | GET detail | scoped backend | KEEP | retain |
| Delete icon | visual only, no handler | DELETE exists service missing | permission/scoped | MISSING_UI | do not activate in restyle |
| Preview/download | absent | endpoints exist | scoped backend | MISSING_UI | later connect |
| Approve/reject/archive | absent | no workflow controller | accountant | BACKEND_MISSING | unavailable |

Pagination: backend `number` is 0-based; current UI hard-codes page 1 and does not expose totals. Target `ADAPT_FRONTEND` in service adapter only.

### AC3 — Document Detail (`AccountantDocumentDetail.tsx`)

Core calls in parallel: GET document, `/ocr`, `/classification`, `/invoice`. Current sections: document/file metadata, preview/download, invoice edit form, OCR raw text/confidence, classification edit/approve/correct. Sample adds tabs for document/invoice/seller/buyer/items/OCR/extracted fields/AI/history/comments/related and workflow actions.

| Section | API | Failure policy | Target |
|---|---|---|---|
| File/document metadata | document detail | CORE_REQUIRED | show filename/mime/size/type/status; never show filePath |
| Preview/download | blob endpoints | CORE_REQUIRED action | CONNECT_EXISTING_API |
| Invoice, seller/buyer/items/totals | document invoice | OPTIONAL_SECTION, EMPTY_ALLOWED | read/edit only per role policy |
| OCR raw text/confidence | document OCR | OPTIONAL_SECTION, ERROR_ISOLATED | show independent empty/error |
| AI classification | classification | OPTIONAL_SECTION, EMPTY_ALLOWED | existing controls only for accountant/admin |
| extracted fields/versions/log history/reviews/comments/duplicates | not individually exposed in frontend services | BACKEND_MISSING/PARTIAL_API | unavailable; no fabricated timeline |
| approve/reject/request-info/archive/void | no controller endpoint | BACKEND_MISSING | hide/disabled unavailable |

Current Promise-all makes any optional failure fail entire detail; target must isolate secondary calls while keeping document as core. This is an implementation requirement, not an API request.

### AC4 — Upload (`AccountantUpload.tsx`)

Real flow: file picker, upload via `FormData` field `file`, then optional `processDocument`; displays response/processing errors. Sample asks drag/drop, accepted PDF/JPG/PNG, 10MiB validation, per-file preview/metadata/OCR correction/submit/draft/retry. Existing API has upload/process only; server/source AI supports JPEG/PNG and returns explicit PDF-pending error. Target: keep one-file real upload/process, present loading/error/result; client validation/drag/drop/preview is `ADAPT_FRONTEND`; draft/metadata/submit/retry are `BACKEND_MISSING` unless merely re-run existing process.

### AC5 — Financial Transactions (`FinancialTransactions.tsx`)

Real: income/expense/balance derived cards; create/edit form (type, amount, date, category, documentId, invoiceId, payment method, description); filters type/date/category; table date/type/category/description-link/amount/edit/delete. APIs GET/POST/PUT/DELETE `/financial-transactions`, GET categories. Sample financial/reconciliation screen differs: bank statement semantics are absent. Target: KEEP real transactions; category/summary `DERIVE_FROM_EXISTING_DATA`; reconciliation must be a separately labelled advanced document feature.

### AC6 — AI Test (`AiTest.tsx`), Classification/Storage/Reports placeholders

AI Test uses existing multipart `/api/v1/ai/test`; target is API_EXISTS but `RUNTIME_REQUIRED` and must not claim processing success. Classification route is placeholder although document classification APIs exist: `MISSING_UI + PARTIAL_API`. Storage and reports are `PLACEHOLDER`/`BACKEND_MISSING`.

## 5. Employee Pages

### E1 — Dashboard (`EmployeeDashboard.tsx`)

Real implementation mirrors admin cards/recent table and calls statistics/documents; source backend scopes employee results to owned documents. Sample asks own totals/status/spending/latest documents. Target: retain real own document KPIs/recent list; financial spending and per-month chart `BACKEND_MISSING`; “view all” must navigate employee list (current visual button has no handler).

### E2 — My Documents (`/employee/documents` reuses `AccountantDocuments.tsx`)

Sample asks own-document table, status tabs, timeline/resubmit. API backend scope establishes ownership; frontend must not pass company/uploader IDs. Reusable: table, status badge, core list/search/date/mime filters, view. Missing: timeline/resubmit workflow API, progress. Accountant-only action controls must not appear. Target: read-focused owner list with upload CTA only where role allows.

### E3 — Detail (`/employee/documents/:id` reuses `AccountantDocumentDetail.tsx`)

Visible/read-only: file metadata, preview/download, document/invoice/OCR/classification results if server authorizes. Hidden: invoice edit, classification edit/approve/correct, review/accounting actions. Backend workflow submit/resubmit/comments unavailable. Target is a dedicated read-focused presentation over same calls, not wholesale accountant reuse.

### E4 — Upload (`/employee/upload` reuses AccountantUpload)

Existing upload API is authorized through document create permission and assigns current uploader. Same file/process contract as AC4. Employee metadata/draft/submit/resubmit sample behaviors are `BACKEND_MISSING`; no arbitrary user/company input.

## 6. Advanced Pages

| Unit | Sample requirement | Existing API | Status | Target |
|---|---|---|---|---|
| ADV1 OCR/AI result | OCR fields/confidence, processing feedback | `/ocr`, `/process`, `/reprocess`, AI test | PARTIAL_API/RUNTIME_REQUIRED | detail/upload extension |
| ADV2 Classification | candidate, correction, approve/review | document classification APIs | API_EXISTS, screen missing | accountant/admin UI only |
| ADV3 Reconciliation | bank row match/unmatch | document reconciliation controller exists; no frontend service/page | PARTIAL_API | document reconciliation UI, not bank statement |

## 7. Missing Backend Pages

Supplier, notifications, comments, tags, audit list, reports/export, admin settings, storage administration, backup/restore, classification rules, bank statements and global semantic search are `SAMPLE_UI_ONLY / NEEDS_DECISION` unless project scope later confirms them. They must be distinct placeholders, never “successful” mock persistence.

## 8. Field Mapping

| UI field | Current frontend | Existing API field | Source | Status | Action |
|---|---|---|---|---|---|
| File name/type/size/date | yes/partly | originalFileName/fileType/fileSize/createdAt | document | AVAILABLE | map presentation |
| processing/review | status label only | status + reviewStatus | document | DERIVED | StatusBadge mapper |
| supplier/total/items | detail only | invoice sellerName/totalAmount/items | invoice | AVAILABLE_DETAIL_ONLY | compose detail |
| OCR text/confidence | detail | rawText/confidence | OCR | AVAILABLE_DETAIL_ONLY | optional section |
| AI category/confidence | detail | category/confidence/status | classification | AVAILABLE_DETAIL_ONLY | optional section |
| object key | n/a | filePath | document | INTERNAL | do not display |
| uploader/company/type IDs | type IDs only | uploadedById/companyId/typeId | document | PARTIAL | do not infer names |

## 9. Table Mapping

Document list mapping is AC2; User table mapping is A2; Financial table mapping is AC5. Dashboard recent tables use document-list columns only. No table may add supplier, amount, confidence, invoice number, tags or uploader names until API returns a batched/list-level source. Static SystemStatistics tables are `REMOVE_FROM_TARGET`.

## 10. Action Mapping

| Action | API exists | Target |
|---|---|---|
| document view/preview/download/upload/process | yes | CONNECT_EXISTING_API |
| invoice update/classification update/correct/approve | yes | accountant/admin only; presentation gate |
| transaction create/update/delete, user create/status/roles, role create/permission update | yes | KEEP_BEHAVIOR/RESTYLE |
| workflow submit/resubmit/review decision/reject/request-info/archive/void | no verified controller | BACKEND_MISSING |
| comments/notifications/supplier/settings/backup | no | BACKEND_MISSING/PLACEHOLDER |

## 11. Filter Mapping

| Filter | Sample | Current UI | Backend support | Target |
|---|---|---|---|---|
| filename search | yes | yes | `search` | KEEP |
| processing status/date | yes | yes | parameters exist | KEEP |
| review status/type/uploader/company | yes | absent | parameters exist | ADAPT_FRONTEND after policy/UI approval |
| mime type | no exact sample | client-only | no needed | FRONTEND_ONLY |
| category/amount/supplier/tags/global fields | yes | absent | not list-supported | BACKEND_MISSING |
| financial type/date/category | yes | yes | exists | KEEP |

## 12. Dashboard Data Mapping

Admin/accountant/employee all have real base statistics. Accountant also has real financial aggregate and expense categories. All sample monthly/type/department/VAT/storage/audit chart series are `BACKEND_MISSING`; do not use static SystemStatistics data. Employee document ownership is source-backed; accountant all-company security finding remains unresolved and must not be hidden by UI.

## 13. Document Detail Mapping

Canonical call plan: core GET `/documents/{id}`; optional parallel GET `/invoice`, `/ocr`, `/classification`; blob preview/download on demand. Invoice items arrive with Invoice response. No version/history/extracted-field-specific frontend endpoint was found. Core failure blocks page; optional failures isolate to their section; `404` auxiliary data becomes empty allowed where business-valid.

## 14. Upload Mapping

`uploadDocument(file)` posts the exact multipart field `file`; `processDocument(id, reprocess)` selects `/process` or `/reprocess`; `downloadDocument` returns a Blob URL. No request accepts sample metadata, draft state, multiple files, progress or file replacement. UI must communicate this rather than emulate persistence.

## 15. Role-specific Action Matrix

| Action | Admin | Accountant | Employee | API | Target |
|---|---:|---:|---:|---|---|
| view/preview/download own/scoped doc | yes | yes | yes | document | keep backend scope |
| upload | yes | yes if permission | EMPLOYEE only route | upload | retain guards |
| invoice/classification edit | yes | yes | no UI target | existing | hide employee |
| financial transaction | yes policy | yes | no route | existing | accountant/admin |
| approval/reject/resubmit | no exposed API | no exposed API | no exposed API | none | unavailable |

## 16. API Coverage Matrix

| Page | UI requirement | Existing API | Coverage |
|---|---|---|---|
| A1 Admin Dashboard | base KPI/recent docs | statistics/documents | PARTIAL |
| A2 Users | create/roles/status | users/roles | PARTIAL |
| A3 Roles | RBAC | roles/permissions | FULL |
| A4 Admin documents/upload | core document flow | document | PARTIAL |
| A5 System statistics | charts/people/departments | none | NONE |
| A6 Categories/types/settings | master/settings | read lists / no settings | PARTIAL |
| AC1 Dashboard | KPI/financial/recent | stats/financial/docs | PARTIAL |
| AC2 Documents | list/filter/detail | documents | PARTIAL |
| AC3 Detail | document/invoice/OCR/classification | four APIs | PARTIAL |
| AC4 Upload | one file/process | upload/process | PARTIAL |
| AC5 Financial | transactions/categories | full CRUD | FULL |
| AC6 Advanced placeholders | AI/classify/reports/storage | partial | PARTIAL |
| E1 Dashboard | own KPI/recent | scoped stats/docs | PARTIAL |
| E2 Documents | own list | scoped documents | PARTIAL |
| E3 Detail | read detail | scoped detail subresources | PARTIAL |
| E4 Upload | upload/process | upload/process | PARTIAL |

## 17. Page Readiness Matrix

| Unit | UI mapped | API mapped | Missing backend | Ready |
|---|---|---|---|---|
| A1,A2,A3,A4 | yes | yes | partial | PARTIAL |
| A5 | yes | no | yes | BLOCKED |
| A6 | yes | partial | settings CRUD | PARTIAL |
| AC1–AC4 | yes | yes | workflow/list fields | PARTIAL |
| AC5 | yes | yes | none for current scope | YES |
| AC6/ADV1–3 | yes | partial | screen/runtime | PARTIAL |
| E1–E4 | yes | yes | employee workflow/action split | PARTIAL |

## 18. Implementation Units

P0: A3 Roles/Permissions; AC5 Financial (independent FULL APIs). P1: A1 Dashboard, A2 Users, AC2 Documents, AC3 Detail, AC4 Upload, E1–E4 action-safe views. P2: ADV1–3. P3: A5/A6 settings and all missing-backend placeholders. Shared UI F0 is a prerequisite; build environment must first be repaired before any unit.

## 19. File Impact Per Unit

| Unit | Existing files likely modified | Reuse | APIs | Backend/DB | Risks |
|---|---|---|---|---|---|
| A1 | AdminDashboard | F0 states/cards/table | stats/docs | none | mock chart leakage |
| A2 | UserManagement | F0 table/modal/states | users/roles | none | DTO shape/role policy |
| A3 | RoleManagement | F0 states/cards | roles/permissions | none | preserve grouped RBAC |
| AC2 | AccountantDocuments, document adapter | F0 table/filter/badge | docs | none | pagination/list-field limits |
| AC3 | AccountantDocumentDetail | F0 states/badge | four endpoints | none | partial failure/action gates |
| AC4 | AccountantUpload | F0 feedback | upload/process | none | PDF/process limitation |
| AC5 | FinancialTransactions | F0 table/filter | transactions/categories | none | company security scope |
| E1–E4 | EmployeeDashboard/shared accountant pages or employee wrappers | F0 components | existing scoped APIs | none | prevent accountant actions |
| ADV1–3 | AiTest/new approved UI only | F0 states | AI/classify/reconcile | none | runtime/provider/reconciliation semantics |

## 20. Open Questions / Backend Missing

1. Is accountant all-company invoice/statistics access intentional? Existing security gap requires decision before visualizing broad data.
2. Which presentation label corresponds to `UPLOADED` and `PROCESSED/PENDING`?
3. Are suppliers, reports, notifications, audit, settings and bank statements confirmed project scope rather than handover mock UI?
4. Is a batched document-list read model approved later for supplier/amount/confidence, instead of N+1 calls?
5. Build must be unblocked (`Tailwind oxide` Windows EPERM) before UI implementation validation.

Counts: ADMIN_PAGES=6; ACCOUNTANT_PAGES=6; EMPLOYEE_PAGES=4; ADVANCED_PAGES=3; FULL_API_COVERAGE=2; PARTIAL_API_COVERAGE=13; NO_API_COVERAGE=1; READY_UNITS=1; PARTIAL_UNITS=6; BLOCKED_UNITS=1.

`DETAILED_PAGE_MAPPING_READY = YES`
