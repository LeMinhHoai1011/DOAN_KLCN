# Yêu cầu 02 — Confidence, validation và NEED_REVIEW

## Phạm vi và hiện trạng đã kiểm tra

Branch `dev/ai`; Git sạch tại đầu lượt triển khai. Không đổi authentication/JWT/RBAC,
company scope, workflow APPROVE, entity/schema/migration hoặc invoice_1.
Không kết nối PostgreSQL thật hoặc chạy upload/reprocess trên chứng từ thật.

Luồng thực tế: DocumentUploadProcessingService → DocumentService.claimForAiProcessing →
DocumentAiProcessingService (OCR/PDF và AI) → AiDocumentResultValidator →
DocumentAiResultPersistenceService → React AccountantDocumentDetail.
ReconciliationService là luồng độc lập do người dùng gọi; không tự phê duyệt chứng từ.
ReviewWorkflowService chỉ tạo giao dịch từ chứng từ được duyệt qua workflow có quyền.

Đã tồn tại: OCR confidence trong ocr_results, classification confidence/reason trong
ai_classifications; confidence theo trường trong extracted_fields; validation số tiền,
MST, ngày và chi tiết dòng; bảo vệ invoice/classification chỉnh thủ công và chứng từ APPROVED.

Lỗi đã xác minh từ source trước thay đổi:
- PDF text layer bị gán OCR confidence 100 mặc dù không chạy OCR.
- Tổng hợp classification PDF dùng phép chia không chỉ định scale nên đầu vào scale 0 (1 và 0) có thể cho trung bình 1 thay vì 0.5;
  trường hợp tất cả trang thiếu confidence thành 0, hoặc bỏ qua trang thiếu confidence.
- Validation chưa luôn bắt hóa đơn thiếu items; cảnh báo OCR chưa tham gia quyết định trạng thái.
- processing_logs.message bị cắt 255 ký tự; trang chi tiết chưa hiển thị đánh giá đầy đủ.
- Bulk delete trường AI chưa loại trừ trường có field_corrections.
- Reprocess ảnh dùng OCR lưu trước đó nhưng dữ liệu OCR không có định danh phiên bản file.

## Confidence và lưu trữ

- OCR: chỉ confidence đo được từ Tesseract, thang 0–1; trung bình word của từng trang rồi
  trung bình trang. Text layer hoặc nguồn không đo được: null, không gán 100.
- Classification: confidence model tự báo cáo; PDF trung bình khi mọi trang đều cung cấp,
  giữ scale 4; nếu thiếu bất kỳ trang nào thì null. Đây không phải xác suất đã hiệu chuẩn.
- Extraction: contract AI hiện không có confidence tổng trích xuất. Không lấy classification
  thay thế, không tự tính trung bình field; trả null và method NOT_PROVIDED_BY_MODEL.
  Confidence từng field vẫn được giữ riêng nếu model cung cấp.
- API mới: GET /api/v1/documents/{id}/assessment, dùng quyền DOCUMENT_VIEW/ADMIN và
  DocumentService.load để giữ scope người dùng/doanh nghiệp.
- DTO DocumentAssessment có ba score kèm source/method, validationStatus, warnings
  (code/severity/message/affectedField), reviewReasons.
- Tái sử dụng processing_logs: JSON đầy đủ chia đoạn tối đa 200 UTF-16 code units,
  không cắt giữa surrogate pair, với BEGIN/PART/END trong cùng transaction; không thêm bảng/cột.
  Đọc theo log_id. AI_DOCUMENT/START làm mất hiệu lực assessment lần trước; FAILED/PROCESSING
  không công bố lại confidence cũ. Payload legacy thiếu metadata trả UNKNOWN và lý do rõ ràng.
- Classification.reason vẫn giữ toàn bộ cảnh báo tương thích API hiện có. Log thông thường
  tiếp tục giới hạn 255 ký tự; assessment riêng được ghép đủ, không dựa vào log bị cắt.

## Validation theo loại (mapping đã được người dùng xác nhận)

Dữ liệu chuyên biệt đi qua AiDocumentResult.extraFields → extracted_fields, document_id,
invoice_id null. Cũng tiếp nhận fields hiện có và hợp nhất theo tên; xung đột là CRITICAL.
Không thêm Entity chuyên biệt hoặc migration. INVOICE tiếp tục đi vào Invoice/InvoiceItem.

| Loại | REQUIRED | CONDITIONAL | OPTIONAL |
|---|---|---|---|
| INVOICE/VAT_INVOICE | số/ngày hóa đơn, người bán/MST, subtotal, VAT, tổng tiền, ít nhất 1 item hợp lệ; số lượng/đơn giá/thành tiền dòng | kiểm tra MST/ngày ký và phép tính dòng/thuế khi có dữ liệu | các thông tin mở rộng hiện có |
| PAYMENT_VOUCHER | voucherDate, payerName, payeeName, paymentReason, totalAmount, currency | kiểm tra MST nếu được cung cấp | voucherNumber, paymentMethod và trường bổ sung |
| RECEIPT_VOUCHER | voucherDate, payerName, recipientName, receiptReason, totalAmount, currency | kiểm tra MST nếu được cung cấp | voucherNumber, paymentMethod và trường bổ sung |
| RECEIPT | receiptDate, issuerName, receiptKind | PAYMENT: payerName, totalAmount, currency; NON_FINANCIAL: recipientName, subject, không có dữ liệu tiền mâu thuẫn | receiptNumber, MST và trường bổ sung |
| CONTRACT | contractDate, partyAName, partyBName, subject | contractNumber hoặc contractTitle hoặc referenceNumber; currency khi có totalAmount | giá trị tiền, effectiveDate, expiryDate, MST hai bên |

Ngày chấp nhận ISO yyyy-MM-dd hoặc dd/MM/yyyy hợp lệ, lưu ISO; không tự gán ngày hiện tại.
Tiền phải là số thập phân dương; không đoán dấu phân nhóm mơ hồ. Currency dùng mã ISO 4217
do java.util.Currency nhận biết, chuẩn hóa chữ hoa; không tự suy ra VND, không nhận XXX/XTS.
Nếu có effectiveDate và expiryDate thì ngày hết hạn không trước ngày hiệu lực.
MST tùy chọn kiểm tra định dạng 10 chữ số hoặc 10 chữ số + mã chi nhánh -xxx; chưa tra cứu
tồn tại thực tế hay checksum cơ quan thuế. Không sử dụng bộ trường INVOICE cho loại khác.
Không biết receiptKind → NEED_REVIEW, không suy luận từ tên file.

Quyết định trạng thái:
- Thiếu trường bắt buộc, dữ liệu sai, confidence phân loại null/thấp, OCR đo được thấp hơn
  ngưỡng hoặc CRITICAL → NEED_REVIEW; mỗi trường hợp có mã và lý do.
- Loại chứng từ không xác định/ngoài danh sách → NEED_REVIEW, không tự gán OTHER hay tạo loại mới.
- Phân tích được, validation hợp lệ và đạt ngưỡng → PROCESSED, reviewStatus PENDING.
- Lỗi kỹ thuật như JSON không parse được/kết nối AI thất bại → FAILED, không cấp confidence giả.
- APPROVED và giao dịch tài chính vẫn do workflow có quyền quyết định, không dựa vào confidence.
- Workflow có thể đưa một kết quả VALID vào NEED_REVIEW: API giải thích WORKFLOW_REVIEW_REQUIRED.

## Reprocess và dữ liệu đã chỉnh sửa

Reprocess đọc lại file hiện tại, chạy OCR mới; không dùng lại OCR chưa gắn phiên bản file.
APPROVED bị chặn theo cơ chế có sẵn. Invoice/classification chỉnh thủ công vẫn được giữ.
Bulk delete AI fields loại trừ field có correction; không tạo field cùng tên đè lên correction.
Nếu invoice AI mới thiếu items nhưng invoice cũ có items, giữ items cũ để tránh mất dữ liệu,
ghi STALE_AI_ITEMS_PRESERVED và NEED_REVIEW; không coi đó là kết quả trích xuất mới thành công.
Giữ cơ chế chống mất dấu tiếng Việt của tên/địa chỉ đã có.

## Frontend

Giữ trang chi tiết/layout hiện tại, thêm card Đánh giá OCR/AI: ba confidence, validation,
cảnh báo có severity/code/trường và lý do cần kiểm tra. Dùng API thật, không mock dữ liệu nghiệp vụ.
formatConfidence(null/undefined/không hợp lệ) hiển thị Chưa xác định.

## Kiểm chứng và giới hạn

Kết quả cuối:
- Backend Maven test, Java 21, offline dependencies: 333 tổng, 320 PASS, 0 FAIL/Error,
  13 SKIP; BUILD SUCCESS. Các test runtime bật bằng biến môi trường vẫn bị skip.
- Chạy riêng DocumentAssessmentTest sau khi bổ sung assertion lý do workflow:
  2 PASS, 0 FAIL/Error.
- Regression cuối DocumentAiProcessingServiceTest và DocumentAiResultPersistenceServiceTest:
  28 PASS, 0 FAIL/Error; có assertion trung bình confidence 0/1 bằng 0.5 và cảnh báo
  STALE_AI_ITEMS_PRESERVED khi giữ lại dòng hàng cũ.
- Frontend npm run build: PASS; npm test: 15 PASS, 0 FAIL/SKIP.
- git diff --check: PASS. Vite vẫn cảnh báo bundle lớn hơn 500 kB.
Integration test H2 kiểm tra cả năm loại: persist, đọc lại assessment, giữ PENDING và không
tạo giao dịch; test riêng giữ nguyên field có lịch sử chỉnh sửa khi thay AI fields.
Unit/regression test gồm 99% thiếu item, 100% sai tổng, 95% hợp lệ, null, mất dấu,
PDF thiếu confidence, lưu cảnh báo dài, reprocess mới, không rõ loại và JSON lỗi.

Không chạy runtime đầy đủ với PostgreSQL/MinIO/Ollama/Tesseract thật hoặc kiểm thử trình duyệt
trên API sống. Không khẳng định độ chính xác OCR/AI ngoài đời từ các test mock/H2.
Không backfill assessment cho chứng từ cũ. Mã field chuyên biệt phải khớp tên đã công bố;
model không trả đủ thì NEED_REVIEW, không tự bổ sung.
Log chia đoạn làm tăng số dòng log; chưa tối ưu đọc lịch sử log rất lớn. Có thể cân nhắc
đổi message thành text trong migration riêng về sau, không cần cho thay đổi hiện tại.
Chưa thiết kế quy tắc pháp lý/chữ ký/thuế chuyên sâu, đơn vị tiền tệ lịch sử hoặc alias tên
trường tự do ngoài mapping đã duyệt.

## File liên quan

Backend production: AiDocumentPromptFactory, AiDocumentResultValidator, AssessmentWarnings,
DocumentTypeValidationPolicy, DocumentAssessment, DocumentController, ExtractedFieldRepository,
DocumentAiProcessingService, DocumentAiResultPersistenceService, DocumentTextExtractionService,
DocumentService, ProcessingLogService.

Backend tests: AiDocumentResultValidatorTest, AssessmentWarningsTest, DocumentTypeValidationPolicyTest,
DocumentAssessmentPersistenceTest, CorrectedFieldReprocessTest, DocumentAssessmentTest,
DocumentAiProcessingServiceTest, DocumentAiResultPersistenceServiceTest.

Frontend: AccountantDocumentDetail.tsx, documentService.ts, confidence.ts, confidence.test.mjs.
