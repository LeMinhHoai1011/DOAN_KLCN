# Yêu cầu 03 — Pipeline OCR/AI, lỗi và retry

## Phạm vi

Giữ nguyên các thay đổi chưa commit của Yêu cầu 02. Không đổi entity, schema, migration,
datasource/ddl-auto, JWT/RBAC/company scope, initializer hoặc invoice_1. Không commit/push.
Không chạy backend thật hoặc xử lý lại chứng từ thật. Các phép ghi trong kiểm thử chỉ ở H2 độc lập.

## Khảo sát và nguyên nhân đã xác minh

- Upload đã commit file/chứng từ trước khi gọi AI. DocumentService.claimForAiProcessing dùng
  khóa ghi theo document và từ chối PROCESSING/APPROVED theo điều kiện hiện có.
- ProcessingJob/ProcessingAttempt/ProcessingLog đã có entity, quan hệ và unique(job, attempt_number),
  nhưng pipeline trước thay đổi không tạo job/attempt và chưa giới hạn retry xuyên request.
- DocumentTextExtractionService/PdfImageConversionService đã phân biệt text layer với scan.
- AdaptiveOcrService/ImageQualityAnalyzer/OpenCvImageProcessor đã đo độ nét, tương phản,
  nhiễu, kích thước chữ, skew và layout; thử profile hữu hạn, NORMAL giữ ảnh gốc, chọn kết quả tốt nhất
  với minimumQualityImprovement, không dùng bộ lọc cố định cho mọi ảnh.
- Nhánh Text kiểm tra confidence, words/lines/blocks, độ phủ trường và dấu tiếng Việt.
  Nhánh Vision đã có, không cần viết lại. Trước thay đổi Text JSON lỗi được gọi lại một lần trước Vision.
- GlobalExceptionHandler và upload wrapper có đường đưa message gốc của AI ra API.
  Upload wrapper còn có thể ép FAILED dù processor đã từ chối một request trùng.
- NaN confidence có thể khiến điểm quality trở thành NaN và lỗi khi chuyển sang BigDecimal để ghi log.
- API assessment của lượt trước thiếu prefix /api/v1 so với các API thực tế; đã sửa và thêm regression test đường dẫn.
- StorageService tạo object key UUID và chuẩn hóa tên; I/O MinIO thực tế ở DocumentService.
  Không sửa cấu hình/bucket/policy MinIO hoặc thao tác lên MinIO thật.

## Lỗi theo giai đoạn

PipelineFailure gồm errorCode, failedStage, retryable, attemptCount, timestamp, userMessage;
giữ alias error/message để frontend cũ vẫn đọc được thông điệp an toàn. UploadError giữ
code/message/retryable và bổ sung metadata tương ứng. technicalDetails không nằm trong DTO public.

| errorCode | Giai đoạn/ý nghĩa | Retry |
|---|---|---|
| INVALID_FILE | Kiểm tra tệp, MIME/signature, phần multipart, giới hạn dung lượng hoặc PDF không đọc được | Không |
| UPLOAD_FAILED | Lỗi upload khác trước khi chấp nhận chứng từ | Không tự động |
| STORAGE_FAILED | Đọc/ghi kho tệp bắt buộc | Chỉ lỗi kết nối/timeout/server tạm thời; không AccessDenied/NoSuchKey |
| OCR_FAILED | Nhận dạng lỗi, thiếu language data | Không tự thử lại cùng cấu hình |
| OCR_LOW_QUALITY | Chất lượng nhận dạng chưa đạt | Chuyển Vision/kiểm tra thủ công, không loop retry |
| AI_TIMEOUT | AI quá thời gian | Có, trong giới hạn chuỗi |
| AI_INVALID_RESPONSE | JSON/response/model/token limit không hợp lệ | Không retry cùng yêu cầu |
| EXTRACTION_FAILED | Lỗi trích xuất khác | Chỉ nguyên nhân kết nối/tạm thời đã nhận diện, như HTTP 429/503 |
| VALIDATION_FAILED | Không đáp ứng kiểm tra nghiệp vụ/cấu hình loại dữ liệu | NEED_REVIEW, không tự retry |
| DATABASE_ERROR | Lỗi truy cập/lưu DB | Chỉ TransientDataAccessException; schema/constraint không retry |

Giai đoạn dùng FILE_VALIDATION, UPLOAD, CLAIM, STORAGE, TEXT_EXTRACTION, OCR,
AI_EXTRACTION, VALIDATION, PERSISTENCE/DATABASE. Các từ chối điều khiển bổ sung:
PROCESSING_IN_PROGRESS, RETRY_NOT_ALLOWED, APPROVED_DOCUMENT (HTTP 409).

Thông điệp public dùng allowlist, không trả raw exception, provider body, SQL, token,
đường dẫn nội bộ hoặc stack trace. Logger backend ghi correlation document/job/stage,
exception class/root class/SQLState khi có; không ghi raw SQL/message trong technicalDetails mới.
Ollama không còn debug response/thinking preview và message lỗi gốc trong log failure mới.
Phân quyền truy cập file/console log trên môi trường triển khai cần được kiểm chứng riêng.

## Điều phối và bảo toàn dữ liệu

PipelineExecutionService.start thực hiện trong transaction ngắn:
1. Kiểm tra scope qua DocumentService.load.
2. Khóa document và refresh sau khi lấy khóa để tránh snapshot từ lần scope lookup trước lúc chờ.
3. Chặn PROCESSING/APPROVED; kiểm tra lỗi trước có retryable và số attempt còn lại.
4. Claim PROCESSING và lưu job/attempt RUNNING trong cùng transaction.

Transaction claim của pipeline kết thúc trước OCR/AI/đọc MinIO. Mỗi lần bắt đầu được commit trước khi gọi remote.
Riêng upload phiên bản giữ khóa trong transaction cập nhật phiên bản hiện có, bao gồm ghi MinIO;
cần kiểm chứng timeout/độ trễ lưu trữ khi triển khai để tránh chờ khóa kéo dài.
Kết thúc ghi job/attempt và log liên kết job; failure lưu JSON public an toàn trong cột error_message TEXT hiện có.
ProcessingLog có START/COMPLETE hoặc bước lỗi và quan hệ job; các log chi tiết hiện có vẫn giữ theo document.
Lịch sử sắp theo job ID và attempt number, không phụ thuộc đồng hồ giữa các instance.

- Tối đa **3 attempt cho một chuỗi retry lỗi tạm thời**, gồm lần đầu và tối đa 2 lần người dùng thử lại.
- Không có background scheduler tự retry hoặc chạy lại toàn bộ dữ liệu cũ.
- /retry chỉ nhận FAILED tạm thời; /reprocess trên FAILED cũng chịu cùng giới hạn, không bypass.
- Reprocess một kết quả nghiệp vụ hợp lệ/NEED_REVIEW là một yêu cầu phân tích mới theo API hiện có.
- Legacy FAILED chưa có lịch sử lỗi chuẩn bị chặn fail-closed, không đoán là lỗi tạm thời.
- Khóa document và unique(job, attempt_number) ngăn tạo attempt trùng khi nhiều request.
- Kiểm tra thêm job RUNNING độc lập với nhãn document; đổi status qua API không mở được lượt trùng.
  Giới hạn chuỗi FAILED lấy từ lịch sử job, không bị reset bằng cách gán document về UPLOADED.
- DocumentService.update/uploadNewVersion khóa và refresh document, từ chối mutation khi document hoặc job
  đang xử lý, tránh thay input giữa một phiên AI. Quyền và scope trước/sau khóa giữ nguyên.
- Không xóa job/attempt cũ. Invoice/classification chỉnh tay, field_corrections và chứng từ APPROVED
  tiếp tục được bảo vệ theo Yêu cầu 02. Persist vẫn upsert invoice/classification và giữ field có correction.
- Validation/unresolved type đi NEED_REVIEW; lỗi kỹ thuật mới đi FAILED. Không tự APPROVED hoặc tạo giao dịch.
- Không nhầm NEED_REVIEW đã hoàn tất với lỗi kỹ thuật: lịch sử có nhóm success/failed/needsReview/running riêng.

Nếu ghi history thất bại do DB mất kết nối thì API vẫn trả lỗi an toàn, nhưng không thể bảo đảm DB đã lưu
được failure. Không tự mở khóa hoặc khởi chạy lại khi chưa biết attempt cũ còn hoạt động hay không.

## Fallback và OCR

PDF text layer → Text. PDF scan/JPG/JPEG/PNG → adaptive preprocessing → Tesseract → quality:
OCR tốt → Text; OCR kém/nghi mất dấu/layout thiếu → Vision. Khi Text trả lỗi định dạng,
chỉ thử **một Vision fallback**, không lặp lại cùng Text JSON lỗi. Timeout/kết nối không tự chuyển
sang Vision để gửi tiếp yêu cầu đến dịch vụ đang lỗi. Mỗi trang có số nhánh hữu hạn; PDF chịu giới hạn trang hiện có.

OkHttp Ollama tắt implicit retryOnConnectionFailure; giữ call/read/write/connect timeout cấu hình.
HTTP 503 dù body không phải JSON vẫn được nhận diện là lỗi dịch vụ tạm thời; TLS/certificate không retry.
Điểm quality không hữu hạn được xử lý an toàn cho routing; confidence đo được vẫn theo quy tắc Yêu cầu 02,
không thay null thành 100. Cảnh báo OCR public được chuẩn hóa, không trả đường dẫn native/language data.

## API/UI

- GET /api/v1/documents/{id}/processing-history: chỉ người có quyền xem/scope; trả các attempt,
  thời gian, failure metadata, retryAllowed, maxAttempts và thống kê/phân loại nguyên nhân theo chứng từ.
- POST /api/v1/documents/{id}/retry: giữ cùng authority CREATE/UPDATE/ADMIN như process/reprocess;
  backend quyết định điều kiện, không tin frontend.
- API cũ /process, /reprocess, /upload vẫn tồn tại; upload lỗi giữ các field code/message/retryable cũ.
- Danh sách đã có lọc FAILED, được giữ lại; thêm nguyên nhân ngắn gọn và nút retry khi server cho phép.
- Chi tiết thêm ProcessingHistoryCard: bước lỗi, timestamp, số lần thử, lịch sử, kết quả gần nhất,
  số success/failed/needsReview/running và failuresByCode. Giữ sidebar, màu, bảng và card hiện có.
- Frontend khóa nút đang chạy; helper kiểm tra thêm FAILED, chưa APPROVED, chưa hết lượt và trạng thái
  attempt gần nhất. Quyền backend luôn là quyết định cuối cùng.
- Tất cả dữ liệu nghiệp vụ lấy từ API; không mock dữ liệu trong giao diện.

## Bằng chứng kiểm thử

Kết quả cuối ngày 2026-10-16:
- Backend `mvn test` trên Java 21, dependencies offline, mock/H2: **351 tổng, 338 PASS,
  0 FAIL/Error, 13 SKIP; BUILD SUCCESS**, exit code 0.
- PipelineRetryIntegrationTest: **9 PASS**; DocumentProcessingMutationGuardTest: **2 PASS**.
- Frontend `npm run build`: **PASS**; `npm test`: **18 PASS, 0 FAIL/SKIP**.
- `git diff --check`: PASS. Log bằng chứng: backendqt/backendqt/request03-test.log (được Git ignore).
- Các test yêu cầu runtime thật vẫn SKIP; không tính chúng là PASS.

PipelineRetryIntegrationTest trên H2:
- 2 thread retry đồng thời: đúng 1 request được nhận, chỉ 1 attempt mới.
- Chuỗi timeout: 3 thất bại, AI_TIMEOUT=3, không tạo attempt thứ 4; số thứ tự [1,2,3].
- Đảo timestamp giả lập clock skew: attempt gần nhất vẫn xác định đúng bằng thứ tự logic.
- Reset nhãn document giả lập: không vượt được job RUNNING hoặc giới hạn chuỗi FAILED.
- Retry thành công sau timeout: 1 success/1 failure, invoice thủ công giữ ID/số/tổng tiền,
  correction giữ nguyên và chỉ có 1 classification, không sinh field trùng.
- 3 case chạy processor thật với dependency mock: MinIO connection lost, Ollama timeout,
  invalid JSON; mỗi case chỉ 1 attempt, mã/bước/retryable đúng, không tự gọi lại AI.
- APPROVED, deterministic failure, legacy không rõ nguyên nhân và thiếu scope đều bị chặn.
- NEED_REVIEW hoàn tất không bị đếm là đang chạy hoặc lỗi kỹ thuật.
- DocumentProcessingMutationGuardTest: chặn đổi metadata và upload phiên bản mới trước khi ghi MinIO/version,
  kể cả khi status label khác PROCESSING nhưng job vẫn RUNNING.

Test khác: API không trả secret/stack trace/SQL; upload quá lớn có metadata; OCR NaN không làm hỏng quality;
fallback Text/Vision hữu hạn; PDF text layer/scan, JPG/PNG, ảnh nghiêng/mờ, layout/dòng/bảng và tiếng Việt
được bao phủ bởi các test hiện có và regression. Frontend test đường dẫn API và điều kiện hiển thị retry.

Các số liệu trên là fixture test độc lập, **không phải thống kê chứng từ thật**.

## File của Yêu cầu 03

Backend thêm: dto/document/PipelineFailure.java, PipelineHistory.java;
exception/PipelineException.java; repository/ProcessingJobRepository.java,
ProcessingAttemptRepository.java; service/PipelineExecutionService.java, PipelineFailures.java.

Backend sửa: ai/OllamaAiProvider.java; controller/DocumentController.java;
dto/document/DocumentUploadResponse.java; exception/GlobalExceptionHandler.java;
service/DocumentAiProcessingService.java, DocumentUploadProcessingService.java,
DocumentService.java, AdaptiveOcrService.java.

Tests thêm: PipelineRetryIntegrationTest, controller/PipelineErrorResponseTest,
service/PipelineFailuresTest, service/DocumentProcessingMutationGuardTest. Tests sửa: ai/OllamaAiProviderTest,
service/AdaptiveOcrServiceTest, DocumentVisionFallbackPageSelectionTest.

Frontend thêm: components/ProcessingHistoryCard.tsx, utils/pipelinePresentation.ts,
pipelinePresentation.test.mjs. Sửa: package.json, services/documentService.ts,
pages/accountant/AccountantDocuments.tsx, AccountantDocumentDetail.tsx.

## Giới hạn và kiểm chứng runtime còn lại

- Chưa gọi runtime thật PostgreSQL/MinIO/Ollama hoặc chứng minh chất lượng trên tài liệu doanh nghiệp thật;
  chưa kiểm thử trình duyệt E2E. Không suy ra độ chính xác OCR/AI từ test mock/H2.
- Không có lease/watchdog tự thu hồi RUNNING khi JVM chết; cần kiểm tra vận hành trước xử lý thủ công,
  không được tự reset trạng thái/khởi chạy lại vì có thể còn worker đang hoạt động.
- Không kiểm thử cạnh tranh giữa chỉnh sửa thủ công và một phiên AI đã chạy; test bảo toàn hiện tại
  kiểm tra dữ liệu đã được chỉnh sửa trước retry và hai request retry đồng thời.
- Nếu DB không ghi được failure thì history có thể còn RUNNING; không che bằng trạng thái thành công giả.
- Lịch sử trả toàn bộ attempt theo document, chưa phân trang; list tải history cho FAILED trong trang hiện tại.
- Giới hạn 3 lần là hằng số phía server, không phải số HTTP request AI (PDF có nhiều trang/nhánh khác nhau).
- Job/attempt tái sử dụng mapping/cột hiện có, không migration. Schema/constraint vật lý PostgreSQL và
  timeout MinIO trong môi trường triển khai chưa được xác minh ở lượt này; không tự ALTER để che drift.
- Backend logger không được public qua API mới, nhưng ACL/retention của log thực tế cần kiểm tra khi triển khai.
- Vite có cảnh báo bundle lớn; không đổi kiến trúc frontend để xử lý ngoài phạm vi.
