# Yêu cầu 07 — Đánh giá chất lượng OCR/AI

Ngày khảo sát: 2026-10-17. Phạm vi: đọc source, thêm bộ tính chỉ số/API read-only, giao diện và công cụ đóng gói dữ liệu đánh giá độc lập. Không chạy backend trên PostgreSQL, không truy vấn/ghi dữ liệu thật, không migration, không tự chạy OCR/AI trên chứng từ cũ.

## 1. Hiện trạng đã xác minh từ source

| Thành phần | Dữ liệu có thể khai thác | Giới hạn đối với đánh giá |
|---|---|---|
| `OCRResult` / `ocr_results` | rawText, layoutJson, engine/modelVersion/language/sourceType, confidence, processingTime, processedAt | Không có bản chép tay độc lập và hash bộ mẫu; confidence không phải CER/WER. Text layer PDF không phải OCR Tesseract. |
| `Classification` / `ai_classifications` | document, accountingCategory, predictedLabel, modelName/modelVersion, status, aiGenerated | Tên Java là Classification, không có AiClassification.java. Category kế toán khác loại chứng từ. Nhãn AI không phải ground truth. |
| `ExtractedField` / `extracted_fields` | fieldName/value, source, confidence, corrections | Correction chỉ xác nhận một trường, không chứng minh các trường còn lại hoặc toàn bộ văn bản đúng; không tự lấy effective value làm chuẩn rồi so với chính nó. |
| `Invoice` / `invoice_data` | seller/buyer, MST, số/ngày, tiền/thuế và InvoiceItem | Tên Java là Invoice, không phải InvoiceData. Bản hiện tại có thể được sửa; không đủ bằng chứng gắn snapshot dự đoán với từng lần chạy. |
| `InvoiceItem` / `invoice_items` | productName, quantity, unit, unitPrice, taxRate, taxAmount, amount | Chưa có bộ hàng hóa chuẩn thủ công. Cần chốt thứ tự dòng và quy tắc ô trước khi đo. |
| `FieldCorrection`, `ClassificationCorrection` | người sửa, thời điểm, giá trị/category trước-sau, review | Có thể chọn làm ứng viên để người kiểm tra xác minh lại; không tự động là toàn bộ ground truth. |
| `ProcessingJob`, `ProcessingAttempt` | startedAt/completedAt/status, attemptNumber, errorMessage | Có thể phục vụ vận hành; job retry khác reprocess tạo job mới. Không tự lấy mọi lần START làm một mẫu benchmark độc lập. |
| `ProcessingLog` | processType/agentStep/model/time/status; assessment chia chunk | Execution time có thể là một bước; message giới hạn 255 ký tự. Một số quyết định Text/Vision nằm ở application log, chưa bảo đảm có telemetry có cấu trúc cho mọi trang/mọi lần. Không suy diễn fallback=false nếu thiếu log. |
| DashboardService + `/dashboard/statistics` | tổng chứng từ, loại, trạng thái; scope hiện có | Đây là thống kê nghiệp vụ, chưa có ground truth, confusion matrix hoặc accuracy theo trường. Giữ nguyên API và cách scope. |

Đã đọc `DocumentAiProcessingService`, `DocumentTextExtractionService`, `ProcessingLogService`, `PipelineExecutionService`, DashboardController/Service, SecurityConfig và trang SystemStatistics. Pipeline hiện có OCR thích ứng, đánh giá chất lượng, Text khi tốt, Vision khi OCR kém/JSON lỗi; không thêm vòng fallback hoặc thay pipeline trong yêu cầu này.

Đã có `RealInvoiceOcrBenchmarkTest` và `RealInvoiceVisionBenchmarkTest`, opt-in bằng OCR_REAL_PDF_PATH / OLLAMA_RUNTIME_MODEL. Chúng đối chiếu một số trường cố định, chưa phải bộ chuẩn chung cho A/B/C, chưa cung cấp đầy đủ CER/WER hay confusion matrix. Không tự chạy hai test này trên file thật và không dùng output trước đây làm ground truth mới.

Không tìm thấy tập ground truth độc lập đã được kiểm tra thủ công trong repository. Vì vậy chưa có số liệu chất lượng thực tế đủ điều kiện công bố.

## 2. Thiết kế bộ chuẩn và thực nghiệm

Tập mẫu ngoài Git và ngoài database nghiệp vụ, được phép sử dụng và đã che dữ liệu nhạy cảm trước khi chốt mẫu. Nếu che mẫu, hash và ground truth phải theo chính bản đã che. Phân tách tập phát triển prompt/ngưỡng và tập đánh giá cuối; không điều chỉnh prompt dựa trên kết quả test cuối. Tài liệu cùng nguồn/cùng nhà cung cấp cần được phân nhóm khi chia tập để tránh rò rỉ.

Nhãn đa lựa chọn: CLEAR, BLUR, SKEW, SCANNED_PDF, TEXT_LAYER_PDF, MULTILINE, VIETNAMESE; thêm loại INVOICE, RECEIPT, PAYMENT_VOUCHER, RECEIPT_VOUCHER, CONTRACT và OTHER nếu cần. Một mẫu có nhiều nhãn nhưng chỉ xuất hiện một lần theo hash. Không tính một ảnh vừa mờ vừa nghiêng thành hai mẫu độc lập. Đề xuất pilot 5–10 mẫu mỗi nhóm để phát hiện lỗi quy trình, sau đó tối thiểu 20 mẫu cho từng nhóm quan trọng nếu nguồn dữ liệu cho phép. Đây là kế hoạch chọn mẫu, không phải số lượng thực tế đã có; công bố số mẫu thực và giới hạn đại diện.

Quy trình mỗi mẫu:

1. Người A chép văn bản theo thứ tự đọc đã định (trang tăng dần, tiêu đề, dòng bảng); lưu đầy đủ dấu tiếng Việt, ký hiệu, số và dấu câu.
2. Nhập nhãn loại và các trường/ô hàng hóa có trên tài liệu. Không dùng AI điền phần chưa rõ. Không rõ thì loại trường khỏi bộ chấm có ghi chú trong hồ sơ kiểm tra; mẫu cần adjudication trước khi đưa vào tập cuối.
3. Người B đối chiếu tài liệu gốc; xử lý bất đồng; ghi reviewer, thời điểm và biên bản bên ngoài. `verifiedBy`/`verifiedAt` là khai báo kiểm tra, không phải bằng chứng hệ thống đã tự xác thực người kiểm tra.
4. Đóng băng asset SHA256, datasetVersion và snapshot ground truth. Không sửa nhãn để khớp output model. Mỗi thay đổi ground truth phải tăng phiên bản và giữ bundle cũ.

Các cột chuẩn extraction dùng tên hiện tại của DTO/entity: invoiceNumber, invoiceSeries, invoiceDate, sellerName/TaxCode/Address, buyerName/TaxCode/Address, subtotal, vatAmount, totalAmount, paymentMethod… Hàng hóa dùng productName, quantity, unit, unitPrice, taxRate, taxAmount, amount. Các loại khác dùng mapping `extraFields` đã chốt ở Yêu cầu 02; không ép chúng có InvoiceItem.

Trong V1 scorer dùng exact match sau Unicode NFC và chuẩn hóa khoảng trắng, giữ chữ hoa/thường, dấu và dấu câu. Không tự chuyển ngày hoặc số tiền của prediction để che lỗi. Protocol cần yêu cầu cùng định dạng chuẩn đầu ra (ngày ISO và decimal không phân tách hàng nghìn). Sai định dạng được tính sai; muốn thêm semantic numeric/date matcher phải phát hành metricVersion mới và chạy lại cả A/B/C. Trường không có/không thuộc mẫu không đưa vào denominator. Dự đoán trường ngoài danh sách chuẩn báo `extraFields`, chưa thể gọi tất cả là hallucination vì có thể là trường thật chưa được chấm.

Thực nghiệm A/B/C:

| Phương án | Quy tắc cố định trước khi đo |
|---|---|
| A | Cùng file, raster/DPI cố định nếu PDF, Tesseract vie+eng, không preprocessing, AI Text; lỗi OCR/Text được ghi FAILED, không âm thầm Vision. |
| B | Cùng đầu vào/DPI/Tesseract/prompt/model; preprocessing với cấu hình được chốt và ghi profile thực chọn; AI Text, không Vision. |
| C | Cùng đầu vào/model/prompt; preprocessing thích ứng + quality thresholds đã chốt, Text khi đạt, Vision fallback có giới hạn khi không đạt/response lỗi. Ghi chính xác có fallback hay không cho từng mẫu. |

PDF có text layer vẫn cần nhãn riêng. Nếu A/B bắt buộc Tesseract thì raster toàn trang; không âm thầm dùng text layer để tăng kết quả. Nếu muốn so sánh PDFBox text layer, phải tạo thí nghiệm riêng với tên và protocol riêng, không đổi định nghĩa A/B. Với PDF nhiều trang: ghi đủ trang, hợp nhất theo thứ tự đọc đã chốt; fallback=true khi có ít nhất một trang dùng fallback. Không chỉ lấy trang đầu rồi tuyên bố kết quả cho cả PDF.

Giữ cùng model digest, quantization, prompt hash, context/output budget, temperature/seed, language/tessdata version/hash, DPI, OCR version, hardware/OS/GPU/VRAM/RAM/threads, số worker, timeout và chính sách retry. Ghi cấu hình preprocessing/quality thresholds/page merge. Phân biệt warm-up và đo chính thức; warm-up không cộng mẫu; báo cold/warm trong môi trường. Đảo hoặc cân bằng thứ tự A/B/C, không chạy song song nếu tài nguyên dùng chung làm lệch latency. Ghi wall-clock monotonic từ đầu preprocessing đến parse/validation hoàn tất, bao gồm retry/fallback; không bao gồm thời gian người duyệt và không suy ra từ confidence. Bản ghi FAILED vẫn giữ thời gian nếu đo được.

API chỉ gắn `comparable=true` khi bộ lọc còn đúng một run đầy đủ cho mỗi A/B/C và cùng hash ground truth, model digest, environment, codeRevision. Đây là kiểm tra điều kiện tối thiểu, không thay kiểm tra protocol thủ công; cấu hình strategy cố ý khác nhau. Không tự tính chênh lệch từ hai tập mẫu khác nhau. Nếu chạy nhiều lần để tính biến thiên/CI, lưu từng run, xuất JSON để phân tích paired/bootstrap bên ngoài; V1 không tự công bố CI hay significance.

## 3. Công thức và denominator

- CER = tổng edit distance theo Unicode code points / tổng ký tự reference. WER = tổng edit distance theo token tách khoảng trắng / tổng từ reference. NFC, trim và co khoảng trắng áp dụng cho hai phía; không bỏ dấu tiếng Việt. Đây là micro/corpus ratio, không lấy trung bình tỷ lệ mỗi mẫu. CER/WER có thể >100% khi nhiều insertion. Reference rỗng không tự thành 100%; denominator tổng bằng 0 trả null.
- Field accuracy = correct / expected; missing rate = missing / expected; incorrect rate = nonempty wrong / expected. Ba nhóm không chồng lấn; missing không bị loại denominator. OCR-field score độc lập AI-extraction score. Nếu không có bản đối chiếu OCR-field thì null/không có dòng, không tự lấy AI fields làm OCR fields.
- Hàng hóa: mỗi ô reference theo thứ tự dòng là một expected cell. Dòng/ô thiếu tính missing, giá trị khác tính incorrect, dòng thừa báo riêng. Không nhận đây là perfect invoice/table score; cần xem missing/incorrect và extra rows cùng accuracy. V1 chưa có row matching theo nội dung hoặc table structure recognition score.
- Classification V1 đo **loại chứng từ**, không phải accounting category. Accuracy = đúng loại / số mẫu có nhãn chuẩn. TP/FP/FN từ confusion matrix (dòng truth, cột prediction). Thiếu nhãn/AI lỗi đi vào `__NO_PREDICTION__`, vẫn sai và tạo FN cho lớp chuẩn. Precision = TP/(TP+FP); recall = TP/(TP+FN); F1 harmonic mean; macro là trung bình theo union lớp thật + lớp dự đoán, loại sentinel. Khi một lớp không có predicted/support, zero_division=0; toàn bộ chưa có mẫu thì null. Không gộp nhãn document type với code danh mục kế toán.
- Processing success rate = số result SUCCESS / số result đã ghi nhận; failure rate tương tự FAILED. SUCCESS gồm hoàn tất NEED_REVIEW, không có nghĩa đúng nội dung, đã duyệt hoặc đã thanh toán. Các run chưa đủ toàn bộ mẫu phải hiển thị coverage và không được so sánh đầy đủ.
- Average processing time = tổng durationMs / timedSamples, bao gồm result failed có thời gian. durationMs=null không tính vào mẫu đo, không coi là 0.
- Vision fallback rate = số result có visionFallback=true / fallbackObserved. `null` = không đo được, khác `false`.
- Retry rate = số result attemptCount>1 / attemptsObserved. attemptCount là tổng lần thử pipeline cho một mẫu trong run, bắt đầu từ 1; không dùng số gọi Vision làm retry nếu đó là nhánh fallback của cùng attempt.
- Error breakdown theo failureCode của FAILED. Chỉ số quality tính cả lỗi pipeline thiếu output để tránh survivor bias. confidence không được chấp nhận trong schema đánh giá.

## 4. Tách lưu trữ, API và xuất

Không thêm Entity, Repository, bảng hoặc migration. `EvaluationService` không phụ thuộc repository/service nghiệp vụ, không gọi OCR/AI và không ghi file. `app.evaluation.directory` mặc định rỗng qua @Value; runtime có thể đặt `APP_EVALUATION_DIRECTORY` tới thư mục riêng **chỉ khi bộ dữ liệu đã được kiểm tra và cho phép sử dụng**. Không sửa cấu hình production hiện tại.

Thư mục chỉ đọc cho backend: benchmark.json, assets/<sha256>.bin, configs/<sha256>.json, evidence/<sha256>.json. Ngoài Git, đặt ACL giới hạn cho người chuẩn bị và backend; file nguồn/evidence có thể chứa văn bản hóa đơn nên cần chính sách lưu trữ/xóa và không gửi chúng qua API. Cập nhật bundle bằng thư mục mới sau khi đóng băng; không ghi đè file trong lúc server đọc. Hash bảo vệ tính toàn vẹn theo bundle, không chứng minh model đã chạy hoặc ground truth trung thực; cần giữ biên bản và log gốc để kiểm tra độc lập.

API ADMIN-only, qua JWT/method security hiện có:

- GET `/api/v1/evaluation/statistics?model=...&strategy=A|B|C&from=<ISO-instant>&to=<ISO-instant>`.
- GET `/api/v1/evaluation/export` cùng bộ lọc, tải JSON gồm aggregate, per-type, per-field, confusion matrix, run metadata và hash để đối chiếu với bundle gốc.
- Chưa cấu hình: 200 NOT_CONFIGURED, runs=[]; đã cấu hình hợp lệ nhưng không có run phù hợp: 200 NO_TEST_DATA. Không có quyền: 403; chưa đăng nhập: 401. Dữ liệu/hashes/path không hợp lệ hoặc thiếu artifact: 422 EVALUATION_DATA_INVALID_OR_UNAVAILABLE, không trả path hay raw content; không đổi lỗi thành tập rỗng. Bộ lọc sai: 400.
- Không có endpoint upload/import vào DB, không có endpoint chạy AI/reprocess. Không đọc dữ liệu của Company khác; mô-đun không dùng business IDs/Company và chỉ phục vụ bộ mẫu độc lập do quản trị quản lý. Không cấp thêm quyền USER/EMPLOYEE/ACCOUNTANT.
- Limits V1: benchmark JSON <=8 MB, <=500 mẫu, <=20 run, mỗi text <=20.000 UTF-16 code units, <=100 trường, <=500 dòng hàng; artifact <=25 MB; edit-distance pair budget mỗi run <=100 triệu phép so sánh xấp xỉ. Bộ lớn cần chia thành các nghiên cứu/phiên bản được kiểm tra; không cắt text hoặc bỏ mẫu âm thầm để lấy điểm đẹp.

Frontend giữ trang/bố cục hiện có và thống kê nghiệp vụ, thêm OcrAiPerformance: filter model/strategy/local date, bảng so sánh, chart theo loại, field scores, item score, P/R/F1, confusion matrix, breakdown lỗi, thời gian/fallback/retry và số mẫu đo; export JSON. Null hiển thị Chưa xác định. NOT_CONFIGURED/NO_TEST_DATA hiển thị Chưa có dữ liệu kiểm thử. Lỗi 403/422/500 có trạng thái lỗi riêng.

## 5. Chuẩn bị dữ liệu mà không ghi nghiệp vụ

`tools/evaluation/prepare-bundle.mjs` chỉ đóng gói file đã chuẩn bị, không sinh ground truth/prediction, không gọi model, không kết nối DB. Cách dùng sau khi đã có dữ liệu thật:

```powershell
node tools/evaluation/prepare-bundle.mjs <thu_muc_nguon> <thu_muc_dich_moi_doc_lap>
```

Đích phải chưa tồn tại và tách khỏi nguồn; không ghi đè. Nguồn `manifest.json` có schemaVersion=1, datasetId/datasetVersion, samples[], runs[]. Mỗi sample dùng các thuộc tính của `EvaluationData.Sample` nhưng thay assetSha256 bằng đường dẫn tương đối assetFile. Reviewer/time bắt buộc. `ocrText`, fields, ocrFields, items dùng null khi không được chấm, không dùng nội dung AI làm giá trị chuẩn.

Mỗi run khai báo id, startedAt, configFile và results=[{evidenceFile: đường_dẫn_tương_đối}]. Config JSON có strategy, model, modelDigest (64 hex), environment (chuỗi điều kiện đo), codeRevision và settings object không rỗng. Giữ đầy đủ tham số ở settings như protocol trên. Builder tính config hash, sao chép giữ nguyên nội dung config.

Mỗi evidence JSON là **snapshot output thực tế của một mẫu**, đủ key: sampleId, assetSha256, outcome, failureCode, durationMs, visionFallback, attemptCount, ocrText, ocrFields, fields, items, predictedType. Không có evidenceSha256 trong file này; builder tính sau. Null được ghi rõ cho giá trị chưa có. assetSha256 là hash file mẫu được dùng khi chạy, không lấy file khác có tên giống. outcome chỉ SUCCESS hoặc FAILED; lỗi kỹ thuật có failureCode, không dùng NEED_REVIEW làm technical failure. Builder không tự điền timestamp/duration/fallback hay đoán kết quả. Giữ thêm application log gốc ở kho hạn chế để xác minh snapshot; không đặt token/password/URL chứa credential trong config hoặc metadata.

Backend kiểm tra mẫu tồn tại/hash, reviewer/time, ID không trùng, hash asset khớp result, hash config khớp metadata run, hash evidence và nội dung evidence khớp result, khóa lạ bị từ chối (bao gồm confidence). API export không chứa text/field value/items/reviewer/path/secret, chỉ aggregate và các metadata không nhạy cảm.

## 6. File thay đổi trong Yêu cầu 07

- Mới: `evaluation/EvaluationData.java`, `EvaluationMetrics.java`, `EvaluationService.java`; `controller/EvaluationController.java`.
- Mới: `EvaluationMetricsTest`, `EvaluationServiceTest`, `EvaluationApiIntegrationTest`.
- Mới: frontend `services/evaluationService.ts`, `components/OcrAiPerformance.tsx`, `utils/evaluationPresentation.ts` và test.
- Sửa tối thiểu: `pages/admin/SystemStatistics.tsx` gắn khu vực mới; `quan tri/package.json` thêm test mới, giữ test cũ.
- Mới: `tools/evaluation/prepare-bundle.mjs` và test; báo cáo này.
- Không sửa DashboardService/Controller, Entity nghiệp vụ, DTO nghiệp vụ, SecurityConfig, JWT/Auth, pipeline OCR/AI, invoice_1/ hoặc migration. Mọi thay đổi Requests 02–06 đã có trước lượt này được giữ nguyên; Git dirty từ đầu, không commit/push.

## 7. Kiểm thử và điều kiện nghiệm thu

Kết quả cuối: backend `mvn clean test` 411 test tổng, **398 PASS, 0 FAIL/Error, 13 SKIP** (24 test mới của mô-đun đánh giá đều đạt); frontend **29 PASS, 0 FAIL/SKIP**, `npm run build` đạt. Test công cụ đóng gói **1 PASS**. `git diff --check` đạt. Build frontend vẫn có cảnh báo chunk >500 kB, không phải lỗi build. Unit/integration fixtures là dữ liệu kiểm thử tổng hợp chỉ để chứng minh công thức và kiểm soát quyền; chúng không được cài vào runtime hay dùng làm kết quả thực nghiệm khóa luận.

Chưa hoàn thành điều kiện báo cáo thực tế: sau khảo sát người dùng đã xác nhận một mẫu trường/dòng hàng pilot (chi tiết bổ sung bên dưới), nhưng chưa có bản chép toàn văn chuẩn và chưa thực hiện thực nghiệm A/B/C trên cùng tập chuẩn. Không báo accuracy hoặc số thành công/thất bại thực tế của model từ các test phần mềm. Cần người phụ trách chuẩn bị/kiểm tra tập mẫu, chốt protocol, chạy trên môi trường độc lập và giữ evidence/config, sau đó API sẽ đọc dữ liệu thật và xuất báo cáo. Chưa kiểm chứng browser E2E hoặc runtime production; backend hiện có phụ thuộc migration Requests 05–06 chưa thực thi, không được khởi động trên invoice_db để chỉ thử giao diện mới.

## 8. Mẫu pilot người dùng cung cấp trong lượt này

Người dùng chỉ định `D:\test`. Kiểm tra chỉ đọc cho thấy **một file hoadon.jpg (529.563 byte)**, không có file ground truth, PDF hay kết quả A/B/C. Đã xem ảnh để đề xuất bản đọc, sau đó người dùng xác nhận trực tiếp bộ trường/ngày/tổng và một dòng hàng trong chat. Chỉ phần đã được xác nhận này được lưu làm reference; không tự lấy toàn văn hoặc các trường khác do AI đọc làm ground truth.

SHA256 ảnh gốc: `f81e386c8ba896d22affabc98b34b495aeff358a8602f50514981a156590c737`. Không sửa file gốc, không chép ảnh hoặc giá trị chứng từ vào Git. Bộ pilot chuẩn bị thành công gồm **1 sample, 0 run** tại thư mục dữ liệu cục bộ `.runtime/` đã được Git ignore sẵn (không thêm quy tắc ignore mới):

`D:\maudoan_2\DOAN_KLCN\.runtime\evaluation\request07-b4643e3b181047648374df475f335641\bundle`

Bundle SHA256: `ca9355df80ea7e20bf8c307aca421dba077458474ff29dc670ae4fc096b7a2a9`. Manifest nguồn và ảnh sao chép giữ nguyên ở thư mục `source` cạnh bundle; có reviewer pseudonym `user-confirmed-in-chat`, thời điểm ghi nhận, reference fields và item đã xác nhận. Không ghi số điện thoại/thông tin ngân hàng thành field chuẩn. `ocrText=null`, `ocrFields=null`, `runs=[]`. Đã dùng chính EvaluationService đọc bộ pilot bằng Java độc lập (không Spring/DB): `NO_TEST_DATA; RUNS=0; COMPARABLE=false`, exit 0. `git check-ignore` xác nhận bundle không được theo dõi. Cần đưa vào quy trình backup/ACL của kho đánh giá trước khi dùng lâu dài. Chưa đặt APP_EVALUATION_DIRECTORY cho runtime hiện tại.

Preflight thực nghiệm: GET chỉ đọc `http://127.0.0.1:11434/api/tags` không phản hồi; không có listener cổng 11434. Chưa tìm thấy thư mục tessdata khả dụng theo `./tessdata`, biến TESSDATA_PATH hoặc các vị trí được kiểm tra. Không coi đây là lỗi model trên mẫu và không ghi một result FAILED giả cho một lượt chưa chạy. Không tự tải model/tessdata, mở backend hoặc thay cấu hình hệ thống. Cần cung cấp/cấu hình runtime OCR (vie+eng) và model Ollama trước khi thực hiện thử nghiệm độc lập.

Một ảnh chưa đủ đại diện cho 8 nhóm yêu cầu. CER/WER vẫn Chưa xác định vì chưa có bản chép toàn văn đã kiểm tra; quality/extraction/classification/pipeline của model đều chưa đo vì chưa có run thực tế. API không lấy số liệu unit test hay confidence để lấp vào các chỉ số này.

Đã bổ sung thống kê theo nhánh thực tế TEXT / VISION_FALLBACK / UNKNOWN, khác thống kê theo phương án A/B/C. Không suy ra nhánh từ tên strategy; dùng visionFallback đã đo. Hai nhóm nhánh có thể có độ khó khác nhau nên bảng không được dùng để khẳng định ưu thế nhân quả trên cùng mẫu.

Kết luận: **CODE/FORMULA/RBAC TESTS VERIFIED; REAL A/B/C EXPERIMENT PENDING; OCR/AI QUALITY NOT YET MEASURED; NO DATABASE/MIGRATION CHANGE.**
