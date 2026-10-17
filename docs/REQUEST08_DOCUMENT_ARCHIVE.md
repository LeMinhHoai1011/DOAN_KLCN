# Yêu cầu 08 — File, phiên bản dữ liệu và lịch sử chứng từ

Ngày: 2026-10-17. Branch dev/ai. Git đã có nhiều thay đổi chưa commit từ các yêu cầu trước; giữ nguyên invoice_1/ và các thay đổi đó. Không chạy Spring Boot trên PostgreSQL, không kết nối/ghi PostgreSQL thật, không thực hiện thao tác trên MinIO thật, không migration/commit/push.

## Khảo sát trước khi sửa

- `Document` đã có tên file, MIME, dung lượng, uploadedBy, createdAt, object_key, company, processing/review status. File không nằm trong DB.
- `StorageService` chỉ sinh UUID object key và sanitize tên; các thao tác MinIO thực nằm trong DocumentService. Không có MinioStorageService riêng.
- `DocumentVersion` / `document_versions` đã có ID, document, versionNumber, objectKey, tên, MIME, dung lượng, createdBy/createdAt. Upload lần đầu tạo version 1, upload phiên bản mới sinh UUID khác, giữ object cũ. Chưa có GET versions hoặc tải file theo version ID.
- File download/preview hiện có kiểm tra permission ở controller và scope qua DocumentService.load. DocumentResponse đã che filePath bằng null. Tuy nhiên API tạo metadata nhận object key theo prefix Company; API update cho đổi filePath/MIME/size trực tiếp. Prefix/object key không chứng minh người dùng được truy cập object. DELETE DocumentService trước đây xóa object/versions/metadata. Người dùng đã xác nhận chặn các đường này trong lượt triển khai này.
- `OCRResult` / `Classification` hiện được cập nhật bản gần nhất; `Invoice`/items và AI ExtractedField là projection hiện tại. FieldCorrection giữ giá trị sửa; dữ liệu chưa sửa có thể bị thay khi reprocess. File version không phải extraction version.
- `DocumentReview` đã lưu người duyệt, thời gian, hành động/note. ProcessingJob/Attempt có lượt/lần thử và log; log cũ không có actor hoặc liên kết file version đầy đủ. Không tự suy diễn actor/model/AI output cũ.
- `AuditLog` / `audit_logs` đã có actor, Company, action, entity type/id, old/new text và thời điểm. Request 06 đã ghi audit create/update/cancel giao dịch. Không có API quản lý/xóa audit log thông thường.
- React và Flutter trong repo dùng multipart `/documents/upload`; Flutter có multipart `/documents/{id}/versions`. Không tìm thấy client trong hai mã nguồn gọi POST `/documents` bằng object key hoặc DELETE document. Không sửa Flutter. Client bên ngoài repo chưa xác minh.

## Thiết kế và thay đổi thực tế

### File và lưu trữ

- Giữ API multipart upload hiện tại. SHA-256 được tính từ stream server khi upload và ghi user metadata của **object mới**, không thêm cột/checksum giả. File vẫn ở MinIO; không ghi byte file vào DB.
- Không thay metadata/object key của version cũ. Phiên bản mới dùng Company của chứng từ để sinh prefix, bao gồm trường hợp ADMIN đang làm việc trên chứng từ của Company khác.
- File mới của chứng từ review PENDING đưa processing status về UPLOADED để không duyệt nhầm dữ liệu AI của file trước. REJECTED/CORRECTED không tự chuyển về PENDING; vẫn phải qua workflow gửi lại. Không tự chạy AI sau upload phiên bản.
- POST `/documents` tạo bằng object reference bị chặn với `FILE_UPLOAD_REQUIRED` (HTTP 400). PUT metadata từ chối filePath hoặc thay MIME/size qua `FILE_VERSION_REQUIRED`. Rename metadata vẫn theo quyền và được audit; metadata của file version gốc không bị sửa theo rename.
- DELETE document và DELETE dữ liệu Invoice bị chặn với `DOCUMENT_RETENTION_REQUIRED`. Không chạy removeObject/deleteAll/delete của dữ liệu lưu trữ qua các đường này. Cleanup object UUID **mới chưa commit** khi transaction upload rollback vẫn được giữ, không áp dụng cho object đã tồn tại hay version đã commit.
- File legacy có object nhưng không có version record: không tự tạo số 1 hoặc gán file mới là gốc. Upload thay thế bị chặn bằng `LEGACY_FILE_VERSION_REVIEW_REQUIRED`; cần khảo sát và phê duyệt baseline riêng. GET storage báo original=null nếu không có version 1.
- Metadata kho được kiểm tra bằng statObject, không gọi makeBucket/putObject khi xem. AVAILABLE, MISSING, UNAVAILABLE, NOT_LINKED phân biệt; so MIME/size với DB, không tự sửa khi lệch. SHA-256 chỉ trả khi có metadata hợp lệ; ETag là mục riêng, không được coi là checksum. MinIO versionId nếu có không bị nhầm với DocumentVersion.versionNumber.

### Bốn loại mốc lịch sử

1. **File version**: `document_versions`, số phiên bản và object riêng.
2. **Dữ liệu trích xuất/sửa**: snapshot JSON schemaVersion=1 ở audit_logs, entityType `DOCUMENT_SNAPSHOT_V1`, ID audit là ID snapshot, không phải số phiên bản file.
3. **Lần OCR/AI**: ProcessingJob/Attempt/Log hiện có; snapshot giữ latestJob ID/status và model/engine, cùng fileVersionId tại thời điểm ghi. Không tuyên bố log lịch sử luôn có liên kết chính xác đến một file version nếu chưa ghi từ trước.
4. **Dữ liệu được xác nhận**: snapshot hành động WORKFLOW_APPROVE/CLASSIFICATION_APPROVE tương ứng, giữ actor/time. APPROVE vẫn theo workflow hiện tại, không tạo thanh toán và không mở quyền reprocess chứng từ đã khóa.

`DocumentAuditService` chỉ append, chạy `MANDATORY` trong transaction nghiệp vụ. Ghi before **trước** thay đổi và after sau flush để có ID/cascade; không tự REQUIRES_NEW cho audit. Audit lỗi làm rollback mutation. Snapshot scalar/DTO không serialize JPA User/Company hoặc raw provider response, credential, object key/bucket/path. Nội dung gồm thông tin chứng từ, version ID, dữ liệu invoice/items, OCR text/layout/engine, classification, extracted fields + effective correction, kết quả đối soát và latest job. Với AI_EXTRACT, aiProposal lưu riêng kết quả đề xuất có cấu trúc (loại rawResponse), kể cả khi dữ liệu kế toán được giữ nguyên. Giá trị decimal trong snapshot/API lịch sử được truyền dạng chuỗi để không mất độ chính xác tiền khi vượt giới hạn JavaScript Number; API nghiệp vụ hiện tại không đổi. Đây là text nghiệp vụ, có thể nhạy cảm; chỉ đọc qua API đã scope.

Các điểm ghi mới: upload, upload version, metadata/type edit, OCR extraction/edit, AI persistence và unresolved type, invoice create/update, field correction, classification upsert/correction/approve/review, reconciliation run/decision, workflow actions, start/reprocess/completed/failed processing. Transaction create/update/cancel tiếp tục dùng audit riêng từ Request 06 và được tổng hợp khi có ACCOUNTING_VIEW/ADMIN.

Snapshot bảo toàn dữ liệu từ thời điểm triển khai, dù projection hiện tại tiếp tục cập nhật/thay các dòng AI chưa sửa theo pipeline hiện có. Không backfill, không dựng lại kết quả đã bị ghi đè. Với document cũ chưa có UPLOAD snapshot, API báo `legacyCoverageIncomplete=true`. Correction và review cũ còn lưu vẫn được đọc theo bằng chứng đó.

## API mới và bảo mật

Tất cả bắt đầu bằng `/api/v1/documents/{id}`:

| Method/path | Quyền / hành vi |
|---|---|
| GET `/storage` | DOCUMENT_VIEW hoặc ADMIN; scope trước stat MinIO; không trả object key. |
| GET `/versions` | DOCUMENT_VIEW hoặc ADMIN; DTO file metadata, actor ID/time, original/current flags. |
| GET `/versions/{versionId}/preview` | DOCUMENT_VIEW hoặc ADMIN; version phải thuộc đúng document đã scope. |
| GET `/versions/{versionId}/download` | DOCUMENT_DOWNLOAD hoặc ADMIN; cùng kiểm tra document/version. |
| GET `/history` | DOCUMENT_VIEW hoặc ADMIN; snapshot metadata, review, correction, log an toàn; payment history thêm khi ACCOUNTING_VIEW/ADMIN. |
| GET `/history/{eventId}?source=SNAPSHOT|FIELD_CORRECTION|PAYMENT` | Scope document trước lookup; đúng document và Company, đúng source; before/after từ bản đã lưu, không lấy current data thay thế. |

Snapshot không có endpoint update/delete/restore. Method không hỗ trợ trả 405 bằng handler rõ ràng thay vì lỗi nội bộ. Permission ở controller và archive service; scope kế thừa DocumentService: ADMIN active, ACCOUNTANT theo Company, EMPLOYEE/USER theo uploader lịch sử. Không thay Company/role dữ liệu cũ. Biết document/version/audit ID không bỏ qua được quyền; file tải qua backend, không đưa presigned URL/object key làm capability.

Preview/download hiện tại và version mới đều có Content-Disposition UTF-8 an toàn, cache no-store và nosniff. Chỉ MIME xem trực tiếp được cho phép; MIME legacy không an toàn dùng attachment/octet-stream; CSP sandbox. Quyền xem file vốn cho phép nhận nội dung để trình duyệt hiển thị, không phải cơ chế DRM ngăn lưu nội dung đã xem.

## Frontend

Giữ trang chi tiết, thêm tab **File & lưu trữ**, **Phiên bản**, **Lịch sử xử lý**. Hiển thị file gốc/version, tên/MIME/size/actor/time, trạng thái kho/SHA/ETag, quyền tải trả từ backend. Xem/tải file qua version ID, không dùng object key. Hiển thị lịch sử/snapshot và so sánh trường trước/sau; ảnh/PDF xem trong panel an toàn. Đọc lại processing-history hiện có, giữ retry có giới hạn và workflow. Không thêm nút restore/delete lịch sử hoặc tự chạy reprocess.

Lỗi API/quyền và danh sách trống phân biệt. Null actor/time/checksum/file gốc hiển thị chưa ghi nhận, không tự lấp bằng user hiện tại hoặc 0. Legacy snapshot thiếu được cảnh báo. Không render layout JSON dài thành bảng diff nhưng layout vẫn được giữ trong snapshot API.

## File thay đổi

- Mới: DocumentAuditService, DocumentArchiveService, DocumentArchive DTO, DocumentArchiveController, DocumentArchiveIntegrationTest.
- Sửa: DocumentService; OCRResultService; DocumentAiResultPersistenceService; InvoiceService; ClassificationService; ExtractedFieldCorrectionService; ReviewWorkflowService; ReconciliationService; PipelineExecutionService — thêm snapshot trong transaction và các chốt bảo vệ nêu trên.
- Sửa: DocumentController (header file an toàn); DocumentVersionRepository (lookup theo document + version); AuditLogRepository (đọc audit Company); GlobalExceptionHandler (405).
- Test tương thích: DocumentAssessmentPersistenceTest thêm mock recorder cho test slice không khởi tạo service audit thực.
- Frontend: documentArchiveService.ts, DocumentArchivePanel.tsx, documentArchivePresentation.ts/test, AccountantDocumentDetail.tsx, package.json; OcrDocumentPreview.tsx chỉ export PdfSurface sẵn có để tái sử dụng renderer PDF.js và điều hướng trang khi xem phiên bản cũ, không sửa công thức/overlay OCR.
- Không sửa Entity/schema/migration/config runtime, invoice_1/, auth/JWT hoặc công thức OCR/AI/đối soát/thanh toán.

## Thiết kế V2 riêng, chưa thực hiện migration

File table đã có nên không tạo bảng file trùng. Chưa có bảng version dữ liệu chuyên biệt; V1 tái sử dụng audit text hiện có. Nếu khối lượng/retention đòi hỏi bảng riêng, đề xuất migration mới, sau khi đối chiếu metadata PostgreSQL độc lập:

- document_data_versions: PK data_version_id; FK document_id; nullable FK file_version_id/processing_job_id/review_id cho dữ liệu legacy chưa xác minh; version_kind OCR_RESULT/AI_EXTRACTION/MANUAL_EDIT/CONFIRMED; payload JSONB; checksum SHA-256 payload chuẩn hóa; created_by nullable cho actor lịch sử chưa biết; created_at; parent_version_id nullable.
- UNIQUE(document_id, version_kind, version_number), cấp số dưới khóa document; snapshot append-only; ràng buộc file_version.document_id phải trùng document. Không dùng một cột version chung cho file, row locking và token version.
- Giữ audit_logs để audit hành động; tham chiếu data_version_id qua metadata mới theo kế hoạch; không rewrite old audit hoặc coi dữ liệu hiện tại là bản AI đầu tiên. Chỉ backfill baseline được người phụ trách xác minh, gắn provenance BASELINE chứ không giả lịch sử.
- Quyền DB theo nguyên tắc append-only cho history sau khi phân tích quyền ứng dụng; retention/legal hold/MinIO versioning hoặc Object Lock cần quyết định riêng, không tự bật bucket policy/versioning/retention trên kho hiện tại.
- Thứ tự: xác minh schema và các migration pending trước; backup + restore thử riêng; review SQL mới; approval riêng trước DDL. Không tạo SQL executable ở runtime trong lượt này vì chưa đối chiếu schema thật cho V2.
- Rollback triển khai V1: giữ các audit snapshot đã ghi và mọi file/version, dừng writer hoặc sửa lỗi tương thích; không xóa snapshot để rollback. Không quay về code cũ mở lại delete/object-key mutation mà không giữ các guard bảo mật. Migration V2 nếu được duyệt sau này cũng ưu tiên giữ bảng/cột/dữ liệu, không drop/restore đè dữ liệu thật tự động.

## Kiểm thử và giới hạn

Kết quả cuối: `mvn clean test` **426 test tổng = 413 PASS, 0 FAIL/Error, 13 SKIP**; trong đó **15 test mới DocumentArchiveIntegrationTest đều đạt**. Frontend **41 PASS, 0 FAIL/SKIP**, `npm run build` đạt. `git diff --check` đạt. Cảnh báo bundle frontend >500 kB và deprecation Specification.where là cảnh báo, không phải lỗi build. Lượt đầu đã sửa lỗi fixture H2 gán IDENTITY thủ công và verification mock, rồi chạy lại clean toàn bộ; không dùng kết quả lỗi làm PASS. Integration dùng H2 mem `archive_v1_test`, assertion URL H2 trước fixture; MinioClient mock, không gọi MinIO thật. Các test phần mềm không chứng minh object production tồn tại hoặc migration pending đã chạy.

Chưa kiểm chứng live MinIO/PostgreSQL, browser E2E hoặc các client bên ngoài repo. Không khẳng định dữ liệu đã mất trong quá khứ có thể phục hồi. Snapshot tăng dung lượng audit (đặc biệt OCR layout); cần đo dung lượng/retention, phân trang lịch sử và index sau khi khảo sát tải thật. V1 không tự thêm index/schema. Job/log cũ thiếu actor/file link vẫn được đánh dấu thiếu bằng chứng, không suy đoán. Các migration từ Requests 05–06 vẫn chưa được phép chạy; không khởi động backend thật để thử tính năng này.


## Cấu hình runtime phát hiện trong lượt kiểm tra

application.yml dòng 19 hiện có ddl-auto: update (#update/validate). Đây là thay đổi đã tồn tại ở Git status đầu lượt, không phải thay đổi do yêu cầu 08. Không ghi đè cấu hình này và không chạy backend thật. Trước khi triển khai phải rà soát effective profile/environment, đưa runtime về validate theo chính sách đã chốt và kiểm tra schema/migration pending bằng quy trình được phê duyệt. Không suy ra schema PostgreSQL hiện tại từ các test H2 hoặc từ cấu hình update.


Các kiểm chứng mới: upload giữ object/version gốc và ghi SHA metadata, actor/time; từ chối object-key injection/alias và hard delete; Company/version/document mismatch bị chặn trước MinIO; VIEW không tự cấp DOWNLOAD; tải version cũ chọn đúng object key; GET không ghi audit; storage/ETag/checksum projection; snapshot invoice/OCR trước-sau; reprocess giữ kết quả AI trước đó và tách proposal khỏi dữ liệu kế toán; approved document bị khóa; legacy file thiếu version không tự gán gốc; PUT/DELETE history trả 405; rollback hủy cả mutation và snapshot; hai upload đồng thời tạo version 2/3 riêng; correction giữ nguyên qua AI replacement; số tiền vượt JavaScript Number được giữ chính xác bằng decimal string.

Lưu ý cho G7 về sau: nếu chuyển Company của document, cần thiết kế riêng quyền truy cập lịch sử có Company cũ và migration mapping; không tự đổi audit Company hoặc mở scope để lấp lịch sử thiếu trong lượt này.

Trạng thái: CODE/TEST VERIFIED; DATABASE MIGRATION NOT EXECUTED; LIVE MINIO/POSTGRESQL AND BROWSER E2E NOT VERIFIED; RUNTIME DEPLOYMENT NOT PERFORMED. Chưa đủ điều kiện kết luận production-ready do cấu hình ddl-auto=update đã có sẵn và các phụ thuộc schema trước đó chưa được kiểm chứng runtime.


Kiểm tra runtime cuối: có 1 listener trên cổng 8081, PID 1808 java.exe, main InvoiceApplication, chạy từ 09:11 trước lượt triển khai này. Command line/argument file chỉ được kiểm tra nhận dạng, không xuất nội dung hay credential; chưa xác định chắc cùng checkout. Không khởi động/dừng/triển khai vào tiến trình đó. Không dùng tiến trình đang chạy làm bằng chứng runtime của code mới. Các bài test dùng SpringBootTest MOCK/H2; không bind backend nghiệp vụ cổng 8081.
