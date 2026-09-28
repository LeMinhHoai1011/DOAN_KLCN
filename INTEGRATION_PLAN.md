# Integration Plan — frontend thật ↔ backend thật

## 1. Executive Summary

Audit nguồn ngày 28/09/2026, chỉ dựa trên source; không chạy ứng dụng, PostgreSQL, MinIO hay Ollama. Frontend thật là React/Vite TypeScript tại `quan tri`; backend là Spring Boot/PostgreSQL/MinIO tại `backendqt/backendqt`. Frontend mẫu trong `FRONTEND_HANDOVER.md` là yêu cầu UI/mock, không phải contract backend.

Kết luận: lõi xác thực, người dùng, tài liệu, upload MinIO, phân loại, OCR-result persistence, AI processing, đối soát, giao dịch tài chính, dashboard và RBAC đã tồn tại ở các mức khác nhau. Không nên tạo “v2” cho các module này. Khoảng cách chính là contract DTO/UI, workflow review, admin settings/audit/notification/report, và màn hình employee chuyên biệt.

Phân loại requirement đã audit: **KEEP 10; ADAPT 14; MISSING 13; CONFLICT 6; MOCK_ONLY 9; RUNTIME_CHECK 8**. Các số này đếm theo module/capability trong bảng §30, không phải số endpoint.

`DATABASE_RUNTIME_VERIFICATION_REQUIRED`: không có schema dump/Flyway baseline đầy đủ hoặc kết nối read-only PostgreSQL được xác nhận. `application.yml` dùng PostgreSQL và `ddl-auto: update`; tài liệu `DATABASE_NOTES.md` nói H2 mặc định, nên tài liệu và runtime config đang không đồng nhất.

## 2. Current Project Architecture

| Layer | Hiện trạng liên quan tích hợp |
|---|---|
| Frontend | React + Vite + TypeScript, `react-router-dom`, Axios; localStorage `token`/`user`; state local component/service, không Redux/Zustand. |
| Router/layout | `App.tsx`: protected route và role guard; `AdminLayout`, `AccountantLayout`, `EmployeeLayout`. Routes đã có dashboard, document, upload, users, roles, financial transactions, AI test. Một số route là `UnavailableFeature`. |
| Frontend services | `api.ts` thêm Bearer token, xử lý 401; `authService.ts` dùng login/register/me/password. Có document/invoice/dashboard/classification/financial services và `mockData.*`; phải rà từng service trước khi thay mock. |
| Backend | Spring Boot REST, JPA, Spring Security method security, JWT, PostgreSQL config, MinIO client, AI provider abstraction/Ollama/external provider. |
| Persistence | Entity/repository/service/controller cho documents, users/RBAC, invoices, OCR, classifications, reconciliation, accounting/financial transactions, dashboard. |
| Exceptions/config | `GlobalExceptionHandler`; `SecurityConfig`, `CorsConfig`, `MinioConfig`, `AiConfiguration`, `DemoAdminInitializer`, role-permission initializer. |

## 3. Frontend Handover Requirements

Handover yêu cầu ba vai trò `admin/accountant/staff`, màn hình dashboard, document detail/workflow, upload wizard, OCR/AI, classify/reconciliation, users/permissions/master/settings/storage/audit, notifications và reports. Toàn bộ dữ liệu của mẫu là mock/local memory; endpoint ở handover chỉ là đề xuất. UI hiện tại không cùng cây file với mẫu, vì vậy phải giữ các layout/page hiện hữu tối đa và kết nối/điều chỉnh từng screen thay vì copy mẫu.

## 4. Existing Backend Inventory

| Nhóm | Thành phần có sẵn |
|---|---|
| Auth/user/RBAC | `AuthController`, `UserController`, `RoleController`, `PermissionController`; `AuthService`, `UserService`, `RoleService`; JWT filter/service, `CustomUserDetailsService`. |
| Documents/storage | `DocumentController/Service/Repository`, `Document`, `DocumentVersion`, `DocumentType`; `StorageService`; MinIO upload/download/preview. |
| Invoice/OCR/AI | `InvoiceController/Service`; OCR result service/repository; `DocumentAiProcessingService`, `AiProcessingService`, Ollama/external providers, parser/validator, processing log/job entities. |
| Review/classification | `ClassificationController/Service`, corrections; `ReviewWorkflowService`, `DocumentReview`, field corrections. No review-workflow controller found. |
| Accounting | `AccountingCategoryController`, `FinancialTransactionController`, accounting/reconciliation services and repositories. No `AccountingEntryController` found. |
| Dashboards | `/api/v1/dashboard/statistics`, `/financial`; dashboard service. |
| Missing controller domains | Supplier, notification, audit log, report/export, system/AI-OCR settings, storage administration, company update, bank-statement resource. |

## 5. Existing Database Model

Source expects tables including `companies`, `users`, `roles`, `user_roles`/role-permission mappings, `permissions`, `permission_groups`, `documents`, `document_versions`, `document_types`, `invoice_data`, `invoice_items`, `ocr_results`, `extracted_fields`, `field_corrections`, `ai_classifications`, `classification_correction`, `document_reviews`, `accounting_categories`, `accounting_entries`, `financial_transactions`, `reconciliation_batches`, `reconciliations`, `processing_logs`, `processing_jobs`, `processing_attempts`, and `audit_logs`.

Document is the central relation: `documents.company_id`, `uploaded_by`, processing `status`, `review_status`, file metadata/object key; it has invoice, versions, OCR results, classifications, reviews, logs and entries. Invoice owns fields/items. Category is company-scoped. User belongs to company and uses a role-assignment model in current service code.

Source evidence conflicts with `ENTITY_COLUMNS.md` in places (for example document references `type` and user service reads `getUserRoles()` while the document describes older singular role fields). Treat Java annotations/source as authority; confirm generated PostgreSQL schema at runtime. Migration folder contains only `20260926_02_allow_normalized_user_inserts.sql`, not a complete schema history.

## 6. API Inventory

| Method/current URL | Controller | Request/response | Security | Status |
|---|---|---|---|---|
| POST `/api/v1/auth/register`, `/login` | Auth | Register/Login → User/LoginResponse | public | WORKING_FROM_CODE |
| GET/PUT `/api/v1/users/me`, password; admin GET/POST/PUT users and roles | User/Role/Permission | user/role/permission DTOs | method `ADMIN` or authenticated | WORKING_FROM_CODE |
| GET `/api/v1/companies`, categories, document types | Company/Category/Type | list DTOs | admin/permission/authenticated | WORKING_FROM_CODE |
| POST/GET/PUT/DELETE `/api/v1/documents`; upload, versions, process/reprocess, download/preview, OCR, invoice | Document | document/OCR/invoice DTOs, multipart `file` for upload | permission + service scope | PARTIAL |
| CRUD `/api/v1/invoices` | Invoice | invoice DTO | role based | WORKING_FROM_CODE; ownership needs review |
| GET/PUT/approve/review/correction `/documents/{id}/classification` | Classification | classification DTO | role based | PARTIAL |
| GET/POST/decision `/documents/{id}/reconciliations` | Reconciliation | reconciliation DTO | ADMIN/ACCOUNTANT | WORKING_FROM_CODE |
| GET/POST/PUT/DELETE `/financial-transactions` | FinancialTransaction | paged transaction DTO | accounting permissions | PARTIAL |
| GET `/dashboard/statistics`, `/dashboard/financial` | Dashboard | dashboard DTO | roles | PARTIAL |
| GET connectivity, POST multipart AI test `/api/v1/ai/*` | AiTest | provider response | admin/all roles | RUNTIME_CHECK |

No source endpoint was labelled runtime-working: all runtime behavior remains `UNKNOWN_RUNTIME` absent safe execution.

## 7. Frontend ↔ Backend Mapping

| Frontend requirement | Current implementation | Database | Status | Action |
|---|---|---|---|---|
| Login + token + `/me` | Existing auth services and backend endpoints | users/roles | KEEP | Preserve Bearer design; add UI adapter only. |
| Users/roles/permissions | Existing admin pages and APIs, but URLs/data differ from handover | users/roles/permissions | ADAPT | Map handover admin UI to current `/users`, `/roles`, `/permissions`. |
| Document browse/detail | Existing endpoints and screens, DTO lacks invoice/OCR/history display fields | documents + related | ADAPT | Compose detail via current subresources first. |
| Search/filter/page | Document `findPage` supports limited filters/page | documents | ADAPT | Normalize Spring page (0-based) to UI; extend only verified query gaps. |
| Upload/file preview | `/documents/upload`, versions/download/preview exist | documents/versions + MinIO | ADAPT | Use existing multipart field and server result; validate contract/mime/size. |
| OCR/AI process | AI endpoints/services and OCR result endpoints exist | ocr/results/classifications/logs | ADAPT | Expose processing state/detail DTO; do not install an engine. |
| Review approve/reject/resubmit | Service exists but no workflow controller endpoint found | reviews/status fields | MISSING | Add APIs only after agreeing canonical state adapter. |
| Dashboard | generic statistics/financial APIs | aggregate documents/financial | ADAPT | Add role-scoped projections, not client aggregation. |
| Financial transactions/reconciliation | Existing backend & accountant screens | financial/reconciliations | ADAPT | Map handover bank statement model to reconciliation model. |
| Invoice/items/accounting | invoice CRUD/entities and entries exist | invoice_data/items/entries | ADAPT | Surface in document detail; employee authorization must be proven. |
| Supplier/master rules | no supplier/rule source model | none verified | MISSING | Confirm scope; do not create by UI assumption. |
| Notifications/audit | audit entity only; no exposed workflow/source endpoint | audit_logs only partially evidenced | MISSING | Define event and retention contract. |
| Reports/export | financial dashboard only | existing aggregates | MISSING | Start with read-only report projections. |
| Admin storage/settings/backup | MinIO config only | config/MinIO | MOCK_ONLY | Do not expose secrets/backup until approved architecture. |

## 8. Frontend ↔ Database Mapping

| Frontend field | DTO field | Entity field | Database column | Status | Note |
|---|---|---|---|---|---|
| `id,fileName,fileSize,type` | DocumentResponse partial | document file/type fields | documents | RENAMED | `originalFileName`, `fileSize`, `documentType/typeId`; adapter required. |
| `status` | `status`,`reviewStatus` | `Document.status`,`reviewStatus` | processing_status/review_status | CONFLICT | two dimensions, do not add a synthetic column. |
| upload file URL | `filePath` only | `filePath` | object_key | CONFLICT | object key must not be exposed as public URL. |
| invoice number/date/partner/tax/totals | InvoiceResponse | Invoice | invoice_data | DERIVED | detail needs invoice fetch/composed response. |
| OCR fields/confidences | OCRResultResponse partial | OCRResult; ExtractedField | ocr_results/extracted_fields | ADAPT | extracted fields belong to Invoice. |
| AI suggestion/correction | ClassificationResponse | Classification/Correction | ai_classifications/correction | ADAPT | category/name shape must be mapped. |
| uploader/approver labels/history/comments/tags/flags | not in DocumentResponse | mixed/no entity verified | mixed | MISSING | only uploader ID currently explicit; no comment/tag model found. |
| company setting/supplier/bank statement/notification | none | none verified | none verified | MISSING | database change only after product decision. |

## 9. Entity ↔ Database Mapping

Entity-to-table mapping is derived from Java annotations and `ENTITY_COLUMNS.md`; actual database is **NOT_VERIFIED**. PKs are identity `Long`; documented FK links include company/user/document/invoice/category/review/classification. Notable constraints: `companies.taxCode`, `users.username/email`, `roles.roleName`, and `invoice_data.document_id` are intended unique; invoice items and extracted fields reference invoice; document versions/OCR/classifications/reviews/logs reference document.

Verification tasks before migrations: inspect `information_schema` read-only; compare table/column names, enum storage and nullability to compiled entities; identify duplicate role mapping (`roleRef`/`role` versus `user_roles` usage); confirm `Document.type` association and document type strings. `DATABASE_RUNTIME_VERIFICATION_REQUIRED`.

## 10. Enum/Status Mapping

| Frontend value | Backend enum/database value | Status | Proposed mapping |
|---|---|---|---|
| `draft/submitted/under_review/.../void` | DocumentStatus `UPLOADED,PROCESSING,PROCESSED,NEED_REVIEW,COMPLETED,FAILED` + ReviewStatus `PENDING,APPROVED,REJECTED,CORRECTED` | CONFLICT | DTO presentation state derived from both; retain underlying enums. |
| `admin/accountant/staff` | role code usage `ADMIN,ACCOUNTANT,EMPLOYEE,USER`; older doc says `UserRole ADMIN,USER` | CONFLICT | canonical API roles from role assignments; frontend maps STAFF→EMPLOYEE only. |
| OCR `completed/failed` | OCRResult.status String | ADAPT | document allowed values and failure semantics. |
| AI accepted/corrected | ClassificationStatus values incl. PENDING/ACCEPTED/NEED_REVIEW/CORRECTED | ADAPT | map directly after UX labels agreed. |
| bank matched/unmatched | reconciliation decision/status | ADAPT | use reconciliation, not unverified bank table. |

## 11. Authentication Analysis

KEEP: login returns token, frontend stores it, Axios emits Bearer, app calls `/users/me`; backend is stateless JWT and passwords use BCrypt. ADAPT: no logout endpoint exists; frontend logout must remain client token removal until revocation is a defined requirement. Registration is public although handover has demo chooser; preserve backend credentials flow. Never accept role from localStorage as authority; route guards are UX only.

## 12. Authorization Analysis

Method security exists. `DocumentService.load` scopes admin globally, accountant to own company, employee/user to own uploads; download/preview call `load`, a positive IDOR defense. `findPage` also scopes specification. Risks: `findAll` grants ADMIN/ACCOUNTANT all documents (admin cross-company intent needs product confirmation); Invoice/Classification service methods must be traced at implementation time because controller roles alone do not prove ownership; document version mutation calls `load` but authorization permission is not ownership-specific in controller (service mitigates). Employee document re-use of accountant pages must not grant accountant-only write actions. Runtime authorization tests are required.

## 13. Company Scope Analysis

| Domain | Finding |
|---|---|
| Documents/files | SAFE_FROM_CODE for detail/page: company key prefix and service scope; global admin is intentional/needs policy confirmation. |
| Users/categories | PARTIAL: entities contain company links; verify service filters and admin cross-company policy. |
| Financial/reconciliation/invoices | RUNTIME_CHECK: controller security shown, service-level company filtering not established in this audit. |
| Reports/dashboard | PARTIAL: role protected, scope calculation must be verified. |

## 14. Employee Ownership Analysis

Documents: **OWN DOCUMENT** in `DocumentService` for employee/user list, detail, file, update/delete through `load`. Upload assigns current user/company. Comments and notifications have no verified backend implementation. Invoice/classification endpoints permit employee reads by role, but their service ownership proof is not documented here: `RUNTIME_VERIFICATION_REQUIRED` before exposing employee routes.

## 15. Document Workflow Mapping

| Step | Frontend expectation | Backend implementation | DB support | Status | Required change |
|---|---|---|---|---|---|
| Upload | file + metadata | upload endpoint stores MinIO/document/version | yes | ADAPT | metadata contract differs. |
| Processing/OCR/AI | asynchronous visible state | process/reprocess and AI services | logs/jobs/results | PARTIAL | define polling/result contract. |
| Submit/re-submit | staff action | no controller endpoint found | status/review fields | MISSING | workflow API. |
| Start review/approve/reject/request-info | accountant workflow | ReviewWorkflowService, no controller | reviews/corrections | MISSING | expose service after enum policy. |
| Archive/void/delete | UI actions | delete only; no archive/void API | no verified reason/audit | MISSING | decide retention/audit semantics. |

## 16. Upload/MinIO Analysis

Current path: `POST /documents/upload` multipart → `DocumentService.createFromUpload` → company-prefixed opaque key → MinIO bucket → document + first `DocumentVersion`; download/preview stream through the backend. Version endpoint exists. Current controller needs contract verification for multipart field name; service only rejects empty files and does not prove 10 MiB/PDF-image whitelist or malware scanning. Bucket config default differs (`invoices` config versus service fallback `invoice-files`). Do not expose `filePath` in UI DTO; keep authorization through stream endpoints. `RUNTIME_VERIFICATION_REQUIRED` for MinIO connectivity/bucket policy/content handling.

## 17. OCR Analysis

OCR result entity/service/endpoint and processing job/log entities exist. An independent real OCR engine is not evidenced; AI document processing includes image/PDF preprocessing and provider processing. Classification/OCR persistence is **PARTIAL**, engine mode **RUNTIME_CHECK**. The handover fake OCR simulator must be replaced incrementally with server result/polling, retaining a clear `PROCESSING`/failure UI.

## 18. AI Classification Analysis

AI abstraction supports Ollama and external provider; config defaults to Ollama at a LAN URL/model, with test/connectivity endpoints. Parser/validator and persistence service exist. This is **PARTIAL/REAL-CANDIDATE**, not confirmed real until provider, model, MinIO input and persistence succeed. Do not call or download models during integration planning.

## 19. Admin Analysis

KEEP/ADAPT: users, roles, permissions, companies list, accounting categories, document types, dashboard stats. MISSING: supplier CRUD, classification rules, company settings update, audit-log API, settings API, storage metrics/retention/backup, AI/OCR configuration persistence. AI connectivity read check exists but does not equal admin configuration. Sensitive storage/provider credentials must never be returned to browser.

## 20. Accountant Analysis

KEEP/ADAPT: documents, detail subresources, upload, financial transactions, classification, reconciliation, dashboard financial data. MISSING: approval queue/transition endpoints, report projection/export, document flags/duplicate queue, a dedicated classification/reconciliation UI adapter. Accounting entry entity exists but not public API. Scope and write authorization require runtime tests.

## 21. Employee Analysis

KEEP: auth routing and employee dashboard shell. ADAPT: documents/detail/upload are re-used accountant components, so fields/actions need owner-safe UI and backend contracts. MISSING: dedicated My Documents filters/progress/timeline, draft/submit/resubmit, comments, notifications. Remove demo/local state only screen-by-screen after each real API is accepted.

## 22. Search/Filter/Pagination

`DocumentService.findPage` supports date range, company, processing/review status, type ID, uploader ID, filename search, Spring pageable; it does not evidence search in invoice number/supplier/tax/tags, amount ranges, flag filters, multi-sort or global search. Existing frontend uses local/mocked patterns in places. Phase implementation should first adapt to supported query fields and page metadata, then add safe indexed server-side filters only where product requires them. UI page 1 versus Spring default page 0 must be normalized centrally.

## 23. Mock → Real API Mapping

| Current mock/local behavior | Required real API | Existing backend | Action |
|---|---|---|---|
| Demo user selector / local auth | login/me | yes | replace with current auth service (already partly done). |
| mock documents/dashboard | paged docs/statistics/financial | partial | adapt DTO and server pagination. |
| simulated OCR/classification | process/OCR/classification | partial | show server processing result. |
| local submit/approve/reject | workflow actions | service only | add contract/controller after conflict resolution. |
| local categories/rules/suppliers | master APIs | categories/types partial | retain mock only for missing approved domains. |
| local notifications/audit/settings/storage | read/write APIs | missing except audit entity | do not fake completed persistence. |
| local report/export | report/export | missing | build read API first. |

## 24. Conflict Register

| ID | Severity | Frontend | Backend | Database | Risk | Recommendation |
|---|---|---|---|---|---|---|
| C001 | BLOCKER | one document `status` | processing + review statuses | two columns | invalid workflow/data loss | presentation DTO adapter. |
| C002 | HIGH | `staff` | `EMPLOYEE`/`USER` + role assignments; stale enum doc | role mappings | unauthorized/misrouted UI | canonical role contract. |
| C003 | HIGH | document semantic fields inline | file-centric DocumentResponse + Invoice | documents/invoice_data | destructive UI update | composed detail DTO/read model. |
| C004 | HIGH | public file URL | `filePath` object key | object_key | storage disclosure/IDOR | download/preview endpoint only. |
| C005 | MEDIUM | bank statements | reconciliations by document | no bank table verified | duplicate domain | adapt Classify UI to Reconciliation. |
| C006 | MEDIUM | admin config/backup UI | environment config only | no settings schema | secret/ops exposure | separate approved admin architecture. |

## 25. Proposed Database Changes

None approved. Potential registers, only if adapters/projections cannot solve them:

| Change ID | Reason/current structure | Required data/possible solution | Affected | Risk |
|---|---|---|---|---|
| DB-01 | Comments/tags/notifications absent from verified model | first use DTO/event projection; only then new tables | document/user API | medium, ownership/audit. |
| DB-02 | Supplier/rule/bank statement model absent | confirm product domain; reuse category/reconciliation where possible | admin/accountant | high, duplicate concepts. |
| DB-03 | Archive/void/reason/history not verified | derive from reviews/logs or add auditable workflow model only after C001 | document workflow | high. |

## 26. Proposed API Changes

| API | Current | Required | Action | Breaking? | Affected frontend |
|---|---|---|---|---|---|
| Documents list/detail | core endpoints, sparse DTO | scoped composed read DTO and documented page envelope | EXTEND/read adapter | no | all document screens |
| Upload | `/documents/upload` | documented multipart name/validation/metadata response | ADAPT | no | upload |
| Workflow | no exposed controller | submit/review/decision endpoints | CREATE after C001 | no existing caller | approval/staff |
| Dashboard | generic stats/financial | role projections | EXTEND | no | dashboards/reports |
| Classification/reconciliation | exists by document | UI-specific queries/decision mapping | ADAPT | no | classify |
| Notifications/audit/reports | none | scoped paged reads then controlled writes | CREATE only if approved | no | staff/admin/accountant |

## 27. UI Reuse Analysis

| UI/page group | Classification | Reason |
|---|---|---|
| Existing layouts/login/register/password | KEEP_UI | authentication architecture already matches backend. |
| Dashboard/pages document tables/details | CONNECT_API + ADAPT_DATA | screens exist but backend DTOs are leaner/different. |
| Accountant upload/financial/AI test | CONNECT_API | mapped backend capabilities exist. |
| Employee re-used accountant documents/detail/upload | REWORK_REQUIRED | must avoid accountant actions and show owner workflow. |
| Placeholder settings/storage/classification/reports | REMOVE_CANDIDATE until APIs approved | displaying unavailable features is acceptable; do not fake persistence. |
| Handover admin suppliers/audit/storage/settings and staff notifications | ADAPT_DATA/MOCK_ONLY | no matching verified backend domain. |

## 28. Implementation Phases

0. Resolve C001/C002 role/status and canonical document read contract.
1. Validate auth/session and backend role claims; retain existing login/me integration.
2. Verify authorization/company/owner service behavior with non-mutating tests.
3. Connect paged documents/detail/invoice/OCR with DTO adapters.
4. Connect upload/versions/download/preview; validate MinIO behavior.
5. Expose/process OCR-AI result lifecycle after runtime checks.
6. Expose review workflow and employee submit/resubmit only after state decision.
7. Connect classification/reconciliation/financial transactions and accountant dashboard.
8. Add role-specific dashboards/reports projections.
9. Decide and implement genuinely missing admin/staff domains (notifications/audit/settings/etc.) only after product/data approval.

## 29. File Impact Per Phase

| Phase | Existing files to modify | Reuse unchanged | Potential new files / DB / API | Risks |
|---|---|---|---|---|
| 0 | document DTO/service, frontend document adapters | entities/enums | read-model mapper; no DB | C001 data semantics |
| 1–2 | `authService.ts`, guards, Security/User services/tests | JWT config | authorization integration tests; no DB | role claim mismatch/IDOR |
| 3 | document service/pages, `DocumentController`/DTO | repository scope | adapter/query DTO | pagination/status mismatch |
| 4 | upload UI/service/controller validation | MinIO config/service | upload metadata DTO | MinIO/runtime policy |
| 5–6 | document detail/workflow UI; AI/OCR/review services/controllers | processing entities | workflow controller/DTO; DB only DB-03 if necessary | external AI and state transition |
| 7–8 | accountant/dashboard services/pages | financial/reconciliation services | report projection endpoints | company leakage |
| 9 | admin/staff pages/services | existing RBAC | domain APIs/entities only if approved (DB-01/02) | scope creep/secrets |

## 30. Implementation Matrix

| Module | Frontend | Backend | Database | Security | Status | Priority | Phase |
|---|---|---|---|---|---|---|---|
| Auth | existing | existing | users | JWT | KEEP | P0 | 1 |
| Users/Roles/Permissions | existing | existing | RBAC tables | admin | ADAPT | P0 | 1 |
| Company scope | partial | Document proven | company_id | scoped service | RUNTIME_CHECK | P0 | 2 |
| Documents/detail | existing | existing partial DTO | documents/invoice | scoped | ADAPT | P0 | 3 |
| Search/filter/page | partial/local | partial | documents | scoped | ADAPT | P0 | 3 |
| Upload/MinIO/version | existing | existing | docs/versions | scoped | ADAPT | P0 | 4 |
| OCR | mock/partial | partial | OCR/fields | scoped | RUNTIME_CHECK | P1 | 5 |
| AI classification | partial | partial | classifications | accountant/admin write | RUNTIME_CHECK | P1 | 5 |
| Review/workflow | mock | service only | reviews | role+owner | MISSING | P0 | 6 |
| Accounting/financial | existing | existing partial | transactions/entries | permission | ADAPT | P1 | 7 |
| Reconciliation | partial | existing | reconciliations | accountant/admin | ADAPT | P1 | 7 |
| Dashboards/reports | existing/placeholder | partial/missing | aggregates | scope | ADAPT/MISSING | P1 | 8 |
| Notifications/audit | mock | missing/entity-only | unverified/audit | owner/admin | MISSING | P2 | 9 |
| Admin settings/storage | placeholder/mock | missing/config-only | unverified | admin/secrets | MOCK_ONLY | P2 | 9 |

## 31. Blockers Before Implementation

1. Decide canonical document workflow mapping (C001) without altering the database by default.
2. Confirm canonical role source and STAFF→EMPLOYEE mapping (C002), including role-entity/documentation mismatch.
3. Perform read-only PostgreSQL schema verification; establish whether `ddl-auto:update` is acceptable in the target environment.
4. Establish MinIO/Ollama availability and safe credentials/config ownership without changing configs.
5. Confirm whether missing domains (supplier, bank statement, notification, comments, audit API, admin settings) are genuinely in scope rather than inherited mock UI.
6. Complete authorization runtime tests for invoices/classifications/financial data and cross-company/admin policy.

## 32. Final Integration Checklist

- [ ] Auth
- [ ] User
- [ ] Role
- [ ] Permission
- [ ] Company scope
- [ ] Employee ownership
- [ ] Dashboard
- [ ] Document
- [ ] Document Detail
- [ ] Search
- [ ] Filter
- [ ] Pagination
- [ ] Upload
- [ ] MinIO
- [ ] Document Version
- [ ] Invoice
- [ ] Invoice Item
- [ ] OCR
- [ ] Extracted Fields
- [ ] AI Classification
- [ ] Classification Correction
- [ ] Review
- [ ] Field Correction
- [ ] Accounting Category
- [ ] Accounting Entry
- [ ] Financial Transaction
- [ ] Reconciliation
- [ ] Notification
- [ ] Audit Log
- [ ] Reports
- [ ] Admin settings
- [ ] Security / IDOR
- [ ] Runtime verification
- [ ] Database verification

# 33. Runtime & Database Verification

## Verification boundary

No source was modified. Spring Boot was **not started** against PostgreSQL because `application.yml` contains `spring.jpa.hibernate.ddl-auto: update`; therefore `RUNTIME_START_SKIPPED_DUE_TO_DDL_AUTO_UPDATE`. Port `localhost:5432` accepted a TCP connection, but `psql` is not installed/available in this workspace and no other existing read-only PostgreSQL client/configuration was used. Consequently no `information_schema`, constraints, indexes, enum/check constraints or data were read.

**PostgreSQL: NOT_VERIFIED.** TCP reachability is not schema verification. All entity-to-database outcomes below are `RUNTIME_UNKNOWN` unless explicitly marked source-derived.

## Entity ↔ database verification

| Area | Source expectation | Runtime DB result | Verification result |
|---|---|---|---|
| `documents` | Java `@Table("documents")`, identity `document_id`; `company_id`, `type_id`, `original_file_name`, `file_name`, `mime_type`, `file_size`, `object_key`, `processing_status`, `review_status`, `uploaded_by`, timestamps | not queried | RUNTIME_UNKNOWN |
| `users`, `roles`, `user_roles`, `role_permissions` | role assignment model: `UserRoleAssignment`/`RolePermission`, role `code`; `User.role` is transient | not queried | RUNTIME_UNKNOWN |
| `invoice_data`, items, fields | document one-to-one invoice; items/extracted fields invoice-owned | not queried | RUNTIME_UNKNOWN |
| OCR/classification/review | document-owned OCR/classification/review; enum strings in entities | not queried | RUNTIME_UNKNOWN |
| accounting/reconciliation | company-linked category/transactions/batches/reconciliations | not queried | RUNTIME_UNKNOWN |

**Entity ↔ Database: NOT_VERIFIED.** The earlier source-only entity table list remains a candidate and must not be treated as actual PostgreSQL inventory. `DATABASE_MISMATCH` detected at runtime: **0**; this means none could be measured, not that there are none.

## Verified document state model (C001 evidence)

| Evidence point | Verified source behavior |
|---|---|
| Java enums | `DocumentStatus = UPLOADED, PROCESSING, PROCESSED, NEED_REVIEW, COMPLETED, FAILED`; `ReviewStatus = PENDING, APPROVED, REJECTED, CORRECTED`; `ClassificationStatus` is independent. |
| Entity fields | `Document.status` maps to required `processing_status`, default `UPLOADED`; `reviewStatus` maps to required `review_status`, default `PENDING`. |
| Upload | `create`/`createFromUpload` construct a document without overrides, so `@PrePersist` provides `UPLOADED/PENDING`. |
| AI process | `DocumentAiProcessingService`: eligible `UPLOADED` document becomes `PROCESSING`, then persistence sets `PROCESSED` or `NEED_REVIEW`; any caught runtime failure sets `FAILED`. PDF processing explicitly returns `PDF_AI_PROCESSING_PENDING`; only JPEG/PNG are accepted by this flow. |
| AI persistence | Writes latest OCR result (when raw text), Classification (`CLASSIFIED` or `NEED_REVIEW`), Invoice/items/extracted fields; sets `reviewStatus=PENDING`. |
| Classification approval | `ClassificationService.approve` sets classification `ACCEPTED`, document `COMPLETED`, review `APPROVED`; correction marks classification `CORRECTED` only. |
| Review workflow service | Creates `DocumentReview` with `PENDING` and can complete a review; it does **not** update the parent document state and no review controller was found. |
| Database constraint/check | not verified; PostgreSQL query required. |

### C001 presentation-state proposal — source-derived, no schema change

| Backend processing + review (+ classification) | Proposed UI `displayStatus` |
|---|---|
| `UPLOADED` + `PENDING` | `draft` / `uploaded` (product label decision required) |
| `PROCESSING` | `processing` |
| `PROCESSED` + `PENDING` | `submitted` / `pending_review` (decision required) |
| `NEED_REVIEW` + `PENDING` | `under_review` / `need_review` |
| any + `APPROVED` or classification `ACCEPTED`/document `COMPLETED` | `approved` |
| any + `REJECTED` | `rejected` |
| `FAILED` | `processing_failed` |

`request_info`, `archived`, and `void` have no verified source state. They must not be represented as a database enum addition by default. Implement a mapper/read model or frontend adapter after the product vocabulary decision; a synthetic DB `status` column is not justified.

## C002 verified role model

| Database role | Backend authority | JWT | Frontend role | Status |
|---|---|---|---|---|
| role `code` in `roles` (runtime unknown) | `ROLE_` + `role.code`; permissions as `PERMISSION_` + permission code | JWT has only `sub` (username), no roles/permissions claims | `ADMIN` | SOURCE_VERIFIED / DB_RUNTIME_UNKNOWN |
| same | same | same | `ACCOUNTANT` | SOURCE_VERIFIED / DB_RUNTIME_UNKNOWN |
| same | same | same | `EMPLOYEE` | SOURCE_VERIFIED / DB_RUNTIME_UNKNOWN |
| same | same | same | `USER` | SOURCE_VERIFIED / DB_RUNTIME_UNKNOWN |
| no source evidence | none | none | `STAFF` | NOT_FOUND_IN_REAL_FRONTEND_OR_BACKEND |

Canonical authentication role source is active `UserRoleAssignment → Role.code`, loaded on each JWT-authenticated request by `CustomUserDetailsService`. `User.role` is `@Transient`; the old enum/documentation is not canonical persistence. The actual frontend routes/`getEffectiveRole` recognize `ADMIN`, `ACCOUNTANT`, `EMPLOYEE`, `USER`, not `STAFF`. Thus no `STAFF → EMPLOYEE` mapping should be implemented: it is unsupported by this real project and is an inherited handover term.

## C003 detail, C004 files, C005 reconciliation, C006 settings

**C003 — verified relationships.** A document has one Invoice, many versions/OCR results/classifications/reviews/logs/entries. Invoice owns items and extracted fields. Existing real frontend already obtains document, OCR and invoice separately. Recommendation: **A, multiple existing endpoints first**, with a frontend view adapter; it preserves existing security/scoping and limits first-phase backend surface. Consider a composed read DTO only after measuring N+1/request latency and agreeing the canonical presentation state. Advantage: small incremental change; drawback: multiple calls and no atomic snapshot.

**C004 — confirmed.** `DocumentResponse` currently exposes `filePath`, whose source mapping is `object_key`; therefore the browser can receive a MinIO object key today. `download` and `preview` call `findById` then `download`, both of which resolve through `DocumentService.load`; this is source-verified authorization before MinIO streaming. The required browser architecture remains `authorized backend endpoint → MinIO`; later DTO adaptation must omit object key and expose only authorized endpoint URLs/identifiers.

**C005 — confirmed resolvable with adapter.** `ReconciliationService` uses document/invoice line amount arithmetic and persists `ReconciliationBatch`/`Reconciliation`; it has no bank-statement entity, repository, controller, or actual bank feed. It cannot satisfy a bank-statement UI literally, but does support a document-reconciliation screen. Do not create `bank_statements`; classify the handover bank-statement UI as `NEEDS_DECISION` and use a reconciliation adapter for current scope.

**C006 — confirmed server-only.** `application.yml` configures PostgreSQL, MinIO endpoint/access key/secret/bucket, JWT secret/expiration, AI provider/timeout/Ollama base URL/model. Environment-variable overrides are used for most secrets. Classification: provider/model/timeout and connectivity health are `RUNTIME_CONFIG` (not mutable from UI without separate design); endpoint may be `SERVER_ONLY`; DB password, MinIO access/secret keys, cloud API key and JWT secret are `SECRET`; no existing persisted setting is `SAFE_FOR_UI`. No settings table/API is justified.

## Authentication, authorization, company scope and ownership

Authentication is source-verified: public `POST /api/v1/auth/login` accepts `LoginRequest`; response contains `token`, `tokenType`, `user`; frontend saves token then calls `/api/v1/users/me`, and Axios sends `Authorization: Bearer <token>`. A 401 outside auth URLs clears local token/user and redirects to `/login`. Logout is local token removal; there is no server logout/revocation endpoint. JWT contains username subject only; current roles are reloaded from the database.

| Resource | ADMIN | ACCOUNTANT | EMPLOYEE/USER | Company check | Owner check | Verified level |
|---|---|---|---|---|---|---|
| Document page/detail/update/delete | all (optionally requested company for page) | own company | own uploaded docs | yes | yes employee | SOURCE_VERIFIED |
| Document download/preview | same via `load` | same via `load` | same via `load` | yes | yes employee | SOURCE_VERIFIED |
| Invoice detail/by-document | all | all invoices (not company-scoped) | own uploaded doc invoice | employee yes | yes employee | SOURCE_VERIFIED — SECURITY_GAP for accountant scope |
| Classification | document load first | document load first | read allowed, document load first | yes | yes employee | SOURCE_VERIFIED |
| Reconciliation | document load first | document load first | controller disallows | yes | n/a | SOURCE_VERIFIED |
| Financial transaction | all/selected company | current company | current company if permission assigned | yes | no personal ownership design | SOURCE_VERIFIED |
| Statistics dashboard | all | all | own uploaded docs | accountant no | employee yes | SOURCE_VERIFIED — SECURITY_GAP for accountant scope |
| Financial dashboard | all | own company | endpoint disallows | yes | n/a | SOURCE_VERIFIED |

Confirmed `SECURITY_GAP` count: **2** (accountant `InvoiceService` read path and statistics dashboard aggregate are all-company, whereas document policy scopes accountant to company). Whether all-company accountant access is intentional needs a product authorization decision; source evidence makes it inconsistent. No runtime authorization tests were run.

## MinIO, OCR and AI verification

MinIO architecture is source-verified: `POST /documents/upload` requires multipart field `file`, sanitizes name, derives `company-{id}/documents/{year}/{month}/{uuid}.{ext}`, creates the configured bucket when absent, stores via MinIO, and creates a version. Download/preview stream by object key after `load` authorization. Config bucket default is `invoices`; service fallback is `invoice-files`, but injected config wins in the default application configuration. Connectivity, credentials, bucket access and data handling are untested: `MINIO_RUNTIME_VERIFICATION_REQUIRED`.

OCR is **VERIFIED_ARCHITECTURE**, not a separately verified real OCR engine: OCR result storage exists, but the observed active extraction route is AI-provider based and persists raw text as OCRResult/fields. AI is **VERIFIED_ARCHITECTURE**: provider abstraction, Ollama/external implementations, configured provider `ollama`, base URL/model/timeout, connectivity endpoint, parser/validator and persistence path are source-present. No connectivity call, model download or document inference was performed: `AI_RUNTIME_VERIFICATION_REQUIRED`.

## Frontend service boundary and routes

| Item | Result |
|---|---|
| `api.ts`, `authService.ts`, `documentService.ts`, `invoiceService.ts`, `dashboardService.ts`, `classificationService.ts`, `financialTransactionService.ts`, `roleService.ts`, `aiService.ts` | REAL_API (source paths match current backend, subject to runtime contract verification). |
| `mockData.ts`, `mockData.js` | MOCK; duplicate mock modules remain in repository. |
| Upload/detail screens | MIXED: real services exist, but visual/model adapters may remain incomplete. |
| `UnavailableFeature` routes (settings/storage/classification/reports and others) | UNAVAILABLE, explicitly not backed by UI functionality. |
| Admin routes | real dashboard/users/roles; documents/upload reuse accountant components. |
| Accountant routes | real dashboard/documents/detail/upload/financial/AI test; storage/classification/reports/settings placeholders. |
| Employee routes | dashboard real shell; documents/detail reuse accountant components; upload is nested EMPLOYEE-only. | 

Employee reuse of accountant documents/detail is a **UI security risk**: backend guards are necessary and source document scope is strong, but UI can render inappropriate accountant controls unless action gating is audited during implementation. Do not remove mock data until each consuming screen is proven to use real service data.

Unverified/missing handover domains remain: Supplier, Bank Statement, Notification, Comments, Tags, Classification Rules, Audit API, Admin Settings, Storage Administration, Backup/Restore = **NEEDS_DECISION** (not evidence of a required project domain).

# 34. Verified Conflict Decisions

| ID | Status | Evidence | Recommended solution | DB change? | API change? | Frontend change? |
|---|---|---|---|---|---|---|
| C001 | CONFIRMED; RESOLVABLE_WITH_ADAPTER | two persisted source fields and AI transitions | canonical display-state mapper/read model | no | possibly read DTO later | yes |
| C002 | CONFIRMED; RESOLVABLE_WITH_ADAPTER | role codes/authorities, JWT subject-only, frontend uses EMPLOYEE not STAFF | canonical `Role.code` contract; no STAFF alias | no | no | align labels/guards later |
| C003 | RESOLVABLE_WITH_ADAPTER | verified document/invoice/OCR/classification graph | multiple authorized endpoints then adapter | no | no initially | yes |
| C004 | CONFIRMED | `filePath` is returned; protected stream endpoints use `load` | stop exposing object key in presentation DTO | no | response DTO adaptation | yes |
| C005 | PARTIALLY_CONFIRMED; REQUIRES_DECISION | reconciliation is intra-document arithmetic, no bank source | adapter only for reconciliation scope | no | no initially | yes if bank UI retained |
| C006 | CONFIRMED | config holds operational/secrets, no settings model | server-only config; expose sanitized health only | no | optional safe read health | placeholder remains |

Database register decision: **DB-01 NEEDS_PRODUCT_DECISION**, **DB-02 NEEDS_PRODUCT_DECISION**, **DB-03 ADAPTER_SUFFICIENT for now**. No schema change is approved.

# 35. Verified Phase 0 Plan

Phase 0 is planning only; it is **not implemented**.

| Item | Plan |
|---|---|
| Goal | Define Canonical Role Contract, Canonical Document Presentation State, Canonical Document Read Contract without schema change by default. |
| Prerequisites | Product decision for `draft` versus `uploaded`, `submitted`/`under_review`, and accountant cross-company policy; read-only PostgreSQL schema verification. |
| Existing files affected later | frontend `authService.ts`, guards, document service/pages; backend document DTO/controller/service mapper tests; security/dashboard/invoice services only if policy confirms gaps. |
| Potential new files later | frontend document presentation adapter; backend read-model mapper/DTO only if multi-call UX is insufficient. |
| API changes | Document response must no longer expose raw object key to UI; optional composed detail endpoint later, not first step. |
| Database impact | None by default. |
| Security impact | Preserve service `load` scoping; resolve the two accountant all-company inconsistencies or explicitly approve them. |
| Test required later | one compile/build pass; auth/role, company isolation, employee ownership, document mapper/pagination, file authorization and workflow transition tests — one run per phase; on failure record and stop rather than iterate automatically. |
| STOP condition | Approved role/state/read contracts and runtime schema verification result documented; no implementation begins without a new instruction. |

**Phase 0 readiness: NOT READY** — it is blocked by PostgreSQL metadata verification and the named product authorization/state decisions.

# 36. Runtime Verification Remaining

1. Run strictly read-only PostgreSQL metadata queries with an approved/available client; compare actual tables, columns, nullability, PK/FK/unique/check/identity constraints against entities.
2. Confirm current role records and role-permission assignments through read-only queries; never infer from enum/documentation alone.
3. Run non-mutating authorization tests for all roles, especially accountant invoice/statistics scope and employee subresources.
4. Verify MinIO connectivity, configured bucket, upload/download authorization and bucket policy without creating/changing objects unless separately authorized.
5. Verify Ollama connectivity only; do not submit documents or download models.
6. Verify frontend API response shapes and CORS in a controlled runtime that cannot invoke `ddl-auto:update` against production data.
