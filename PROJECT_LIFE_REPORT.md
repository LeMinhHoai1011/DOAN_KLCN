# BÁO CÁO “SỰ SỐNG” CỦA PROJECT

**Dự án:** Hệ thống số hóa, lưu trữ và tự động phân loại hóa đơn chứng từ kế toán cho doanh nghiệp nhỏ  
**Ngày kiểm toán:** 13/10/2026 (Asia/Saigon)  
**Phạm vi:** trạng thái working tree hiện tại, chỉ đọc/chạy kiểm tra. Repo đã có thay đổi chưa commit trước kiểm toán; báo cáo đánh giá đúng trạng thái đó.  
**Quy ước:** “SỐNG” chỉ khi kiểm tra hiện tại chạy thành công hoặc luồng UI → API → service → repository/entity liên tục và không đòi hỏi bằng chứng runtime ngoài. “CÓ CODE CHƯA KIỂM CHỨNG” không đồng nghĩa chạy được.

## Kết luận điều hành

Project **không phải code giả toàn bộ**: frontend build và 15 test chạy thành công; các luồng tài liệu, OCR/AI, phân loại, đối soát, thu/chi, dashboard và xuất Excel có implementation thật, có nhiều unit/contract test trong source. Tuy nhiên **chưa thể tuyên bố hệ thống end-to-end sống** vì tại thời điểm kiểm toán PostgreSQL, MinIO, Ollama, backend và frontend đều không chạy; Docker daemon không chạy; Java launcher trên máy thoát mã 1 không in chẩn đoán; Maven wrapper không tạo artifact/test report mới. Cơ sở dữ liệu còn thiếu `dd.sql`/baseline schema và các file migration không có Flyway/Liquibase để tự chạy.

Điểm sống đã xác nhận:

- Frontend `npm run build`: thành công, Vite tạo `dist`; bằng chứng cấu hình `quan tri/package.json`, entry `quan tri/src/main.tsx`.
- Frontend `npm test -- --run`: **15/15 pass**; các test ở `quan tri/src/components/ocr-preview/ocrPreviewUtils.test.mjs`, `quan tri/src/utils/confidence.test.mjs`, `quan tri/src/services/readState.test.mjs`, `quan tri/src/utils/reconciliationPresentation.test.mjs`.
- Backend có artifact Surefire cũ ghi **159 tests, 0 failure, 0 error, 11 skipped**, nhưng timestamp trước lần kiểm toán hiện tại; chỉ là bằng chứng lịch sử, không phải lần chạy đã xác nhận hôm nay.

Điểm chết/chưa sống quan trọng nhất: startup backend chưa chứng minh; DB baseline/migration không tự động; Ollama/MinIO/Postgres đều vắng; runtime OCR/DB/Ollama tests bị điều kiện hóa; `processing_jobs`/`processing_attempts` và `audit_logs` chủ yếu là entity mồ côi; không có benchmark Precision/Recall/F1.

---

## PHẦN A — Bản đồ công nghệ thực tế

| Thành phần | Công nghệ/phiên bản thật | Bằng chứng | Đang dùng thật hay dự định |
|---|---|---|---|
| Java | Java target **21**; Java launcher cài trên PATH nhưng `java --version` thoát mã 1, không in phiên bản | `backendqt/backendqt/pom.xml` (`java.version`); launcher `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe` | Target compile có thật; runtime hiện tại **chưa dùng được** |
| Spring Boot | **3.5.6** | `backendqt/backendqt/pom.xml`, parent `spring-boot-starter-parent` | Có code thật; startup hiện tại chưa kiểm chứng |
| React | **19.3.0** (range `^`) + React DOM 19.3.0, Router 7.18.3 | `quan tri/package.json`; `quan tri/src/App.tsx` | **Đang dùng thật**, build thành công |
| Vite/TypeScript | Vite **8.3.0**, TypeScript **6.0.2** | `quan tri/package.json`, `quan tri/vite.config.ts` | **Đang dùng thật**, build thành công |
| Chart.js | Chart.js **4.5.1**, `react-chartjs-2` **5.3.1** | `quan tri/package.json`; dashboard pages gọi `dashboardService` | Có code/build được; dữ liệu runtime chưa kiểm chứng |
| Tess4J | **5.18.0**, mặc định `vie+eng`; có cả `tessdata/vie.traineddata` và `eng.traineddata` | `pom.xml`; `application.yml`; `Tess4jOcrService.recognize`; hai file dưới `backendqt/backendqt/tessdata/` | Có implementation và model data; runtime native OCR chưa chạy trong phiên này |
| OpenCV | `org.openpnp:opencv` **4.9.0-0** | `pom.xml`; `OpenCvRuntime.load`; `OpenCvImageProcessor.process` | Có implementation và unit test lịch sử; runtime hiện tại chưa kiểm chứng |
| PDFBox | **3.0.8**; render PDF thành PNG theo từng trang | `pom.xml`; `PdfImageConversionService.convert` | Có code thật; DPI lấy từ `AiProperties.Pdf`, mặc định được cấu hình trong class; runtime test hiện tại chưa chạy |
| Ollama | REST `POST /api/generate`; model **qwen3-vl:8b**; `stream=false`, `format="json"`, `think=false`, `num_ctx=8192`, `num_predict=3072`; **không thấy temperature được gửi** | `application.yml`; `config/config.local.json`; `OllamaAiProvider.generate`, record `OllamaGenerateRequest`/`OllamaOptions` | Có adapter thật; Ollama 0.34.1 client có nhưng server không chạy; chưa E2E |
| MinIO/S3 | MinIO Java SDK **8.6.0**, API S3 qua `MinioClient`; bucket mặc định `invoices` | `pom.xml`; `MinioConfig.minioClient`; `DocumentService.uploadToMinio/loadFileBytes/download` | Có code thật; port 9000 không mở, chưa E2E |
| JWT/Security | Spring Security + JJWT **0.12.6**, bearer filter, method security | `pom.xml`; `SecurityConfig.securityFilterChain`; `JwtAuthenticationFilter.doFilterInternal`; `JwtService` | Có code và endpoint guards thật; runtime chưa xác nhận |
| PostgreSQL/JPA | PostgreSQL driver do Boot quản lý; Hibernate `ddl-auto: validate` | `application.yml`; entity/repository packages | Có mapping thật; DB không chạy |
| Docker | CLI **29.8.0**; không có Dockerfile/compose; daemon không chạy | kiểm tra `docker --version`, `docker ps`; tìm file Docker trả về “Không tìm thấy” | Chỉ có CLI, **không có đóng gói Docker của project** |
| Migration | Có 11 file SQL đặt dưới `db/migration`, nhưng **không có dependency Flyway/Liquibase**; `ddl-auto=validate` | `pom.xml`; `src/main/resources/db/migration/*`; `application.yml` | **Dự định/đặt nhầm chỗ**: Spring không tự chạy các file này |

### Payload Ollama thực tế

`OllamaAiProvider.generate` gửi: model, prompt, `images` base64 (khi vision), `stream=false`, `format="json"`, `think` từ config và options chỉ gồm `num_predict`, `num_ctx`. **Không tìm thấy `temperature`**. Response thô được giữ trong `AiProviderResponse.rawResponse`; log INFO chỉ ghi độ dài/thời gian, nhưng `log.debug` ghi preview của `response` và `thinking` (`OllamaAiProvider.generate`) — có nguy cơ lộ dữ liệu hóa đơn nếu bật DEBUG.

---

## PHẦN B — Kiểm tra build/chạy

### Kết quả lệnh

| Lệnh | Kết quả hiện tại |
|---|---|
| `npm run build` tại `quan tri` | **PASS**. `tsc && vite build`; 1.994 module; tạo bundle. Cảnh báo nguyên văn: `(!) Some chunks are larger than 500 kB after minification.` Bundle JS 1.097,44 kB, gzip 333,76 kB. |
| `npm test -- --run` tại `quan tri` | **PASS: 15 tests, 15 pass, 0 fail**. |
| `.\mvnw.cmd test` / `cmd /c mvnw.cmd test` | **CHƯA KIỂM CHỨNG**. Lệnh không in output và không cập nhật Surefire report; một lần `mvnw.cmd -v` phải ngắt, exit 1. Không được coi exit 0 im lặng là build thành công. |
| Maven trực tiếp từ wrapper cache `...\apache-maven-3.9.16\bin\mvn.cmd test` | **CHƯA KIỂM CHỨNG**. Không output, không cập nhật class/report. |
| `java --version` | **FAIL**, exit code 1, output rỗng. PATH trỏ tới Oracle `javapath`. |
| `docker ps` | **FAIL**, nguyên văn: `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is running: open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.` |
| `psql --version` | **FAIL**, nguyên văn: `The term 'psql' is not recognized as a name of a cmdlet, function, script file, or executable program.` |
| `tesseract --version` | **FAIL**, nguyên văn: `The term 'tesseract' is not recognized as a name of a cmdlet, function, script file, or executable program.` (Tess4J có thể dùng native bundled khác, nên đây không tự động chứng minh Tess4J chết.) |
| `ollama --version` | Client **0.34.1**, cảnh báo: `Warning: could not connect to a running Ollama instance`. |

Artifact lịch sử `target/surefire-reports`: 34 suite, 159 test, 0 failure, 0 error, 11 skipped. Các skip là `OllamaTextRuntimeTest` (1), `RuntimePersistenceVerificationTest` (5), `DocumentTextExtractionRuntimeTest` (5), do `@EnabledIfEnvironmentVariable`; bằng chứng tại chính các class test này. Đây không phải kết quả tái chạy hiện tại.

### Trạng thái dịch vụ khi kiểm toán

`localhost:5432`, `9000`, `11434`, `8081`, `5173` đều `reachable=False`. Vì vậy không có kiểm tra HTTP/DB/upload thực nào thành công.

### Môi trường tối thiểu để chạy đầy đủ

1. JDK 21 hoạt động và `JAVA_HOME`/PATH đúng; Maven 3.9.16 hoặc sửa điều kiện khiến wrapper treo.
2. PostgreSQL tại DB cấu hình, có **baseline schema đầy đủ** rồi áp dụng 11 patch SQL theo đúng thứ tự thủ công (hoặc bổ sung migration engine sau này).
3. MinIO tại 9000, credential và bucket; Ollama tại 11434 với đúng tag `qwen3-vl:8b`.
4. `JWT_SECRET`, DB password, MinIO access/secret; không dùng giá trị mẫu.
5. Chạy backend từ `backendqt/backendqt` để đường dẫn mặc định `./tessdata` tìm thấy `vie.traineddata`/`eng.traineddata`.
6. Node 26.8.1/npm 11.19.0 hiện có; `VITE_API_BASE_URL=http://localhost:8081`.

---

## PHẦN C — Sống/chết theo chức năng

| Chức năng | Trạng thái | Bằng chứng file + class/hàm | Thiếu gì / điểm đứt | Tự kiểm chứng trong 2 phút |
|---|---|---|---|---|
| 1. Login/logout/đổi mật khẩu/phân quyền | **CHẠY MỘT PHẦN** | `AuthController.login/register`, `AuthService.login`; `UserController.changePassword`; `SecurityConfig.securityFilterChain`; `JwtAuthenticationFilter.doFilterInternal`; UI `authService.login/logout/changePassword`. Endpoint có `@PreAuthorize`. `DocumentService.load/scopeFor`, `InvoiceService.findAll/load`, `FinancialTransactionService.resolveCompanyId` có lọc company. | Logout chỉ xóa localStorage, không revoke JWT; chưa runtime. `ClassificationService.findByDocumentId/upsert/approve/review/correction` không tự gọi scope company, dựa vào document id — **IDOR tiềm tàng**. `ReconciliationService.find/run/decide` cần kiểm tra scope tương tự. | Bật stack; login từng role; thử ACCOUNTANT công ty A gọi document/classification id của công ty B, kỳ vọng 404/403; đổi password rồi login lại. |
| 2. Thu/chi, liên kết hóa đơn | **CÓ CODE CHƯA KIỂM CHỨNG** | `FinancialTransactionController`; `FinancialTransactionService.create/update/findAll/apply/createFromApprovedDocument`; entity `FinancialTransaction` có `document_id`, `invoice_id`; UI `FinancialTransactions.tsx`, `financialTransactionService`. | DB/backend chưa chạy. Tự tạo từ chứng từ chỉ xảy ra qua `ReviewWorkflowService` khi duyệt và assessment phù hợp. | Tạo giao dịch EXPENSE gắn document/invoice; GET lọc `documentId`; duyệt chứng từ và kiểm tra giao dịch sinh đúng 1 lần. |
| 3. Upload ảnh/PDF, MinIO, tra cứu | **CÓ CODE CHƯA KIỂM CHỨNG** | UI `documentService.uploadDocument/getDocumentPage`; `DocumentController.upload/findPage`; `DocumentService.createFromUpload/uploadToMinio/scopeFor`; `FileValidationService.validate`; query lọc ngày, company, status ở `DocumentController.findAll`/`DocumentService.findPage`. | MinIO/Postgres/backend đều tắt. | Upload PNG/PDF từ `AccountantUpload`; kiểm tra object trong bucket; lọc ngày/status/company và thử chéo công ty. |
| 4. Tiền xử lý chỉnh nghiêng/giảm nhiễu | **CÓ CODE CHƯA KIỂM CHỨNG** | `ImagePreprocessingService.preprocess/detectSkewAngle/medianDenoise`; pipeline thích nghi `OpenCvImageProcessor.process` (CLAHE, bilateral denoise, sharpen, deskew, upscale, threshold); `AdaptiveOcrService`. | Native OpenCV/Tess4J runtime chưa chạy hiện tại. Có hai pipeline tiền xử lý, cần xác nhận pipeline nào được gọi với từng nhánh. | Upload ảnh nghiêng 2–3°, gọi `/process`, xem metadata operations/angle và OCR trước/sau. |
| 5. Trích xuất invoice_data/items | **CÓ CODE CHƯA KIỂM CHỨNG** | `AiDocumentPromptFactory.createWithCategories`; `DocumentAiProcessingService.process`; `AiDocumentResultParser.parse`; `DocumentAiResultPersistenceService.persist/upsertInvoice/replaceItems/persistExtractedFields`; entity `Invoice` → `invoice_data`, `InvoiceItem` → `invoice_items`. | Không Ollama/DB; ảnh thật chưa chạy. | Upload 1 hóa đơn VAT rõ; process; GET `/documents/{id}/invoice`, kiểm tra seller/buyer/date/MST/items/tax/total và DB. |
| 6. Phân loại, gợi ý category/account, sửa tay | **CHẠY MỘT PHẦN** | AI prompt yêu cầu category/account; `DocumentAiResultPersistenceService` lưu classification; `ClassificationService.correction/applyAccountingCategory`; entity/repo `ClassificationCorrection`; UI `ClassificationWorkspace`, `classificationService.correctClassification`. | Build UI sống; backend/DB chưa chạy. Cần xác nhận `ClassificationCorrection` thực sự được ghi ở mọi sửa tay (service có repo nhưng kiểm tra E2E chưa có). | Process chứng từ, mở workspace, sửa category/type, gọi correction, query `classification_corrections`. |
| 7. Đối soát số lượng × đơn giá, thuế, tổng | **CHẠY MỘT PHẦN** | `ReconciliationController`; `ReconciliationService.run`, các phép tính/`sum` và status; UI `ReconciliationWorkspace`, `reconciliationPresentation`. 8 unit test lịch sử + 1 contract test lịch sử; 1 frontend test hiện tại pass. | Backend runtime/DB chưa xác nhận. Các amount có thể null nhờ migration mới nhưng migration không tự chạy. | Chọn invoice có một item cố tình sai; Run; kiểm tra MATCHED/MISMATCH/INSUFFICIENT_DATA và nút decision. |
| 8. Dashboard doanh thu/chi phí/dòng tiền | **CHẠY MỘT PHẦN** | `DashboardController.financial/timeSeries/statistics`; `DashboardService` truy vấn `FinancialTransactionRepository.summarize/expensesByCategory/timeSeries`; UI `AccountantDashboard`, `dashboardService`; Chart.js. | Không hardcode ở đường dùng thật; `mockData.ts/js` tồn tại nhưng **không có import**. DB chưa chạy. | Tạo 1 INCOME + 1 EXPENSE; reload dashboard; đối chiếu tổng, cashflow, category và time-series. |
| 9. Xuất Excel | **CÓ CODE CHƯA KIỂM CHỨNG** | `ExportController.invoices/financialReport`; `ExcelExportService.invoices/financialReport` dùng Apache POI; UI `exportService`. | Chưa gọi runtime. | Bấm xuất 2 loại; mở `.xlsx`, so số dòng/tổng với màn hình và phạm vi công ty. |
| 10. Duyệt, audit, jobs/attempts/retry | **CHẠY MỘT PHẦN** | `DocumentWorkflowController.execute`; `ReviewWorkflowService.execute`; entity/repo `DocumentReview`; `DocumentUploadProcessingService.process/processFailure` có retryable codes; `ProcessingLogService`. | **AuditLog chỉ có entity, không repository/service**. `ProcessingJob` và `ProcessingAttempt` không có repository/service sử dụng nghiệp vụ; retry chỉ biểu diễn lỗi/reprocess thủ công, không thấy scheduler/queue retry. | Submit → start review → approve/reject; query `document_reviews`. Gây Ollama timeout và kiểm tra có job/attempt mới hay không (dự kiến không). |
| 11. Baseline Tesseract vs LLM, Precision/Recall/F1 | **CHƯA CÓ** | Tesseract (`Tess4jOcrService`) và LLM (`OllamaAiProvider`) đều tồn tại; `DocumentAiProcessingService` kết hợp/fallback. | **Không tìm thấy** dataset ground truth, evaluator, confusion matrix, Precision/Recall/F1 theo trường hoặc báo cáo so sánh baseline. | Không thể kiểm trong 2 phút; cần bộ hóa đơn gán nhãn + script evaluation. |

---

## PHẦN D — “Xác sống”

1. **Mock bị bỏ:** `quan tri/src/services/mockData.ts` và `.js` tạo `mockDocuments`, `mockDashboardStats`, nhưng `rg` không tìm thấy import bên ngoài hai file. Không ảnh hưởng dashboard hiện tại nhưng là code chết.
2. **Audit log chỉ là vỏ:** `entity/AuditLog.java` không được class Java nào khác tham chiếu; không có `AuditLogRepository`/service/controller.
3. **Processing job/attempt không vận hành:** `entity/ProcessingJob.java` và `ProcessingAttempt.java` chỉ liên hệ entity; không có repository tương ứng, không scheduler/worker. `ProcessingLog` có service/repository nhưng không biến jobs thành hàng đợi thật.
4. **AccountingEntry gần như mồ côi:** `entity/AccountingEntry.java` + `AccountingEntryRepository.java`; không tìm thấy controller/service nghiệp vụ sử dụng để sinh bút toán.
5. **Migrations trông đúng chỗ nhưng không sống:** 11 file `src/main/resources/db/migration/*.sql` không được Flyway/Liquibase tải vì `pom.xml` không có dependency. `ddl-auto=validate` cũng không tạo/cập nhật schema.
6. **Catch rỗng nuốt lỗi:** `RolePermissionInitializer.run` có bốn `catch (DataAccessException ignored) {}` ở dòng 32/35/38/41; seed role/permission có thể thất bại im lặng.
7. **Raw AI test có thể lộ dữ liệu:** `AiTestController.test` trả `AiProviderResponse` gồm `rawResponse`; UI `AiTest.tsx` dành cho nhiều role (`ADMIN/ACCOUNTANT/EMPLOYEE/USER`). Đây không phải mock nhưng là endpoint debug không nên coi là tính năng production.
8. **Endpoint không thấy frontend gọi:** `CompanyController.findAll`; nhiều CRUD `InvoiceController.create/list/get/delete`; `DocumentController.create/uploadNewVersion/updateOcr/delete/download`; `AiTestController.connectivity`. Có thể dùng Swagger, nhưng trong UI hiện tại **Không tìm thấy** call tương ứng (ngoại trừ invoice update/get-by-document).
9. **UI thiếu logic quản trị DB setup:** Catalog UI gọi document types/categories nhưng schema/seed ban đầu không có đường bootstrap đáng tin cậy vì migration engine thiếu.
10. **TODO/FIXME:** Không tìm thấy TODO/FIXME đáng kể trong source application. Các `return null` được xem xét chủ yếu là nhánh parse/optional hợp lệ, không đủ bằng chứng gọi là giả.

---

## PHẦN E — Đối chiếu DB

### Phát hiện quyết định

- File **`dd.sql` không tồn tại** trong repo: **Không tìm thấy**. Vì vậy không thể thực hiện đối chiếu chuẩn “dd.sql ↔ entity” hay xác nhận CHECK constraints/seed của baseline.
- 11 SQL hiện có đều là **ALTER/patch**, không chứa baseline `CREATE TABLE` đầy đủ. Không có Flyway/Liquibase trong `pom.xml`, nên chúng không tự chạy.
- `spring.jpa.hibernate.ddl-auto=validate`: backend chỉ sống nếu database đã được tạo ngoài repo và khớp hoàn toàn.

### Bảng có entity/repository và luồng dùng rõ

`users`, `companies`, `roles`, `permissions`, `permission_groups`, `role_permissions`, `user_roles`, `documents`, `document_versions`, `document_types`, `ocr_results`, `invoice_data`, `invoice_items`, `extracted_fields`, `field_corrections`, `ai_classifications`, `classification_corrections`, `document_reviews`, `financial_transactions`, `accounting_categories`, `reconciliations`, `reconciliation_batches`, `processing_logs` — bằng chứng ở `entity/*.java`, `repository/*.java` và service tương ứng.

### Bảng/code bị bỏ hoặc không có luồng sống

- `audit_logs`: `AuditLog` entity duy nhất; không repository/service.
- `processing_jobs`, `processing_attempts`: entity có, repository/service/worker **Không tìm thấy**.
- `accounting_entries`: entity + repository, service/controller tạo bút toán **Không tìm thấy**.

### Lệch/điểm không thể xác nhận

- Migration `20261013_01_nullable_reconciliation_amounts.sql` bỏ NOT NULL cho ba amount, khớp entity `Reconciliation` hiện không `nullable=false`; nhưng nếu chưa chạy migration, Hibernate validate/runtime insert có thể lệch.
- `Invoice` map `subtotal` → cột `total_before_tax`, `vatAmount` → `tax_amount`; cần schema baseline để xác nhận, nhưng `dd.sql` không có.
- `Classification` map bảng `ai_classifications`; migration `20261006_02_accounting_suggestion.sql` thêm category/account. Nếu patch chưa áp dụng, startup validate sẽ fail.
- CHECK `status`, `role`: không thể đối chiếu vì baseline CHECK **Không tìm thấy**. Enum code: `DocumentStatus`, `ReviewStatus`, `ClassificationStatus`, `UserRole`, `UserStatus`, `ReviewType` dưới `entity/`.

### Seed

- Roles/permissions: có code seed `RolePermissionInitializer.run/seedGroup/seedPermission/seedRole`; nhưng bốn lỗi DB bị nuốt.
- Admin demo: `DemoAdminInitializer.run`, mặc định disabled.
- `document_types`: initializer/seed đáng tin cậy **Không tìm thấy**.
- `accounting_categories`: `RolePermissionInitializer` có thao tác tương thích/seed liên quan quyền, nhưng baseline catalog nghiệp vụ theo công ty đầy đủ **Không tìm thấy**.
- Seed SQL cho roles/permissions/document_types/accounting_categories: **Không tìm thấy**.

---

## PHẦN F — Luồng một hóa đơn end-to-end

### Luồng code

1. UI `documentService.uploadDocument` → `POST /api/v1/documents/upload`.
2. `DocumentController.upload` → `DocumentService.createFromUpload`: validate file, tạo object key theo company, `uploadToMinio`, lưu `Document`, đăng ký rollback cleanup.
3. `DocumentUploadProcessingService.process` → `DocumentAiProcessingService.process`.
4. PDF: `PdfImageConversionService.convert` dùng PDFBox, **render từng trang thành PNG**, không gửi PDF thẳng Ollama. Ảnh được quality analyze/preprocess qua `AdaptiveOcrService`/`OpenCvImageProcessor`; pipeline phụ `ImagePreprocessingService` cũng tồn tại.
5. `Tess4jOcrService.recognize` tạo text + word bbox; `OcrLayoutReconstructor`; `OcrTextNormalizer` giới hạn prompt.
6. `AiDocumentPromptFactory.createWithCategories` tạo prompt; `OllamaAiProvider.analyzeText` ưu tiên text-first hoặc `analyzeImage` vision fallback; `AiDocumentResultParser.parse` parse JSON; `AiDocumentResultValidator.validate` kiểm schema/range.
7. `DocumentAiResultPersistenceService.persist` ghi `OCRResult`, `Invoice`, `InvoiceItem`, `ExtractedField`, `Classification`, cập nhật Document role/direction/assessment.
8. `ClassificationService` cho sửa/approve/review; `ReconciliationService.run` tính kiểm tra; `ReviewWorkflowService.execute` duyệt.
9. Khi APPROVE, `FinancialTransactionService.createFromApprovedDocument` có thể sinh transaction theo assessment; `DashboardService` aggregate repository và UI vẽ Chart.js.

### Điểm đứt thực tế

Luồng đứt **ngay trước bước 1 runtime**: backend 8081, PostgreSQL 5432, MinIO 9000, Ollama 11434 đều không chạy. Ngoài ra DB không có baseline/migration tự động. Vì vậy không có bằng chứng một hóa đơn thật đã đi hết chuỗi trong phiên kiểm toán.

### Prompt gửi Ollama và dấu tiếng Việt

Prompt nguyên văn nằm tại `AiDocumentPromptFactory.createWithCategories`. Đoạn quyết định về tiếng Việt (trích nguyên văn ngắn, đủ kiểm toán):

> Preserve Vietnamese Unicode and diacritics. Do not transliterate Vietnamese text to ASCII. Preserve personal names, company names and addresses exactly as written in the document. Do not invent missing diacritics when the source is uncertain.

Và:

> Human-readable reasons, warnings and extra-field labels must be returned in Vietnamese. JSON property names and enum values remain in English. Never translate source document values.

Prompt còn yêu cầu JSON-only, không đoán dữ liệu, giữ boundary `[PAGE n]`, trích mọi item đúng một lần, thuế dạng fraction, và schema đầy đủ seller/buyer/date/MST/items/totals. Toàn bộ literal xem trong `backendqt/backendqt/src/main/java/com/example/invoice/ai/AiDocumentPromptFactory.java`, hàm `createWithCategories`.

### PDF/DPI

- `PdfImageConversionService.convert`: `PDFRenderer.renderImageWithDPI(index, properties.getPdf().getDpi())`, xuất PNG từng trang; **không gửi PDF trực tiếp**.
- `Tess4jOcrService.recognize`: set Tesseract `user_defined_dpi=300` để chống metadata DPI sai; đây không đổi bitmap.
- Giá trị PDF DPI được giữ trong `AiProperties.Pdf`; cần đọc default/config khi demo và log lại để chứng minh chính xác trên môi trường chạy.

### Các chỗ bỏ dấu/ASCII hóa

- `DocumentAiResultPersistenceService.normalizeKey` (dòng khoảng 473): `Normalizer.NFD`, `.replaceAll("\\p{M}+", "")`, đổi `đ/Đ`, bỏ non-alnum. Dùng để chuẩn hóa **key/tên trường so sánh**, không phải cố ý ghi đè giá trị source; vẫn là điểm cần regression test.
- `OcrFieldLocator.normalize` (dòng khoảng 129): NFD + bỏ `\p{M}` + chỉ giữ `[A-Za-z0-9]`; dùng matching vị trí, không trả lại giá trị invoice.
- `DocumentAiResultPersistenceService.stableExtraFieldName` dùng `replaceAll("[^A-Za-z0-9]+", " ")` để tạo tên ổn định.
- `OcrTextNormalizer.normalize` chỉ bỏ control/repeated chars/space; **không bỏ dấu tiếng Việt**.
- Không tìm thấy `removeAccents` khác trong application source.

### Charset/log raw response

- Source/config khai báo UTF-8; `Tess4jOcrService` set `jna.encoding=UTF-8`; OkHttp JSON media type là `application/json` (không ghi explicit charset, JSON mặc định UTF-8 theo thực tế codec).
- `OllamaAiProvider` giữ toàn bộ raw JSON response trong DTO trả về service; INFO log không ghi raw body, DEBUG ghi preview nội dung response/thinking. `AiTestController.test` trả raw response ra client. Cần coi đây là rủi ro dữ liệu nhạy cảm.

---

## PHẦN G — Top 10 rủi ro

| # | Mức | Rủi ro | Bằng chứng |
|---|---|---|---|
| 1 | **Nghiêm trọng** | DB không thể tái tạo từ repo: thiếu `dd.sql`/baseline, migration không có engine, Hibernate chỉ validate. | `pom.xml`, `application.yml`, `db/migration/*` |
| 2 | **Nghiêm trọng** | IDOR/cross-company ở các service nhận document id nếu không gọi `DocumentService.load`/scope; classification/reconciliation là điểm cần pentest đầu tiên. | `ClassificationService.findByDocumentId/loadByDocumentId`; `ReconciliationService.find/run/decide`; guards chỉ theo role ở controller |
| 3 | **Cao** | Secret thật có thể nằm trong `.env` trong workspace. Không in giá trị; vị trí phát hiện: `.env` dòng 2–5, 7–8. | `.env` (giá trị đã redacted trong kiểm toán) |
| 4 | **Cao** | Runtime chưa tái lập: Java launcher lỗi, DB/MinIO/Ollama/Docker tắt; không có E2E xanh. | kết quả Phần B; `scripts/start.ps1` |
| 5 | **Cao** | Upload file phụ thuộc validation nhưng vẫn xử lý PDF/ảnh phức tạp; nguy cơ decompression bomb/native parser, giới hạn 10 MB chưa đủ chống pixel/page bomb. | `FileValidationService.validate`; `application.yml` multipart; `PdfImageConversionService.convert` max pages |
| 6 | **Cao** | Audit trail giả: có entity nhưng không ghi audit; khó chứng minh ai sửa/xóa/duyệt. | `AuditLog.java`; không có repository/service |
| 7 | **Cao** | Retry/job giả: processing jobs/attempts không có repository/worker; lỗi AI có thể cần retry tay và mất khả năng quan sát. | `ProcessingJob.java`, `ProcessingAttempt.java`, `DocumentUploadProcessingService.processFailure` |
| 8 | **Trung bình-cao** | Dữ liệu hóa đơn/raw AI có thể lộ qua debug preview và AI test response; endpoint test mở cho EMPLOYEE/USER. | `OllamaAiProvider.generate`; `AiTestController.test`; `AiProviderResponse.rawResponse` |
| 9 | **Trung bình-cao** | Lỗi seed bị nuốt; hệ thống có thể khởi động thiếu role/permission mà không có log rõ. | `RolePermissionInitializer.run`, bốn empty catch |
| 10 | **Trung bình** | Hiệu năng: request timeout 120s, context 8192/output 3072; vision/OCR nhiều trang + OpenCV tuần tự; bundle frontend >1 MB. Không có bằng chứng “95 giây/lượt” trong source/log hiện tại, nên con số đó **chưa xác nhận**. `think=false` đã giảm rủi ro thinking chậm. | `application.yml`; `OllamaAiProvider.generate`; `PdfImageConversionService.convert`; log build Vite |

SQL injection: repository chủ yếu JPA/parameterized `@Query`; **không tìm thấy** native SQL ghép chuỗi từ input trong các luồng đã kiểm tra. Điều này không thay thế pentest.

Mất dữ liệu: delete document có code xóa object MinIO (`DocumentService.delete`) trong khi transaction DB và object storage không cùng một transaction; rollback cleanup chỉ được triển khai cho upload. Cần fault-injection trước production.

---

## PHẦN H — Tổng kết

### (a) Điểm hoàn thành ước lượng

**5,25/10 ≈ 53%** theo thang đề cương, không phải % dòng code.

| Hạng mục | Trọng số | Điểm đạt | Lý do |
|---|---:|---:|---|
| Auth/phân quyền/multi-company | 1,0 | 0,65 | Có luồng và guards, nhưng chưa runtime và còn IDOR cần kiểm |
| Upload/lưu trữ/tra cứu | 1,0 | 0,60 | Code đầy đủ, chưa MinIO/DB E2E |
| Tiền xử lý/OCR/PDF | 1,2 | 0,75 | Implementation sâu + traineddata, runtime test chưa xác nhận |
| LLM extraction/persistence | 1,5 | 0,85 | Prompt/parser/validator/persist thật, chưa hóa đơn thật E2E |
| Phân loại/sửa tay | 1,0 | 0,60 | Backend+UI có, chưa DB/E2E |
| Đối soát | 0,8 | 0,50 | Có logic/test source/UI, migration/runtime chưa xác nhận |
| Thu chi/dashboard/export | 1,2 | 0,80 | Query thật, không hardcode; chưa runtime |
| Workflow/audit/retry | 1,0 | 0,35 | Workflow có; audit và jobs/attempts gần như xác sống |
| DB/deploy/reproducibility | 0,7 | 0,15 | Thiếu baseline/migration engine/Docker |
| Đánh giá thuật toán P/R/F1 | 0,6 | 0,00 | Không tìm thấy |
| **Tổng** | **10,0** | **5,25** | Tổng trực tiếp theo trọng số; frontend build/test thành công đã được phản ánh trong các điểm chức năng liên quan. |

### (b) 5 việc nên làm trước, theo thứ tự

1. **Làm DB tái lập được:** đưa baseline schema vào version control, chọn Flyway/Liquibase, sửa thứ tự/version migration, chạy trên DB trắng và `ddl-auto=validate` phải xanh.
2. **Dựng một smoke E2E bắt buộc:** Postgres + MinIO + Ollama + backend + frontend; upload một PDF tiếng Việt → invoice/items → classification → reconciliation → approve → transaction → dashboard/export.
3. **Khóa tenant boundary:** mọi service theo document/invoice/classification/reconciliation phải load theo `company_id`; thêm integration tests công ty A không đọc/sửa id công ty B.
4. **Biến workflow vận hành thật:** audit log cho login/upload/sửa/duyệt/xóa; repository/worker/retry cho processing_jobs/attempts hoặc xóa claim khỏi đề cương nếu không làm.
5. **Tạo bộ đánh giá:** 30–50 hóa đơn gán nhãn theo trường; chạy Tesseract baseline và Tesseract+LLM; báo Precision/Recall/F1 từng trường, exact match/NED cho tiếng Việt, latency P50/P95 và error rate.

### (c) Những gì phải demo được trước hội đồng

1. Login ba vai trò; chứng minh 403/404 khi sai quyền và không xem chéo công ty.
2. Upload một ảnh và một PDF nhiều trang; mở preview; chỉ ra object MinIO và bản ghi DB.
3. Hiển thị trước/sau preprocessing, OCR bbox, tiếng Việt có dấu, prompt/model config không lộ secret.
4. Invoice_data + invoice_items đúng seller/buyer/date/MST/hàng/thuế/tổng; sửa tay và xem lịch sử correction.
5. Chạy đối soát với một hóa đơn đúng và một hóa đơn cố tình sai; duyệt; sinh giao dịch thu/chi.
6. Dashboard thay đổi theo dữ liệu vừa duyệt và xuất Excel mở được.
7. Cho xem audit log, một failure Ollama có retry/attempt rõ, không mất file/bản ghi.
8. Trình bày bảng đo Tesseract vs LLM theo field với Precision/Recall/F1 và latency; không dùng screenshot/hardcode.

## Phán quyết cuối

**Frontend: SỐNG ở mức build/test. Backend: CÓ NHIỀU CODE THẬT nhưng CHƯA KIỂM CHỨNG runtime hiện tại. Hệ thống end-to-end: CHƯA SỐNG.** Nút thắt không phải thiếu giao diện mà là khả năng tái tạo DB, môi trường Java/dịch vụ ngoài, tenant security, audit/retry và bằng chứng định lượng thuật toán.

---

# PHỤ LỤC KIỂM THỬ RUNTIME THỰC TẾ — 13/10/2026

Phụ lục này thay thế các nhận định “chưa runtime” ở phần trên. Không sửa source. Dữ liệu duy nhất được tạo qua API là user `rt_20261013125414` và document `256`; không in mật khẩu, JWT hay secret.

## 1. Môi trường thực sự đã chạy

| Thành phần | Kết quả runtime | Bằng chứng code/cấu hình |
|---|---|---|
| PostgreSQL | **SỐNG** — backend kết nối PostgreSQL **18.6**, Hikari khởi tạo thành công; DB có 27 bảng nghiệp vụ | `backendqt/backendqt/src/main/resources/application.yml`; JPA repositories dưới `backendqt/backendqt/src/main/java/com/example/invoice/repository` |
| MinIO | **SỐNG trên host**, health `localhost:9000` trả 200; upload/download thực tế thành công | `backendqt/backendqt/src/main/java/com/example/invoice/service/StorageService.java`; `DocumentService.download()` |
| Ollama | **SỐNG trên host** với `qwen3-vl:8b`; test thật hoàn thành trong 25,877 giây | `backendqt/backendqt/src/test/java/com/example/invoice/ai/OllamaTextRuntimeTest.java`; `OllamaAiProvider.analyze()` |
| Backend | **SỐNG** — Spring Boot 3.5.6/Java 21, start 17,03 giây, port 8081; Swagger 200; OpenAPI 43.054 byte | `InvoiceApplication`; `pom.xml` |
| Frontend | **SỐNG** — Vite 8.3.0 ready 586 ms, HTTP 200, trang có Vite client | `quan tri/package.json` |
| Docker Compose test | **CHƯA SỐNG trọn stack** — container Postgres/Ollama chỉ ở trạng thái `Created`, không bind được vì host đã dùng 5432/11434; image MinIO Docker Hub không tồn tại và Quay trả `401 UNAUTHORIZED` | `compose.runtime-test.yml` |

Lưu ý: lần kiểm thử chức năng này dùng chính PostgreSQL/MinIO/Ollama đang chạy trên host vì đó là runtime hiện có. Docker daemon hoạt động, nhưng compose mới không thể chiếm các cổng đang dùng.

## 2. Build và test đã chạy

| Lệnh | Kết quả |
|---|---|
| `mvnw.cmd package -DskipTests` | **PASS**, tạo JAR |
| `mvnw.cmd test` với DB thật và Tess4J bật | **FAIL: 159 tests, 2 failures, 0 errors, 1 skipped** |
| `mvnw.cmd -Dtest=OllamaTextRuntimeTest test` | **PASS 1/1**, Ollama thật; 25,877 giây phản hồi |
| `npm run build` | **PASS**, 1.994 modules; cảnh báo bundle JS 1.097,44 kB > 500 kB |
| `npm test -- --run` | **PASS 15/15** |
| `npm run dev -- --host 127.0.0.1 --port 5173` | **PASS**, HTTP 200 |

Hai lỗi backend nguyên văn cốt lõi:

1. `RuntimePersistenceVerificationTest.actualPostgresPersistsOcrAiStructuresAndHandlesReprocessSafely`, dòng 76: `expected: <PROCESSED> but was: <NEED_REVIEW>`.
2. `Tess4jOcrServiceTest.recognizesVietnameseDiacriticsWithConfiguredDefaultLanguage`, dòng 65: `expected: <UTF8> but was: <UTF-8>`.

OCR tiếng Việt runtime còn lỗi dấu thật, ví dụ output: `H�A ??N GI� TR? GIA T?NG`, `C�NG TY CO PHAN ANH D??NG`, `??a ch?: 123 Nguy?n Hu?...`. Trong khi test PDF số/tiếng Anh, PDF scan, PDF digital và PDF hỗn hợp đều pass. Bằng chứng: `Tess4jOcrService.recognize()`, `DocumentTextExtractionService.extract()`, `DocumentTextExtractionRuntimeTest`.

## 3. Ma trận chức năng hộp đen

| Chức năng | Runtime | Bằng chứng chạy và bằng chứng code | Kết luận |
|---|---|---|---|
| Swagger/OpenAPI | HTTP 200; OpenAPI 43.054 byte | `InvoiceApplication`; cấu hình springdoc | **SỐNG** |
| JWT chặn request | `GET /api/v1/documents` không token → 401 | security config; `DocumentController` | **SỐNG** |
| Đăng ký | `POST /auth/register` tạo user ID 45 | `AuthController.register()`, `AuthService.register()` | **SỐNG** |
| Đăng nhập | đúng → token; sai → 401 | `AuthController.login()`, `AuthService.login()` | **SỐNG** |
| Đổi mật khẩu | đổi thành công; mật khẩu cũ → 401; mật khẩu mới login được | `UserController.changePassword()`, `UserService.changePassword()` | **SỐNG** |
| Vai trò mặc định | user mới nhận `EMPLOYEE` | `AuthService.register()` | **SỐNG** |
| Phân quyền endpoint | EMPLOYEE xem documents/types/dashboard statistics được; financial dashboard, transactions, categories, companies, roles, permissions, users, Ollama connectivity đều → 403 | các controller và method-security tương ứng | **SỐNG đối với deny/allow đã thử**; ADMIN/ACCOUNTANT chưa runtime vì không có credential hợp lệ |
| Danh sách chứng từ | `GET /documents` → 200 | `DocumentController.list()` | **SỐNG** |
| Upload PDF | lần đầu client gửi octet-stream → 400; gửi đúng `application/pdf` → **201**, document ID 256 | `DocumentController.upload()`, `DocumentService.create()` | **SỐNG** nhưng OpenAPI khai báo media type gây client dễ gửi sai |
| Lưu/đọc MinIO | download và preview ID 256 → 200 `application/pdf`, đúng 1.097 byte | `StorageService`, `DocumentService.download()`, `DocumentController.download()/preview()` | **SỐNG** |
| Pipeline sau upload | upload trả `processingStatus=FAILED`, `AI_PROCESSING_FAILED`, `CATEGORY_COMPANY_MISSING: chứng từ chưa thuộc doanh nghiệp` | `DocumentAiProcessingService` dòng 107 | **ĐỨT**: đăng ký user không gắn company nhưng upload được; pipeline không thể tiếp tục |
| OCR của document vừa upload | `GET /documents/256/ocr` → 404 | `DocumentController.getOcr()` và OCR services | **CHƯA TẠO** vì pipeline dừng trước OCR |
| Invoice data/items | `GET /documents/256/invoice` → 404 | invoice controller/service/entity | **CHƯA TẠO** vì pipeline dừng |
| Extracted fields | 200, mảng rỗng | extracted-field controller/service | **CHẠY nhưng không có dữ liệu** |
| Classification | 404 | `ClassificationService` | **CHƯA TẠO** vì pipeline dừng |
| Reprocess | 400 `CATEGORY_COMPANY_MISSING` | `DocumentController.reprocess()`, `DocumentAiProcessingService` | **ĐỨT có kiểm soát**, retry không giải quyết thiếu company |
| Workflow submit | 400: chỉ gửi được document UPLOADED/PENDING; document đã FAILED | `ReviewWorkflowService.act()` | **Guard SỐNG**, luồng duyệt E2E chưa đạt |
| Đối soát | EMPLOYEE → 403 | reconciliation controller/service | **Phân quyền SỐNG**, thuật toán chưa E2E trong lần upload này |
| Thu chi, dashboard tài chính, export | EMPLOYEE bị 403 đúng quyền | `FinancialTransactionController`, `DashboardController`, export controller | **Chưa kiểm chứng happy-path** do thiếu ADMIN/ACCOUNTANT credential |
| Ollama extraction | test runtime riêng pass, JSON invoice number/amount đúng; response rỗng nhưng JSON nằm trong `thinking`, fallback đọc được | `OllamaAiProvider.analyze()`, `OllamaTextRuntimeTest` | **SỐNG riêng lẻ**, chưa nối được với upload do company |
| OCR/PDF | 5/5 test runtime PDF pass; OCR số/Anh confidence 0,9439 | `DocumentTextExtractionRuntimeTest` | **SỐNG một phần**; tiếng Việt lỗi encoding/dấu |

## 4. Điểm đứt end-to-end đã chứng minh

```text
Register EMPLOYEE (company_id = null)       PASS
  -> JWT/login/password                     PASS
  -> upload PDF                             PASS (201)
  -> MinIO put + download/preview           PASS
  -> xác định company                       FAIL: CATEGORY_COMPANY_MISSING
  -> OCR                                    KHÔNG CHẠY cho document này
  -> Ollama/parse/persist invoice/items     KHÔNG CHẠY
  -> classification/reconciliation/review  KHÔNG CHẠY
  -> transaction/dashboard/export           KHÔNG THỂ chứng minh E2E
```

Đây là lỗi thiết kế runtime quan trọng: `AuthService.register()` tạo EMPLOYEE không có company; `DocumentController.upload()` vẫn cho upload; `DocumentAiProcessingService` chỉ phát hiện thiếu company sau khi file đã được lưu và document đã tạo. Kết quả là file/bản ghi ở trạng thái FAILED và retry tiếp tục fail.

## 5. Phán quyết runtime cập nhật

- **Đã hoàn thành/chạy thật:** backend startup, PostgreSQL persistence cơ bản, frontend serve/build/test, JWT 401, đăng ký, login, đổi mật khẩu, role EMPLOYEE, kiểm tra 403, upload đúng MIME, lưu MinIO, download/preview, PDF extraction tests, Ollama test độc lập.
- **Chạy một phần:** OCR (Anh/số/PDF tốt nhưng tiếng Việt hỏng dấu), phân quyền (đã chứng minh EMPLOYEE; chưa ADMIN/ACCOUNTANT), workflow và reconciliation (guard/403 hoạt động nhưng chưa happy-path).
- **Lỗi runtime chặn demo:** user mới không có company nhưng được upload; pipeline chết tại `CATEGORY_COMPANY_MISSING`; vì vậy chưa có bằng chứng E2E upload → OCR → invoice/items → classification → reconciliation → approve → dashboard/export.
- **Hạ tầng Docker chưa tái lập:** compose không chạy trọn stack vì xung đột cổng với dịch vụ host và registry MinIO không kéo được.

Điểm cập nhật thận trọng: **6,1/10 (61%)**. Tăng so với 5,25 vì đã chứng minh runtime auth, storage, DB, OCR/PDF và Ollama riêng lẻ; không tăng các điểm E2E nghiệp vụ vì luồng hóa đơn thực tế vẫn đứt ngay sau lưu file.
