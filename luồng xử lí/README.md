# Luồng xử lí theo mã nguồn hiện tại

Mở **[HE_THONG_XU_LY.drawio](HE_THONG_XU_LY.drawio)** bằng extension Draw.io trong IDE. File chứa 23 trang; chọn tab ở cạnh dưới để chuyển sơ đồ. Có thể dùng Open With → Draw.io Editor nếu IDE mở XML.

Không cần thêm MCP, cài package hoặc tải dữ liệu ra dịch vụ ngoài. Các node, nhãn, điều kiện và connector đều chỉnh sửa được; file là XML draw.io không nén.

## Phạm vi và cách đọc

Đối chiếu tĩnh ngày 07/11/2026. Danh mục nguồn bao gồm 436 file, 31,781 dòng thuộc Java application, React src, Flutter lib và benchmark Python. Quét danh mục/entrypoint toàn bộ phạm vi này; đọc chi tiết các service/call site cốt lõi để dựng luồng. Đây không phải khẳng định đã kiểm thử mọi nhánh hoặc đọc tuần tự từng dòng của toàn bộ repository.

Không quét credentials, .env, config.local, dữ liệu khách hàng, thư viện vendor hay file build. Các file test không được dùng làm bằng chứng endpoint đang được UI gọi. Luồng thể hiện implementation, không xác nhận mọi dịch vụ đang chạy trên máy.

Xanh dương: đầu vào; xanh lá: kết thúc; trắng: xử lý; vàng: quyết định; tím: dữ liệu; đỏ: nhánh lỗi/từ chối. Nhãn cạnh là điều kiện. Mỗi trang có lưu ý và danh sách nguồn ở cuối.

## Mục lục và nguồn

### 01 · Tổng quan hệ thống

Hai ranh giới: pipeline nghiệp vụ có persistence; benchmark gọi helper chung nhưng không ghi DB/MinIO nghiệp vụ.

- [App.tsx](../quan%20tri/src/App.tsx)
- [routes.dart](../invoice_1/lib/app/routes.dart)
- [DocumentController.java](../backendqt/backendqt/src/main/java/com/example/invoice/controller/DocumentController.java)
- [BenchmarkAdapter.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/BenchmarkAdapter.java)
- [main.py](../benchmark_runner/main.py)

### 02 · Đăng nhập và phân quyền

Guard của client điều hướng giao diện; backend tiếp tục kiểm quyền và phạm vi dữ liệu, không chỉ tin client.

- [AuthService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/AuthService.java)
- [SecurityConfig.java](../backendqt/backendqt/src/main/java/com/example/invoice/config/SecurityConfig.java)
- [JwtAuthenticationFilter.java](../backendqt/backendqt/src/main/java/com/example/invoice/security/JwtAuthenticationFilter.java)
- [DocumentService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentService.java)
- [App.tsx](../quan%20tri/src/App.tsx)
- [routes.dart](../invoice_1/lib/app/routes.dart)

### 03 · Upload và phiên bản file

Upload và lưu chứng từ commit trước công việc AI từ xa. Không vẽ upload là một transaction kéo dài xuyên suốt AI.

- [DocumentController.java](../backendqt/backendqt/src/main/java/com/example/invoice/controller/DocumentController.java)
- [DocumentUploadProcessingService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentUploadProcessingService.java)
- [DocumentService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentService.java)
- [FileValidationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/FileValidationService.java)
- [documentService.ts](../quan%20tri/src/services/documentService.ts)
- [document_repository.dart](../invoice_1/lib/features/documents/data/document_repository.dart)

### 04 · Pipeline nghiệp vụ

Mũi tên lỗi biểu diễn nhánh đại diện; catch runtime bao quanh toàn bộ phần chuẩn bị/xử lý/lưu trong processAttempt.

- [DocumentAiProcessingService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentAiProcessingService.java)
- [PipelineExecutionService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PipelineExecutionService.java)
- [DocumentAiResultPersistenceService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentAiResultPersistenceService.java)

### 05 · PDF nhiều trang

Luồng thực gọi là analyzePdfPages. analyzeVisionFallback và DocumentTextExtractionService không được tự ghép vào nhánh này nếu không có call site. Retail nhiều trang có cảnh báo kiểm tay và bỏ retailDetails khi gộp.

- [DocumentAiProcessingService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentAiProcessingService.java)
- [PdfImageConversionService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PdfImageConversionService.java)

### 06 · OCR thích nghi

Score OCR là heuristic chọn chất lượng, không phải CER/WER và không phải xác suất giá trị trích xuất đúng.

- [AdaptiveOcrService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/AdaptiveOcrService.java)
- [OpenCvImageProcessor.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/OpenCvImageProcessor.java)
- [Tess4jOcrService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/Tess4jOcrService.java)
- [OcrLayoutReconstructor.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/OcrLayoutReconstructor.java)

### 07 · Chọn AI text hoặc vision

PDF digital đi trực tiếp TEXT theo sơ đồ 05. Ollama dùng /api/generate, stream=false, format=json; kiểm token budget và timeout. Không đồng nhất fallback định dạng với retry job.

- [DocumentAiProcessingService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentAiProcessingService.java)
- [AiProcessingService.java](../backendqt/backendqt/src/main/java/com/example/invoice/ai/AiProcessingService.java)
- [OllamaAiProvider.java](../backendqt/backendqt/src/main/java/com/example/invoice/ai/OllamaAiProvider.java)
- [AiDocumentResultParser.java](../backendqt/backendqt/src/main/java/com/example/invoice/ai/AiDocumentResultParser.java)

### 08 · Chuẩn hóa và lưu AI

Trường hợp hóa đơn thiếu extraction vẫn theo guard trong persist; không suy ra mọi lần AI đều ghi đủ item. Chỉnh loại/danh mục thủ công là chức năng riêng ClassificationService.

- [AiDocumentResultValidator.java](../backendqt/backendqt/src/main/java/com/example/invoice/ai/AiDocumentResultValidator.java)
- [DocumentAiResultPersistenceService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentAiResultPersistenceService.java)
- [ClassificationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ClassificationService.java)

### 09 · Job, lỗi và retry

Retry là thao tác theo điều kiện của API; sơ đồ không hàm ý có hàng đợi tự chạy lại nền. Progress registry là thông tin tiến độ, không thay lịch sử DB.

- [PipelineExecutionService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PipelineExecutionService.java)
- [PipelineFailures.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PipelineFailures.java)
- [ProcessingProgressRegistry.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ProcessingProgressRegistry.java)

### 10 · Sửa dữ liệu và xác minh trường

FieldVerification không sửa giá trị nghiệp vụ, không lấy điểm rule làm GT benchmark. Gọi recompute sau sửa là thao tác riêng; không vẽ thành auto-call của mọi endpoint sửa.

- [InvoiceService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/InvoiceService.java)
- [ExtractedFieldCorrectionService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ExtractedFieldCorrectionService.java)
- [FieldVerificationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/verification/FieldVerificationService.java)
- [FieldVerifier.java](../backendqt/backendqt/src/main/java/com/example/invoice/verification/FieldVerifier.java)
- [OcrFieldLocator.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/OcrFieldLocator.java)

### 11 · Duyệt và gửi lại

SUBMIT ghi event, không tự gọi AI trong ReviewWorkflowService. APPROVE chỉ là quyết định duyệt, không tạo phiếu thu/chi hoặc xác nhận đã thanh toán.

- [ReviewWorkflowService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ReviewWorkflowService.java)
- [DocumentWorkflowController.java](../backendqt/backendqt/src/main/java/com/example/invoice/controller/DocumentWorkflowController.java)
- [ReconciliationState.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ReconciliationState.java)

### 12 · Đối soát hóa đơn

Chỉ số số học và đối soát không kết luận tính hợp pháp của chứng từ. Đối soát tiền thực thu/chi là luồng khác ở sơ đồ 13.

- [ReconciliationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ReconciliationService.java)
- [ReconciliationState.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ReconciliationState.java)
- [InvoiceReconciliationRules.java](../backendqt/backendqt/src/main/java/com/example/invoice/ai/InvoiceReconciliationRules.java)
- [RetailInvoiceRules.java](../backendqt/backendqt/src/main/java/com/example/invoice/ai/RetailInvoiceRules.java)

### 13 · Thu chi và thanh toán

createFromApprovedDocument hiện trả Optional.empty(). Không suy ra giao dịch tiền từ duyệt hóa đơn, hình thức thanh toán hay gợi ý AI.

- [FinancialTransactionService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/FinancialTransactionService.java)
- [PaymentLinkService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PaymentLinkService.java)
- [PaymentReconciliationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PaymentReconciliationService.java)

### 14 · Lưu trữ và báo cáo nghiệp vụ

Snapshot lỗi không lấy dữ liệu hiện tại thay thế. Các phiên bản file và dữ liệu có lịch sử riêng; dashboard nghiệp vụ không đồng nhất với benchmark dashboard.

- [DocumentArchiveService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentArchiveService.java)
- [DocumentAuditService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentAuditService.java)
- [ExcelExportService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ExcelExportService.java)
- [DashboardService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DashboardService.java)
- [DocumentService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentService.java)

### 15 · Gán nhãn đánh giá trên ứng dụng

Đây là kho nhãn live của backend, không phải thư mục ground_truth của benchmark Python. Endpoint đánh giá bundle A/B/C/R còn là một luồng riêng (sơ đồ 16).

- [LiveEvaluationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/LiveEvaluationService.java)
- [LiveEvaluationStore.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/LiveEvaluationStore.java)
- [LivePrediction.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/LivePrediction.java)
- [LiveEvaluationScoring.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/LiveEvaluationScoring.java)
- [EvaluationLabeling.tsx](../quan%20tri/src/pages/accountant/EvaluationLabeling.tsx)

### 16 · Đánh giá bundle thực nghiệm

R là baseline không LLM; A/B/C là AI variants theo định nghĩa service. Sơ đồ mô tả đường đọc/tính báo cáo, không khẳng định các run đã được thực nghiệm đầy đủ.

- [EvaluationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/EvaluationService.java)
- [EvaluationMetrics.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/EvaluationMetrics.java)
- [EvaluationData.java](../backendqt/backendqt/src/main/java/com/example/invoice/evaluation/EvaluationData.java)
- [EvaluationController.java](../backendqt/backendqt/src/main/java/com/example/invoice/controller/EvaluationController.java)

### 17 · Chạy benchmark file-only

mode both gọi endpoint AI có kèm OCR, không phải hai lần chạy OCR và AI độc lập. Retry/rerun chọn bởi flags và giữ attempt cũ. Gate benchmark giữ đến khi task thực sự kết thúc, kể cả HTTP timeout.

- [main.py](../benchmark_runner/main.py)
- [benchmark_runner.py](../benchmark_runner/src/benchmark_runner.py)
- [dataset_loader.py](../benchmark_runner/src/dataset_loader.py)
- [BenchmarkController.java](../backendqt/backendqt/src/main/java/com/example/invoice/controller/BenchmarkController.java)
- [BenchmarkAdapter.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/BenchmarkAdapter.java)

### 18 · Import GT và tính P/R/F1

UNREADABLE bị loại; GT ngoài dùng UNREADABLE cả cho ô chưa gán nhãn theo quy ước tương thích được lưu ở review_source. Core thiếu điều kiện thì null. Bộ export Ai.xlsx và evaluator cơ bản có phạm vi riêng, không gộp điểm tùy tiện.

- [external_gt.py](../benchmark_runner/src/external_gt.py)
- [ground_truth_manager.py](../benchmark_runner/src/ground_truth_manager.py)
- [ai_evaluator.py](../benchmark_runner/src/ai_evaluator.py)
- [ai_workbook.py](../benchmark_runner/src/ai_workbook.py)
- [review_workbook.py](../benchmark_runner/src/review_workbook.py)

### 19 · Đánh giá OCR và ghép run

CER/WER càng thấp càng tốt và có thể vượt 100%. PDF_TEXT_LAYER đo văn bản PDF đã lấy, không tự coi là đã kiểm thử nhận dạng OCR ảnh.

- [ocr_workbook.py](../benchmark_runner/src/ocr_workbook.py)
- [ocr_ground_truth.py](../benchmark_runner/src/ocr_ground_truth.py)
- [ocr_evaluator.py](../benchmark_runner/src/ocr_evaluator.py)
- [paired_export.py](../benchmark_runner/src/paired_export.py)

### 20 · Dashboard benchmark cục bộ

OCR được hiển thị theo run nguồn riêng. Import GT và chạy model vẫn qua CLI có xác thực, dashboard không nhận token trên giao diện.

- [main.py](../benchmark_runner/main.py)
- [dashboard.py](../benchmark_runner/src/dashboard.py)
- [dashboard.html](../benchmark_runner/src/dashboard.html)

### 21 · Quản trị và danh mục

Đây là bản đồ chức năng quản trị ở mức service, không khẳng định mọi thao tác quản trị đều có trên mobile. Đăng ký công khai bị chặn dù client còn route Register.

- [UserController.java](../backendqt/backendqt/src/main/java/com/example/invoice/controller/UserController.java)
- [UserService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/UserService.java)
- [RoleService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/RoleService.java)
- [AccountingCategoryService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/AccountingCategoryService.java)
- [InternalCompanyService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/InternalCompanyService.java)
- [App.tsx](../quan%20tri/src/App.tsx)

### 22 · Phân loại và trạng thái tổng hợp

Trạng thái tổng hợp để trình bày không thay enum trạng thái lưu DB. Classification ACCEPTED không đồng nghĩa Document APPROVED và cũng không chứng minh đã thanh toán.

- [ClassificationService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ClassificationService.java)
- [DocumentBusinessStateService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/DocumentBusinessStateService.java)
- [PaymentLinkService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/PaymentLinkService.java)

### 23 · Đo công sức kiểm duyệt

Thời gian chưa đo giữ null, không ước lượng. Touchless là duyệt không sửa theo audit, không phải chứng minh AI chính xác tuyệt đối theo GT.

- [ReviewEffortService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ReviewEffortService.java)
- [ReviewWorkflowService.java](../backendqt/backendqt/src/main/java/com/example/invoice/service/ReviewWorkflowService.java)
- [activeReviewTimer.ts](../quan%20tri/src/utils/activeReviewTimer.ts)

## Các ranh giới quan trọng

- Pipeline nghiệp vụ và benchmark file-only dùng helper chung nhưng khác persistence. Benchmark không tạo dữ liệu nghiệp vụ.
- PDF native có thể không chạy OCR ảnh; PDF hỗn hợp xử lý theo từng trang. Nhánh thực gọi là analyzePdfPages, không gộp mọi helper OCR vào một đường chạy.
- Có DocumentTextExtractionService/TesseractOcrService và helper analyzeVisionFallback trong mã. Không vẽ chúng thành đường chạy chính của processPdf/processImage khi call site đã đọc không gọi chúng. Chúng được giữ trong inventory để tra cứu.
- Adaptive OCR dùng điểm heuristic. FieldVerification dùng bằng chứng/rule. Hai loại điểm này không thay thế GT hoặc F1/CER/WER độc lập.
- APPROVE khóa chứng từ, không sinh phiếu thu/chi; tiền thực thu/chi cần xác nhận riêng.
- Gán nhãn live trên ứng dụng, evaluation bundle backend và GT benchmark Python là ba kho/luồng khác nhau.
- Public register bị chặn ở backend; DELETE chứng từ vật lý cũng bị chặn dù controller/client còn phương thức.
- Gộp PDF items có distinct trong implementation: nguy cơ mất các dòng trùng nội dung cần kiểm tra nghiệp vụ; sơ đồ ghi theo code, không ngầm coi là hành vi đúng mong muốn.
- GT ngoài dùng UNREADABLE để loại cả ô chưa gán nhãn theo quy ước tương thích; không diễn giải tất cả là không đọc được trên bản gốc.

## Tái tạo và kiểm tra

Chạy từ gốc dự án:

```powershell
.\benchmark_runner\.venv\Scripts\python.exe ".\luồng xử lí\tao_so_do.py"
```

Script kiểm mọi source link tồn tại, ID node không trùng, cạnh có đầu/cuối hợp lệ và XML parse được. `doi_chieu_nguon.json` giữ SHA-256, số dòng và entrypoint để biết khi source đổi cần rà lại. Script chỉ ghi ba artifact do nó quản lý trong thư mục này; không sửa source ứng dụng.

Chưa render bằng extension Draw.io của người dùng: công cụ trình duyệt không khả dụng trong phiên trước. Các tọa độ được đặt cố định theo cột/hàng và connector dùng orthogonal routing của draw.io. Khi xuất hình có thể dùng File → Export as → SVG/PDF trong editor.
