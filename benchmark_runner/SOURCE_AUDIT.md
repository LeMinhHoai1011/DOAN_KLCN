# Source audit — OCR/AI benchmark 1.0

Đối chiếu source thực tế ngày 2026-11-05. Backend Java 21/Spring Boot; không có API OCR/AI file-only trả đủ pipeline trước thay đổi này.

Các đường dẫn Java dưới đây tính từ `backendqt/backendqt/src/main/java/com/example/invoice/`.

| Thành phần | File/class đã kiểm tra | Tái sử dụng | Ghi DB/MinIO? | Phương án |
|---|---|---|---|---|
| Upload/xử lý nghiệp vụ | `controller/DocumentController.java`, `service/DocumentService.java` | Không gọi | Có: documents, versions, logs, MinIO | Không dùng endpoint upload/process/reprocess/retry |
| Điều phối | `service/DocumentAiProcessingService.java` | Chỉ các helper tính toán | `processAttempt/processImage/processPdf/analyzePdfPages` có persist | Không gọi các entrypoint đó; tạo instance riêng với toàn bộ dependency DB/storage bằng null |
| OCR adaptive | `service/AdaptiveOcrService.java` | Có | Không | Giữ nguyên profile, chọn ảnh, ngưỡng chất lượng và retry |
| Tesseract | `service/Tess4jOcrService.java` | Có | Không | Dùng cùng tessdata và cấu hình backend; lỗi OCR giữ dạng unavailable |
| Chất lượng/tiền xử lý | `service/ImageQualityAnalyzer.java`, `service/OpenCvImageProcessor.java` | Có, qua adaptive OCR | Không | Không sửa thuật toán |
| PDF | `service/PdfImageConversionService.java` | Có | Không | Native text từng trang; chỉ render nếu có trang không dùng được text layer |
| Bố cục/chuẩn hóa OCR | `service/OcrLayoutReconstructor.java`, `service/OcrTextNormalizer.java` | Có | Không | Dùng đúng helper pipeline |
| AI dispatch | `ai/AiProcessingService.java`, `ai/AiProvider.java` | Có | Không | Không cần documentId, gọi provider đang cấu hình |
| Ollama | `ai/OllamaAiProvider.java`, `config/AiProperties.java`, `config/AiConfiguration.java` | Có | Không | Giữ model, timeout, context, output budget; không gọi Python model |
| Prompt | `ai/AiDocumentPromptFactory.java` | Có | Không | Dùng đúng factory và text-first suffix; options cung cấp catalog/company tương đương nghiệp vụ |
| Parse/validate | `ai/AiDocumentResultParser.java`, `ai/AiDocumentResultValidator.java` | Có | Không | Provider gọi parser gốc; adapter validate kết quả như hệ thống |
| Persist | `service/DocumentAiResultPersistenceService.java`, `service/OCRResultService.java`, `service/ProcessingLogService.java` | Không | Có | Không inject vào adapter; không ghi OCR/classification/invoice/log nghiệp vụ |
| Test AI cũ | `controller/AiTestController.java` | Không đủ | Không | `/api/v1/ai/test` là raw image + custom prompt, không tương đương pipeline; không dùng |
| JWT/phân quyền | `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`, `security/JwtService.java`, `security/CustomUserDetailsService.java` | Có | Đọc tài khoản, không tạo dữ liệu benchmark | Endpoint mới yêu cầu JWT + ADMIN; xác nhận GT dùng `/api/v1/users/me` và role ADMIN/ACCOUNTANT |
| Schema trích xuất | `dto/ai/AiDocumentResult.java`, `verification/CoreFieldProfiles.java` | Đối chiếu | Không | CamelCase theo DTO, 6 loại chứng từ, header/items/fields/extraFields |

## Các kết luận và giới hạn quan trọng

- OCR/AI thuần không bắt buộc có documentId. Pipeline nghiệp vụ thì có, vì claim trạng thái, đọc MinIO và persist.
- Adapter mới chỉ dùng helper `analyzeSingleImage`, `analyzeDigitalPdfText`, `reconstructLayout`, `aggregateOcr`, `aggregatePageResults`, `resolveCompanyRole`, `addMissingInvoiceItemsWarning`. 5 helper private được đổi sang package-private; thân hàm không thay đổi.
- Adapter xử lý từng trang với cùng quy tắc native PDF text/OCR/Vision. Khác biệt có chủ ý: không đọc catalog/company từ DB; phải cung cấp đúng `options` trong config để so sánh cùng điều kiện. Không có options tương đương thì không khẳng định cùng đầu vào prompt với chứng từ nghiệp vụ.
- Khi chạy chế độ AI, OCR chạy trong request `/ai` đúng một pipeline; Python không gọi `/ocr` lần nữa. Output vẫn tách lưu `ocr.json` và `ai.json`.
- `parsed` là kết quả parser + gộp trang + hậu xử lý trước validator; `normalized.result` là kết quả validator. `calls[].result.rawResponse` giữ phản hồi provider của từng lần thành công, kể cả kết quả từng trang. Nếu provider/parser ném lỗi trước khi trả kết quả, raw không được public service trả ra; adapter lưu mã lỗi và nhánh đã thử, không giả tạo raw response.
- `aggregatePageResults` gốc có `.distinct()` với items, nên hai dòng giống hệt nhau trên PDF nhiều trang có thể bị gộp. Không sửa để tránh đổi hệ thống. Evaluator không gộp dòng; bảng dự đoán cuối bị mất dòng sẽ chịu FN, còn raw từng trang giữ để tra cứu.
- `VERIFIED` của tính năng field verification nghiệp vụ không được dùng làm GT benchmark. Chỉ JSON được người đối chiếu và xác nhận độc lập mới được tính metric.
- Class hash, config hash và model digest được ghi khi chạy; không chỉ dựa vào tên model. Runner không cho AI chạy khi không pin được digest. Chưa có pinned model digest cho dữ liệu lịch sử sẽ không được coi là cùng run.
- Metadata `engineVersion` là phiên bản thư viện Tess4J từ manifest nếu có, không được giả nhận là phiên bản binary Tesseract. `ocrVersion`/`tess4jAdapterVersion` là hash class backend.
- JWT trong môi trường production vẫn đọc tài khoản từ DB như hệ thống hiện tại. “Không ghi DB” không đồng nghĩa không cần DB để backend nghiệp vụ khởi động/xác thực. Test HTTP dùng context riêng không có datasource/JPA/MinIO.
- GT lưu trên filesystem tin cậy, xác thực người xác nhận qua API. Không phải chữ ký số chống sửa file bởi người có quyền ghi thư mục.
- Không sửa frontend, schema, entity, migration, model, prompt, parser, ngưỡng hoặc logic fallback. API mới opt-in qua `BENCHMARK_ENABLED=true`, mặc định không được đăng ký.
- `service/BenchmarkLogPrivacy.java` chỉ chặn log trên worker có MDC `benchmark.fileOnly`; luồng nghiệp vụ ngoài worker không bị đổi log. Output có `toString()` rút gọn để DEBUG response không in OCR/AI.
