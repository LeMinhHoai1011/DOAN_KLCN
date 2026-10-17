# Yêu cầu 04 — Chuẩn hóa trạng thái nghiệp vụ chứng từ

## Phạm vi và hiện trạng xác minh từ source

Branch dev/ai. Git trước sửa đã có các thay đổi chưa commit của Yêu cầu 02–03 (OCR/AI, assessment, pipeline, React). Giữ nguyên các thay đổi đó. Không đọc/ghi PostgreSQL thật, không chạy backend nghiệp vụ, không migration, không commit/push. invoice_1/ không thay đổi.

Các enum lưu trữ được giữ nguyên:
- DocumentStatus: UPLOADED, PROCESSING, PROCESSED, NEED_REVIEW, COMPLETED, FAILED.
- ClassificationStatus: PENDING, ACCEPTED, NEED_REVIEW, CORRECTED, CLASSIFIED, REVIEW_REQUIRED, VERIFIED.
- ReviewStatus: PENDING, APPROVED, REJECTED, CORRECTED.
- Reconciliation: MATCHED, MISMATCH, INSUFFICIENT_DATA; quyết định thủ công MATCHED/NEED_REVIEW.
- FinancialTransaction.status=ACTIVE là hiệu lực giao dịch; không phải bằng chứng đã thanh toán. Không có mô hình settlement/số tiền đã trả được xác minh để suy ra UNPAID/PARTIALLY_PAID/PAID.

Lỗi đã xác minh: ClassificationService.approve từng đặt cả document COMPLETED và APPROVED; DocumentService.update từng nhận trực tiếp status từ request; InvoiceService sửa/xóa chưa khóa trạng thái; OCR upsert cũng chưa chặn chứng từ đã duyệt; workflow chưa tuần tự hóa hai yêu cầu duyệt; pipeline từng reset reviewStatus=PENDING. GET đối soát hiện vốn chỉ đọc, frontend getByDocument dùng GET — giữ nguyên đặc tính này.

## Quy tắc trạng thái logic — không thay dữ liệu lịch sử

| Nhóm | Nguồn | Quy tắc |
|---|---|---|
| Xử lý | documents.status | Giữ các trạng thái pipeline; COMPLETED cũ được chiếu thành PROCESSED; không UPDATE dữ liệu cũ |
| Phân loại | ai_classifications mới nhất | Chưa có/PENDING: PENDING; kết quả với trạng thái đã xử lý: COMPLETED. ACCEPTED chỉ xác nhận phân loại, không phê duyệt chứng từ |
| Đối soát | reconciliations và reconciliation_batches | Chưa có: NOT_RUN; batch mới nhất có MISMATCH: MISMATCH; toàn bộ MATCHED: MATCHED; thiếu dữ liệu/quyết định cần kiểm tra/trạng thái lạ: WARNING. Invoice sửa sau batch: WARNING |
| Kiểm duyệt | documents.review_status và document_reviews | APPROVED/REJECTED giữ nguyên; PENDING với sự kiện mới nhất START_REVIEW: IN_REVIEW. CORRECTED lưu trữ được chiếu thành PENDING, giữ yêu cầu bổ sung trong lịch sử |
| Thanh toán | Không có chứng cứ settlement | payment=null, hiển thị Chưa có dữ liệu thanh toán; không suy ra PAID hoặc UNPAID từ phê duyệt, paymentMethod hoặc giao dịch ACTIVE |

Không tạo đủ năm enum mới. Chưa có bằng chứng riêng về lỗi phân loại thì không tự biến FAILED của pipeline thành FAILED phân loại.

## Bảng chuyển trạng thái workflow

| Hành động | Vai trò | Điều kiện | Kết quả lưu trữ |
|---|---|---|---|
| SUBMIT | EMPLOYEE/USER cũ, đúng scope chủ sở hữu | UPLOADED/PENDING; không gửi trùng sự kiện gần nhất | Ghi SUBMIT; controller chạy pipeline hiện có |
| START_REVIEW | ADMIN/ACCOUNTANT có role active | PROCESSED hoặc NEED_REVIEW, review PENDING, chưa START_REVIEW lần gần nhất | NEED_REVIEW/PENDING; ghi START_REVIEW, logic IN_REVIEW |
| APPROVE | ADMIN/ACCOUNTANT active | PROCESSED hoặc NEED_REVIEW, review PENDING, chưa khóa | COMPLETED/APPROVED, actor/thời gian; giữ hiệu ứng tài chính hiện hành một lần, không coi là thanh toán |
| REJECT | ADMIN/ACCOUNTANT active | Trạng thái xử lý đã hoàn tất/cần kiểm tra, review PENDING; bắt buộc lý do | NEED_REVIEW/REJECTED; thêm lịch sử |
| REQUEST_INFO | ADMIN/ACCOUNTANT active | Như REJECT; bắt buộc lý do | NEED_REVIEW/CORRECTED; thêm lịch sử |
| RESUBMIT | EMPLOYEE/USER cũ, đúng scope | NEED_REVIEW và REJECTED/CORRECTED | UPLOADED/PENDING; thêm lịch sử; controller chạy pipeline |

Mọi hành động bị chặn nếu APPROVED hoặc COMPLETED. Duyệt lặp bị từ chối. Khóa pessimistic trên Document, refresh sau khi chờ khóa và kiểm tra lại scope trước thao tác. Không giữ khóa trong thời gian gọi OCR/AI. Nếu có job RUNNING, workflow/chỉnh sửa bị từ chối.

API cập nhật metadata chỉ chấp nhận status rỗng hoặc giữ nguyên; không được chuyển trạng thái. Sửa/xóa invoice, sửa phân loại, correction trường, metadata, phiên bản, xóa document, OCR upsert và quyết định/chạy đối soát dùng chung guard chứng từ. Không chỉnh sửa hoặc xử lý lại chứng từ đã duyệt/khóa. Chứng từ đã trả về phải RESUBMIT trước khi xử lý AI; pipeline không reset quyết định kiểm duyệt. Hai method legacy createReview/completeReview không có caller trong source hiện tại được giữ chữ ký nhưng từ chối ghi; dùng execute workflow để ghi lịch sử chuẩn.

Đối soát POST tạo batch/kết quả mới, không ghi đè batch/kết quả cũ; GET không tự chạy đối soát. Sắp xếp kết quả theo createdAt rồi ID để ổn định khi cùng thời điểm. Tổng hợp chỉ dùng batch mới nhất, không lấy dòng kiểm tra không còn thuộc batch hiện tại.

## API, file và bảng liên quan

API mới GET /api/v1/documents/{documentId}/business-state: yêu cầu quyền DOCUMENT_VIEW hoặc ADMIN và DocumentService.load kiểm tra scope hiện hành. DTO gồm processing/classification/reconciliation/review/payment/paymentReason/history (actor ID, thời gian, action/note). Không trả Entity hoặc thông tin tài khoản nhạy cảm.

File production mới:
- backendqt/backendqt/src/main/java/com/example/invoice/dto/document/DocumentBusinessState.java
- backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentBusinessStateService.java — get projection chỉ đọc.
- backendqt/backendqt/src/main/java/com/example/invoice/controller/DocumentBusinessStateController.java — get.

File production sửa trong yêu cầu này:
- service/DocumentService.java — lockForWorkflow, lockForEdit, update, uploadNewVersion, delete, claimForAiProcessing.
- service/ReviewWorkflowService.java — execute, currentResponse, kiểm tra role active, latestActionIs; khóa và history; legacy methods fail closed.
- controller/DocumentWorkflowController.java — trả trạng thái được đọc lại sau SUBMIT/RESUBMIT thay vì response trước OCR/AI.
- service/ClassificationService.java — upsert/approve/review/correction dùng guard; approve chỉ thay classification.
- service/InvoiceService.java — create/update/delete; không chuyển hóa đơn sang document khác.
- service/ExtractedFieldCorrectionService.java — correct dùng guard.
- service/OCRResultService.java — chỉ upsert thủ công dùng guard, không sửa thuật toán OCR.
- service/PipelineExecutionService.java — start từ chối chứng từ đã trả về/chứng từ khóa.
- service/DocumentAiResultPersistenceService.java — không reset reviewStatus.
- service/ReconciliationService.java — run/decide dùng guard; run thêm batch, find readOnly.
- repository/ReconciliationRepository.java — thứ tự có ID tiebreak.
- quan tri/src/services/documentService.ts — DTO/client business-state.
- quan tri/src/components/StatusBadge.tsx — COMPLETED có nhãn xử lý riêng; tooltip review tiếng Việt.
- quan tri/src/components/BusinessStateCard.tsx (mới) — năm nhóm trạng thái và lịch sử review.
- quan tri/src/utils/businessStatePresentation.ts (mới) — nhãn/màu theo nhóm.
- quan tri/src/pages/accountant/AccountantDocumentDetail.tsx — card trạng thái, refresh sau hành động/sửa dữ liệu, điều kiện nút workflow/đối soát.
- quan tri/package.json — thêm test presentation.

Bảng dùng lại: documents, document_reviews, ai_classifications, invoices/invoice_items, extracted_fields/field_corrections, reconciliations/reconciliation_batches, processing_jobs. financial_transactions không sửa schema hoặc chính sách thanh toán. Authentication/JWT/RBAC/company scope không thay cấu hình hoặc role assignment.

Test mới: DocumentBusinessStateServiceTest, WorkflowConcurrencyIntegrationTest, businessStatePresentation.test.mjs. Bổ sung/điều chỉnh ReviewWorkflowServiceTest, ClassificationServiceTest, DocumentProcessingMutationGuardTest, ExtractedFieldCorrectionServiceTest, ReconciliationServiceTest cho mutation lock API và quy tắc mới.

## Kiểm chứng

Kết quả cuối ngày 2026-10-16:
- Maven backend: BUILD SUCCESS, tổng 363 test = 350 PASS, 0 FAIL, 0 ERROR, 13 SKIP. Log cục bộ backendqt/backendqt/request04-test.log (không commit).
- Frontend npm test: 20 PASS, 0 FAIL/SKIP; npm run build (tsc + Vite): PASS, còn cảnh báo bundle >500 kB.
- git diff --check: PASS.
- git diff --name-only -- invoice_1: rỗng. Các cấu hình runtime/resources, AuthService, security không có diff mới.
- Git sau sửa vẫn giữ nguyên toàn bộ dirty files của Yêu cầu 02–03; bổ sung các file/method liệt kê ở trên, không stage hoặc commit. Không thay branch.

| Tình huống bắt buộc | Bằng chứng |
|---|---|
| AI phân loại xong, chưa duyệt | DocumentBusinessStateServiceTest + ClassificationServiceTest: classification COMPLETED/ACCEPTED không đổi review PENDING hoặc processing PROCESSED |
| Chứng từ đã duyệt, không có dữ liệu trả tiền | DocumentBusinessStateServiceTest: review APPROVED, processing PROCESSED, payment null; không gán PAID hoặc bịa UNPAID |
| Bị từ chối/yêu cầu bổ sung | ReviewWorkflowServiceTest: lưu actor/note, trạng thái REJECTED/CORRECTED; phải gửi lại trước khi duyệt |
| Cảnh báo đối soát | DocumentBusinessStateServiceTest + ReconciliationServiceTest: INSUFFICIENT_DATA thành WARNING; số tiền chưa xác định vẫn null |
| Duyệt lặp và duyệt đồng thời | ReviewWorkflowServiceTest + WorkflowConcurrencyIntegrationTest: một winner, một history entry, một lần gọi financial effect |
| Không có quyền hoặc role không active | ReviewWorkflowServiceTest: EMPLOYEE và ADMIN role inactive bị AccessDenied; handler backend hiện hành trả FORBIDDEN/403 |
| Sửa chứng từ đã khóa | DocumentProcessingMutationGuardTest: APPROVED hoặc COMPLETED chặn update/upload version/delete trước storage và repository write; chặn client đổi status |
| GET không ghi | DocumentBusinessStateServiceTest kiểm tra không save review/reconciliation; ReconciliationService.find chỉ repository read, frontend dùng GET |

H2 là database độc lập; không phải PostgreSQL production. Test concurrency dùng row lock/transaction H2 thật; scope và actor được fixture/mock để cô lập; FinancialTransactionService được mock trong test concurrency nên bằng chứng ở đây là một lần gọi tạo giao dịch, không phải kiểm đếm giao dịch PostgreSQL thật. Các test regression backend còn lại đều nằm trong tổng kết trên. Chưa kiểm thử trình duyệt hoặc runtime trên dịch vụ thật.

## Hạn chế và bước tiếp theo

- Không có dữ liệu settlement: chưa triển khai suy luận trạng thái thanh toán; nếu cần, phải khảo sát nguồn số tiền đã thanh toán, phân bổ thanh toán, hoàn tiền/hủy và xin duyệt thiết kế/migration riêng.
- Không sửa bản ghi cũ từng được phê duyệt qua đường classification; lịch sử thiếu actor/thời gian không tự bịa bổ sung.
- Trạng thái phân loại logic COMPLETED không có nghĩa validation hợp lệ hoặc đã duyệt; cảnh báo chi tiết vẫn ở assessment của Yêu cầu 02.
- Các bản ghi đối soát legacy thiếu batch chỉ tổng hợp theo dữ liệu đang có; không tự chuẩn hóa database.
- Cảnh báo stale đối soát dựa trên thời gian cập nhật Invoice; chưa có version riêng cho từng bộ kết quả AI/đối soát, không chứng minh đầy đủ lineage lịch sử cũ.
- Không chạy E2E trên PostgreSQL/MinIO/Ollama thật hoặc trình duyệt với dữ liệu nghiệp vụ. Cần môi trường PostgreSQL test riêng để kiểm chứng lock/timeout và hồi quy endpoint theo quyền thật trước triển khai.
- Vite có cảnh báo chunk lớn; không ảnh hưởng kết quả build.
- Không migration, không thay enum/cột/schema. Không reset dữ liệu hoặc tài khoản. Không commit/push.
