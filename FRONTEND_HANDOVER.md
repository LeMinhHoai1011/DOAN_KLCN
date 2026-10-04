# FRONTEND HANDOVER — e-Chứng từ

> Audit source tại 28/09/2026. Đây là frontend mẫu, **không có HTTP client hay API thật**. Mọi endpoint bên dưới là contract **đề xuất từ UI**, không phải endpoint hiện hữu.

## 1. Kiến trúc và nguồn dữ liệu

| Hạng mục | Kết quả |
|---|---|
| Framework | React 18.3.1, Vite 5.4.7, JavaScript JSX |
| Router | react-router-dom 6.26.2 (`BrowserRouter`) |
| UI/CSS | Tailwind CSS 3.4.12, CSS toàn cục `src/index.css`; dark mode class |
| Icon/chart | lucide-react 0.441.0; Chart.js 4.4.3 + react-chartjs-2 5.2.0 |
| State | React Context: Auth, Document, Theme, Toast; local component state; không Redux/Zustand |
| Authentication | [MOCK] chọn user demo theo `id`, không password/token/refresh token |
| Storage browser | `localStorage.auth.user` (toàn object User), `localStorage.theme` (`dark`/`light`); không SessionStorage |
| Service | `documentService.js`: thuần helper `findDuplicates`, `suggestClassification`, confidence/OCR simulator; không gọi mạng |

### Cây thư mục quan trọng

```text
src/
  App.jsx, main.jsx, index.css
  context/{Auth,Document,Theme,Toast}Context.jsx
  data/mock.js
  layouts/DashboardLayout.jsx
  components/{ProtectedRoute,GlobalSearch,DataTable,FilterBar,FileDropzone,FilePreview,
              Modal,StatusBadge,StatCard,ChartCard,Timeline,EmptyState}.jsx
  pages/Login.jsx, DocumentDetail.jsx
  pages/admin/{Dashboard,Users,Permissions,Categories,Suppliers,AiOcr,Storage,AuditLogs,Settings}.jsx
  pages/accountant/{Dashboard,Approval,Documents,Classify,Reports}.jsx
  pages/staff/{Home,Upload,MyDocuments,Notifications}.jsx
  services/documentService.js; utils/{format,permissions,chart}.js
```

`DocumentContext` seed từ `mock.documents`/`mock.notifications` và chỉ tồn tại trong memory. Nó mô phỏng: create/update/delete document, submit/re-submit, receive-review, approve, reject, request-info, archive, void, comment và mark notification read. Toast tự đóng sau 4 giây; dashboard giả skeleton bằng `setTimeout(600)`; kiểm tra AI connection giả bằng timeout.

## 2. Route, layout và phân quyền route

Mọi route trừ `/login` nằm trong `DashboardLayout` (sidebar theo role, topbar, global search, notification dropdown, dark toggle). `ProtectedRoute` redirect người chưa login tới `/login`; role không đúng redirect về role-home. Không có PUBLIC ngoài login.

| Nhóm | URL → page | Role route | Sidebar | Redirect/liên quan |
|---|---|---|---|---|
| COMMON | `/login` → `Login.jsx` | public | none | login → role home |
| COMMON | `/` | authenticated | role sidebar | admin/accountant/staff home |
| COMMON | `/documents/:id` → `DocumentDetail.jsx` | bất kỳ authenticated | role sidebar | search/notification/related dùng URL này |
| ADMIN | `/admin/dashboard` → Dashboard | admin | admin | protected |
| ADMIN | `/admin/users` → Users | admin | admin | protected |
| ADMIN | `/admin/permissions` → Permissions | admin | admin | protected |
| ADMIN | `/admin/categories` → Categories | admin | admin | protected |
| ADMIN | `/admin/suppliers` → Suppliers | admin | admin | protected |
| ADMIN | `/admin/ai-ocr` → AiOcr | admin | admin | protected |
| ADMIN | `/admin/storage` → Storage | admin | admin | protected |
| ADMIN | `/admin/audit-logs` → AuditLogs | admin | admin | protected |
| ADMIN | `/admin/settings` → Settings | admin | admin | protected |
| ACCOUNTANT | `/accountant/dashboard` → Dashboard | accountant, admin | accountant/admin | protected |
| ACCOUNTANT | `/accountant/approval` → Approval | accountant, admin | accountant/admin | protected |
| ACCOUNTANT | `/accountant/documents` → Documents | accountant, admin | accountant/admin | protected |
| ACCOUNTANT | `/accountant/documents/:id` → DocumentDetail | accountant, admin | accountant/admin | protected |
| ACCOUNTANT | `/accountant/classify` → Classify | accountant, admin | accountant/admin | protected |
| ACCOUNTANT | `/accountant/reports` → Reports | accountant, admin | accountant/admin | protected |
| EMPLOYEE | `/staff/home` → Home | staff | staff | protected |
| EMPLOYEE | `/staff/upload` → Upload | staff | staff | protected |
| EMPLOYEE | `/staff/documents` → MyDocuments | staff | staff | protected |
| EMPLOYEE | `/staff/notifications` → Notifications | staff | staff | protected |
| redirect | `*` | — | — | redirect `/` |

## 3. Màn hình và hành vi

### Public/common

| Màn hình/file | Dữ liệu và interaction |
|---|---|
| Login — `pages/Login.jsx` | [MOCK] 6 user active: `id,name,email,department,role,active`. Mỗi card user là nút login; không form/password. |
| Global Search — `components/GlobalSearch.jsx` | Ctrl+K hoặc topbar mở dialog; query trong `number,symbol,partner,taxCode,uploader,tags`; mũi tên/Enter chọn; click → `/documents/:id`. Backend cần search có scope authorization. |
| Document detail — `pages/DocumentDetail.jsx` | Tabs: Thông tin, AI & OCR, Lịch sử, Bình luận, Liên quan. Field document đầy đủ tại mục Data model. Form edit: `number,date,partner,taxCode,pretax,vat,total,type,category,note`; `symbol` read-only. Actions theo role/status: Save, Submit/Re-submit, receive review, approve, reject(reason required), request-info(note required), archive(reason optional), void(reason required), delete(confirm); comment `text`; related/duplicate click document. |

### ADMIN

| Page/file | UI fields, table/chart | Button/modal/function |
|---|---|---|
| Dashboard | KPI: total documents, processing, need-info, active/total users, monthly out-expense, storage used/quota; AI/OCR online; line monthly count, pie DOC_TYPES, bar documents per department; 6 recent docs (`number,type,partner,uploader,createdAt,total,status`); 5 audit events | Read-only; backend aggregate/dashboard feed |
| Users | table `name,email,department,role,active`; view modal includes user identity and uploader document count by status; filters q/name-email, role, active/locked | Add/Edit modal `name,email,department,role`; lock/unlock; no delete/reset password UI |
| Permissions | matrix `role,label,perms`; permissions `view,upload,edit,delete,approve,export`; user counts per role | Checkbox opens confirmation modal then toggles matrix only |
| Categories | tabs Document Type, Expense Category, Classification Rule. rules table/form `id,name,keyword,category,type,active` | add/delete type/category; create rule (`name,keyword,category,type`), toggle active, delete |
| Suppliers | table `name,tax,address,email,phone,active`; filters q name/tax/email + status | Add/Edit modal same five fields; activate/disable. No document relationship endpoint called |
| AI & OCR | config `provider,mode,model,endpoint,ocrEngine,timeout`, status `aiOnline,ocrOnline`, stats `requestsToday,success,failed,avgProcessingMs` | save local state; Test connection [FAKE]; provider options Ollama/OpenAI/Anthropic/Gemini, mode local/cloud |
| Storage | `storageUsed,storageQuota,retentionPolicy`, distribution `type,count,size,pct`; backup timestamp text | backup/restore [FAKE], save retention local only |
| Audit logs | table `time,user,role,action,target,ip,changes`; filters q,user,action,from,to | “Trước/Sau” modal reads `changes.before/after` |
| Settings | company form `name,taxCode,phone,address,email,fiscalYearStart` | Save toast only |

### ACCOUNTANT

| Page/file | UI fields/chart | Button/modal/function |
|---|---|---|
| Dashboard | KPIs pending, VAT input, VAT output, rejected/need-info errors, total expense; bar income/expense per month; doughnut expense by category; line quarterly VAT | Queue link; data is document aggregate |
| Approval | tabs all/submitted/under_review/low_conf/dup/need_info/overdue. List/detail: `number,partner,date,total,status,type,uploader,uploaderDepartment,approver,taxCode,vat,category,ocr confidence,note,file,flags` | select/bulk approve, receive review, approve, reject modal `reason`, request-info modal `reason`, view/dismiss duplicate list. `need_info` shown but not actionable here. |
| Documents | table/card `number,type,partner,taxCode,date,total,status,tags`; filters `q,status,type,category,uploader,from,to,min,max`; sortable date/total; local pagination 10; card/table view | open detail; tag modal adds `tag`; clear filters; no actual export |
| Classify | classification candidates (OCR/AI/document) and bank reconciliation. Bank rows `id,account,date,description,amount,status,matchedDocId` | edit classification modal `type,category`; locally link/unlink bank statement to document. Need OCR/AI and reconciliation APIs |
| Reports | period options Q3/Q2/Q1/2026/12-months; aggregate income/expense/VAT/status/category/type/supplier documents, charts and report tables | period dropdown and “export” controls are demo/client only (if visible); backend reports/export required |

### EMPLOYEE

| Page/file | UI fields/chart | Button/modal/function |
|---|---|---|
| Home | current user's stats: total, draft, pending, approved, rejected/need-info; personal spending bar/month; latest own docs | Upload CTA; list link |
| Upload | wizard: select files → review OCR form → submit. File item `file,name,size,type,preview,status,error,ocr,form`; form `number,symbol,date,partner,taxCode,pretax,vat,total,type,category,note` | remove/retry, previous/next, save draft/create document, submit then redirect My Documents. Validation detailed below. |
| My Documents | own docs table `number,type,partner,date,total,status,progress`; status tab filter; timeline modal (`history`); re-submit modal `resubmitNote` | row/open detail; resubmit updates status only (note is not persisted by context) |
| Notifications | own `id,type,title,message,time,read,link,docId`; unread filter | mark one/all read; click navigates link |

## 4. Data model frontend

| Object | Fields used |
|---|---|
| User | `id,name,email,department,role,active` |
| Document | `id,number,symbol,type,direction,partner,taxCode,date,pretax,vat,total,category,status,uploaderId,uploader,uploaderDepartment,approverId,approver,submittedAt,reviewedAt,fileName,fileSize,tags,note,rejectReason,createdAt,closeInfo,ocr,ai,flags,history` |
| DocumentFile | UI currently flattens to `fileName,fileSize`; upload temporarily has browser `File`, preview URL/type |
| OCRResult | `status,engine,startedAt,completedAt,fields{number,symbol,date,partner,taxCode,pretax,vat,total},confidence` (same field keys → percentage) |
| AIClassification | `suggestedType,suggestedCategory,confidence,accepted,userCorrection{type,category}` |
| DocumentFlags | `lowConfidence,duplicateSuspect,overdue` |
| DocumentHistory | `event,actor,time,note` |
| Comment | `id,user,text,time` |
| Notification | `id,userId,type,title,message,time,read,link,docId` |
| Supplier | `name,tax,address,email,phone,active` (no frontend id: backend must add stable `id`) |
| ClassificationRule | `id,name,keyword,category,type,active` |
| BankStatement | `id,account,date,description,amount,status,matchedDocId` |
| AuditLog | `id,userId,user,role,action,target,ip,time,changes{before,after}` |
| CompanySetting | `name,taxCode,address,phone,email,fiscalYearStart,storageQuota,storageUsed,retentionPolicy` |
| AiOcrSetting | `provider,mode,model,endpoint,ocrEngine,timeout,aiOnline,ocrOnline,stats` |

### Enums/status and rendering

| Value | Vietnamese label / UI meaning | Screens |
|---|---|---|
| roles `admin,accountant,staff` | Quản trị viên, Kế toán, Nhân viên | route/sidebar/users/audit |
| document `draft,submitted,under_review,approved,rejected,need_info,archived,void` | Nháp, Đã gửi, Đang duyệt, Đã duyệt, Bị từ chối, Chờ bổ sung, Đã lưu trữ, Đã vô hiệu hóa; `StatusBadge` colors status-specific | all document UI |
| direction `in,out` | thu/đầu ra, chi/đầu vào (UI calculation semantics) | dashboards/reports |
| notification `approved,rejected,need_info,ocr_failed,new_document,low_confidence,duplicate,overdue` | success/red/orange/blue/amber/red icons | topbar/staff notification |
| OCR | `completed` else UI “Thất bại”; low confidence `<80` amber | detail/approval |
| statement | `matched,unmatched` | classify |
| permissions | `view,upload,edit,delete,approve,export` | permissions UI only |
| DOC_TYPES | Hóa đơn GTGT, Hóa đơn bán hàng, Phiếu thu, Phiếu chi, Sao kê ngân hàng | category/filter/form |
| EXPENSE_CATEGORIES | Lương, Marketing, Vận chuyển, Tiện ích, Mua sắm, Dịch vụ, Khác | category/filter/form/report |

## 5. Upload and files

Flow actually shown: Employee chooses/drop files → `FileDropzone.validateFile` → per-file preview/metadata → `simulateOcrResult` [FAKE] → user corrects extracted fields → create draft or submit → document status/history UI update. Accepted extensions: `.pdf,.jpg,.jpeg,.png`; maximum **10 MiB per file**. Errors: unsupported type and >10MiB. No network upload/progress/abort; preview is only UI `FilePreview`/browser preview. One document is constructed per item; current document stores only one filename/size.

Proposed MinIO contract: `POST /api/v1/documents` with `multipart/form-data`: field `file`, field `metadata` JSON (`number,symbol,date,partner,taxCode,pretax,vat,total,type,category,note`, desired `submit`); response Document including `fileId,fileName,fileSize,contentType,downloadUrl/previewUrl,processingStatus`. Prefer server-side MIME/size validation, malware scan, object key opaque, presigned download/preview; client must surface `UPLOADING/PROCESSING/OCR_FAILED` + progress/error although these states are presently not implemented.

## 6. Search, filter, sort, pagination requirements

| Screen | Proposed query parameters |
|---|---|
| Global document search | `q`, `fields=number,symbol,partner,taxCode,uploader,tags`, `page,size` |
| Accountant documents | `q,status,type,category,uploaderId,from,to,minAmount,maxAmount,sort=date:desc|total:asc,page,size,view` |
| My documents | `uploaderId` inferred current principal, `status,page,size` |
| Approval queue | `status=submitted,under_review,need_info`, `flag=lowConfidence|duplicate|overdue`, `assignee`, `page,size` |
| Users | `q,role,active,page,size` |
| Suppliers | `q,active,page,size` |
| Audit logs | `q,userId,action,from,to,page,size,sort=time:desc` |
| Reports | `period/from/to,groupBy,documentStatus,direction` |

Current client pagination exists only Accountant Documents, is 1-based and `PAGE_SIZE=10`; all filters/search are local. Backend may be 0-based internally but response must clearly expose `page,size,totalElements,totalPages` and frontend adapter must normalize.

## 7. API requirements (proposed)

All authenticated endpoints require bearer authentication and backend authorization; every list uses envelope `{content,page,size,totalElements,totalPages}` and `GET detail` returns the relevant object fields above.

| Method URL | Role | Request → response / screens and action |
|---|---|---|
| `POST /api/v1/auth/login` | public | credentials → access/refresh token + User; Login (replace demo chooser) |
| `POST /api/v1/auth/logout`, `GET /api/v1/users/me` | auth | → success / User; layout/session |
| `GET,POST /api/v1/admin/users`; `GET,PUT /api/v1/admin/users/{id}`; `PATCH /{id}/status` | admin | User create/update/status; Users |
| `GET,PUT /api/v1/admin/roles/permissions` | admin | permission matrix; Permissions |
| `GET,POST /api/v1/suppliers`; `GET,PUT /api/v1/suppliers/{id}`; `PATCH /{id}/status` | admin | Supplier; Suppliers |
| `GET,POST /api/v1/admin/document-types`, `/expense-categories`, `/classification-rules`; `PUT,DELETE /{resource}/{id}` | admin | values/rules; Categories |
| `GET,PUT /api/v1/admin/company-settings` | admin | CompanySetting; Settings |
| `GET,PUT /api/v1/admin/ai-ocr/settings`; `POST /api/v1/admin/ai-ocr/test` | admin | AiOcrSetting/test result; AI & OCR |
| `GET /api/v1/admin/storage`; `PUT /api/v1/admin/storage/retention`; `POST /backup`; `POST /restore` | admin | storage stats/policy/job; Storage |
| `GET /api/v1/audit-logs` | admin | AuditLog page; Audit Logs |
| `GET /api/v1/admin/dashboard` | admin | KPI, monthly document counts, type/department counts, recent docs, recent audit, ai/ocr health; Admin Dashboard |
| `GET,POST /api/v1/documents`; `GET,PUT,DELETE /api/v1/documents/{id}` | scoped by role | Document/list/filter; detail, staff upload, lists |
| `POST /api/v1/documents/{id}/submit`, `/resubmit`, `/start-review`, `/approve`, `/reject`, `/request-info`, `/archive`, `/void` | state/role controlled | body `{reason}` where needed; Document; workflow actions |
| `POST /api/v1/documents/bulk-approve` | accountant/admin | `{ids}` → changed documents; Approval |
| `GET /api/v1/documents/{id}/duplicates`, `/related` | permitted viewer | documents; detail/approval |
| `GET,POST /api/v1/documents/{id}/comments` | permitted viewer | Comment; detail |
| `POST /api/v1/documents` multipart and `GET /api/v1/files/{id}/download` | staff/admin per scope | upload metadata/file → processing Document; Upload/preview |
| `GET /api/v1/notifications`; `PATCH /api/v1/notifications/{id}/read`; `POST /read-all` | authenticated own | Notification page/dropdown |
| `GET /api/v1/accountant/dashboard`, `/reports` | accountant/admin | aggregates/chart series; accountant dashboard/reports |
| `GET,PUT /api/v1/documents/{id}/classification`; `GET /api/v1/bank-statements`; `POST,DELETE /api/v1/bank-statements/{id}/match` | accountant/admin | classification + reconciliation; Classify |

For write responses return the updated Document plus history/audit version or a version/ETag to avoid approval/edit races.

## 8. Frontend → backend and database mapping

| Screen/action | Frontend file | Required data → proposed API | Method/role |
|---|---|---|---|
| login/session | Login/AuthContext | User/token → auth endpoints | POST public, GET auth |
| dashboard cards/charts | three Dashboard files | server aggregates and series → dashboard endpoints | GET corresponding role |
| browse/search/detail | Documents/MyDocuments/DocumentDetail/GlobalSearch | Document + OCR/AI/file/history/duplicates | GET document endpoints, scoped |
| upload/review/submit | Upload | File + metadata + processing result | multipart POST staff |
| approval | Approval/DocumentDetail | queue and state transition/reason | GET/POST accountant/admin |
| classification/reconcile | Classify | OCR/AI classification, bank statements/match | GET/PUT/POST accountant/admin |
| users/roles/settings | admin pages | User, permissions, settings, catalogue | admin CRUD |
| alerts/audit/storage | layout/Notifications/AuditLogs/Storage | Notification, AuditLog, storage stats/jobs | auth/admin |

| UI field | Object | Expected backend field | Database mapping |
|---|---|---|---|
| document totals, identifiers, party, dates/status | Document | same camelCase DTO (or adapter) | TO_BE_MAPPED |
| OCR field values/confidences | OCRResult | nested `ocr` | TO_BE_MAPPED |
| AI suggested/correction | AIClassification | nested `ai` | TO_BE_MAPPED |
| fileName/size/preview/download | DocumentFile | `fileId,objectKey? (not expose),contentType,size,url` | TO_BE_MAPPED (MinIO metadata) |
| employee/approver labels | User reference | `uploaderId,approverId` + display object/name | TO_BE_MAPPED |
| workflow timeline | DocumentHistory | event/actor/time/note | TO_BE_MAPPED |
| all admin master/settings/reconcile fields | models in §4 | same DTO fields | TO_BE_MAPPED |

## 9. Authorization analysis

| Role | Route access | Actions shown | Backend must enforce |
|---|---|---|---|
| admin | all admin + accountant routes; common detail | user/master/config/storage/audit; accountant document actions | all admin scopes, cannot trust hidden UI |
| accountant | accountant + common detail | classify, reconcile, receive/review/approve/reject/request info/archive/void, reports | document state transition and view scope |
| staff | staff + common detail | own upload/draft/edit/submit/resubmit/comments/notifications | ownership on every document/file/comment endpoint |

`ProtectedRoute` checks only local `auth.user.role`; `hasPermission()` always returns `true`. `utils/permissions.js` hides actions based on role, status and uploader, but direct route/detail is not ownership-protected at route level. `DocumentContext` accepts arbitrary IDs. Therefore all authorization, ownership, state transitions, bulk approval, download and audit creation **must be backend-enforced**.

## 10. Business flow actually evidenced

1. **Login:** choose active demo User → store whole user in localStorage → redirect role home. Target: authenticate → issue tokens → `/me` → role redirect.
2. **Document:** select file → validate → fake OCR/AI → user corrects metadata → draft or submit → submitted → accountant starts review → approve/reject/request info → staff resubmits if needed → approved can archive or void. Accounting journal generation is **NOT_IMPLEMENTED**.
3. **Classify/reconcile:** accountant edits classification and locally pairs a bank statement with a document. Persistence/import bank feed is **NOT_IMPLEMENTED**.
4. **Notification/audit:** workflow adds in-memory notifications; audit list is static seed, not generated by actions. Real audit/notifications are **NEEDS BACKEND**.

## 11. Mock inventory and integration issues

| File → variable | Screen/use | Classification → replacement |
|---|---|---|
| `data/mock.js` → `users`, `ROLE_LABELS` | Login, Users, layout | [MOCK] → auth/users/roles APIs |
| `mock.js` → generated `documents` (48), OCR/AI/history/flags | all dashboards/doc pages | [MOCK] → Document/OCR/AI/history APIs |
| `mock.js` → `suppliers`, document types/categories/rules | admin + document forms | [MOCK] → catalogue APIs |
| `mock.js` → `auditLogs`, `notifications` | admin dashboard/audit/layout/staff | [MOCK] → audit/notification APIs |
| `mock.js` → `bankStatements`, `companyInfo`, `aiSettings`, permission matrix | classify/admin config/storage | [MOCK] → respective APIs |
| `DocumentContext.jsx` | all workflow mutations | [LOCAL MEMORY] → service/API, cache invalidation |
| `Users/Suppliers/Categories/Permissions/Settings/Storage/AiOcr` | admin edits | [LOCAL STATE]/[FAKE API] → persistence endpoints |
| `documentService.simulateOcrResult`, dashboard loading, AI test | upload/dashboard/AI config | [FAKE API] setTimeout → job/status endpoints |

Findings: no API client/error boundary; all changes vanish reload (except auth/theme); `Suppliers` has no id; admin may enter accountant routes but sees admin sidebar; duplicated detail URLs (`/documents/:id`, `/accountant/documents/:id`); notification mock has route `/accountant/documents/5` which is valid but global detail uses `/documents`; no real download/export/backup/restore/OCR; resubmit note is collected but not persisted; terminology says VAT input/output with direction semantics that need accounting validation; access control is frontend-only; local page state creates inconsistent users/categories/settings versus document seeds.

## 12. Format conventions

`formatCurrency`: `Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0})`; date `dd/MM/yyyy`; datetime `dd/MM/yyyy HH:mm`; relative time Vietnamese from `timeAgo`; file size bytes auto B/KB/MB/GB; money stored/edited as integer VND; confidence is integer `%`, warning `<80`; null handling visibly uses `—` or hides optional blocks.

## 13. Shared components dependent on backend

`DashboardLayout` (user, notifications), `ProtectedRoute` (session/roles), `GlobalSearch` (document search), `DataTable` (paged data), `FileDropzone` (upload), `FilePreview` (signed preview/download), `StatusBadge` (canonical enum), `Timeline` (history), `ChartCard/StatCard` (aggregates), `Modal/Toast/FilterBar/EmptyState` (UI only). No reusable API layer exists.

# BACKEND INTEGRATION CHECKLIST

- [ ] Auth
- [ ] User
- [ ] Role
- [ ] Permission
- [ ] Dashboard
- [ ] Document
- [ ] Upload
- [ ] Invoice / document metadata
- [ ] OCR
- [ ] AI Classification
- [ ] Review workflow
- [ ] Accounting (**not implemented in UI beyond reports**)
- [ ] Financial transaction / bank reconciliation
- [ ] Audit log
- [ ] Notification
- [ ] Search
- [ ] Filter
- [ ] Pagination
- [ ] File storage (MinIO)
- [ ] Supplier
- [ ] Document types / expense categories / classification rules
- [ ] Company settings
- [ ] AI/OCR settings and processing health
- [ ] Storage quota, retention, backup/restore jobs
- [ ] Reporting/export
- [ ] Backend ownership/state-transition authorization
