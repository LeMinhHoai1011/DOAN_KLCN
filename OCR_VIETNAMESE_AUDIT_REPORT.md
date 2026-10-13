# Báo cáo audit và khắc phục OCR tiếng Việt

Ngày kiểm tra: 13/10/2026  
Branch: `dev/ai`

## A. Root cause đã xác nhận

### 1. Chuỗi `�/?` quan sát trong log không phải mất Unicode trong kết quả Java

- `DocumentTextExtractionRuntimeTest.vietnameseImageRunsRealViePlusEngModel()` đã nhận đúng các chuỗi `HÓA ĐƠN GIÁ TRỊ GIA TĂNG`, `CÔNG TY`, `DƯƠNG`, `Nguyễn Huệ`, `thuế GTGT` và không chứa `U+FFFD`.
- Cùng chuỗi đó khi in trực tiếp qua Maven/PowerShell hiện thành `H�A ??N...`; khi xuất bytes UTF-8 dưới dạng Base64 rồi kiểm tra thì nội dung vẫn đúng.
- Vì vậy lỗi ví dụ trong audit là **lỗi hiển thị/giải mã console Windows**, không phải bằng chứng OCR result trong JVM bị hỏng.
- Bằng chứng: `backendqt/backendqt/src/test/java/com/example/invoice/service/DocumentTextExtractionRuntimeTest.java`, method `vietnameseImageRunsRealViePlusEngModel`, dòng 91–121.

### 2. Assertion `UTF8` so với `UTF-8` kiểm tra sai ngữ nghĩa

- Java coi `UTF8` và `UTF-8` là hai alias của cùng charset.
- Test cũ so sánh hai literal nên fail dù cấu hình JNA hợp lệ.
- Đã thay bằng so sánh `Charset.forName(...)`.
- Bằng chứng: `Tess4jOcrServiceTest.recognizesVietnameseDiacriticsWithConfiguredDefaultLanguage()`, dòng 66.

### 3. Có hai đường OCR và cấu hình không đồng nhất

- Luồng AI chính dùng `DocumentAiProcessingService -> AdaptiveOcrService -> Tess4jOcrService`.
- Luồng extraction phụ dùng `DocumentTextExtractionService -> TesseractOcrService`.
- `Tess4jOcrService` đã đặt `vie+eng`, `PSM_AUTO`, `user_defined_dpi=300`; `TesseractOcrService` trước sửa chưa đặt PSM/DPI.
- Đã đồng bộ PSM và DPI ở `TesseractOcrService.recognize()`, dòng 37–40.
- Native runtime vẫn in cảnh báo `Invalid resolution 1 dpi. Using 70 instead` ở một số ảnh ImageIO. Do Tesseract sau đó tự estimate khoảng 447–473 DPI và test vẫn pass, phần cảnh báo này được phân loại **PARTIAL**, chưa tuyên bố đã loại bỏ.

### 4. Tessdata và language

- `backendqt/backendqt/tessdata/vie.traineddata`: tồn tại, 531.275 byte.
- `backendqt/backendqt/tessdata/eng.traineddata`: tồn tại, 4.113.088 byte.
- Default thực tế: `vie+eng` tại `AiProperties.Ocr.language` và `application.yml`.
- Test thiếu `vie.traineddata` xác nhận hệ thống fail rõ ràng, không âm thầm fallback sang English.
- Máy không có CLI `tesseract` trong PATH; native library được Tess4J nạp thành công qua Maven dependency. Vì thiếu CLI nên chưa đọc được metadata/version nội bộ của traineddata bằng `combine_tessdata`.

### 5. Không tìm thấy bước làm mất dấu trong payload nghiệp vụ

- `OcrTextNormalizer.normalizeAndCompact()` chỉ bỏ control characters, co khoảng trắng và ký tự lặp; không bỏ combining marks.
- `Normalizer.NFD + remove \\p{M}` có tại `DocumentAiProcessingService` và `DocumentAiResultPersistenceService`, nhưng được dùng cho so khớp/heuristic chuẩn hóa, không ghi đè raw OCR hoặc text hiển thị.
- `TesseractOcrService.Utf8Tesseract.getOCRText()` đọc pointer native bằng `StandardCharsets.UTF_8`, dòng 70–81.
- PostgreSQL được truy vấn read-only: `server_encoding=UTF8`, `client_encoding=UTF8`, database encoding `UTF8`.
- React dùng JSON chuẩn; không tìm thấy `TextDecoder`, `getBytes` hay chuyển charset thủ công trong frontend.

### 6. PDF text layer và OCR ảnh được phân nhánh đúng

- PDF text layer dùng `PDFTextStripper`; nếu mọi trang usable thì không OCR lại.
- Trang scan/mixed được render bằng `PDFRenderer.renderImageWithDPI()` theo `ai.pdf.dpi` (default 180) rồi mới OCR.
- Bằng chứng: `DocumentTextExtractionService.extractPdf()` và `PdfImageConversionService.convert()`.

## B. Danh sách thay đổi

| File | Thay đổi | Tác động |
|---|---|---|
| `TesseractOcrService.java` | Đặt `PSM_AUTO` và `user_defined_dpi=300` | Đồng bộ đường OCR phụ với đường adaptive; không resize hoặc sửa file gốc |
| `DocumentTextExtractionService.java` | Sửa metadata engine từ `tess4j-5.13.0` thành `tess4j-5.18.0` | Báo cáo đúng dependency trong `pom.xml` |
| `Tess4jOcrServiceTest.java` | So sánh charset theo ngữ nghĩa; log text Unicode bằng Base64 UTF-8 | Loại false failure và tránh console tạo bằng chứng giả |
| `DocumentTextExtractionRuntimeTest.java` | Thêm kiểm tra không có `U+FFFD`, CER, WER, latency và Base64 UTF-8 | Có bằng chứng định lượng tái lập trên fixture tiếng Việt |
| `OCR_VIETNAMESE_AUDIT_REPORT.md` | Báo cáo này | Không tác động runtime |

Không sửa database schema, Auth, RBAC, company scope, workflow; không reprocess document thật; không commit/push.

## C. Pipeline OCR sau sửa

```text
PDF digital -> PDFBox text layer -> giữ Unicode gốc
PDF scan/image -> giữ file gốc trong MinIO
               -> quality analysis/preprocessing có điều kiện
               -> Tess4J, vie+eng, PSM_AUTO, DPI hint 300
               -> raw OCR + word boxes + confidence
               -> quality score, chọn attempt tốt nhất
               -> text extraction hoặc Vision fallback có giới hạn
               -> parser/validator
               -> persistence có guard không ghi đè document đã duyệt/kết quả tốt
               -> PostgreSQL UTF8 -> JSON HTTP -> React
```

Preprocessing hiện có các profile `NORMAL`, `ENHANCED`, `AGGRESSIVE`; `AdaptiveOcrService.recognize()` giới hạn số attempt và chọn score tốt nhất thay vì mặc định chọn ảnh xử lý mạnh. File gốc không bị ghi đè.

## D. Kết quả kiểm thử

### Regression OCR tập trung

Lệnh:

```text
mvnw.cmd -Dtest=Tess4jOcrServiceTest,DocumentTextExtractionRuntimeTest,ImagePreprocessingServiceTest,OcrTextNormalizerTest test
```

Kết quả: **14 tests, 0 failures, 0 errors, 0 skipped — PASS**.

- ảnh English/numeric: PASS, confidence 0,9439;
- PDF digital 2 trang: PASS, dùng PDFBox;
- PDF scan 2 trang: PASS;
- PDF mixed: PASS, text layer trang 1 + OCR trang 2;
- tiếng Việt `vie+eng`: PASS, giữ dấu và không có `U+FFFD`;
- missing `vie.traineddata`: PASS, fail-safe đúng;
- preprocessing: PASS.

### Regression AI/persistence

Lệnh:

```text
mvnw.cmd -Dtest=AiDocumentResultParserTest,DocumentAiProcessingServiceTest,DocumentAiResultPersistenceServiceTest,OcrTextNormalizerTest,ImagePreprocessingServiceTest,Tess4jOcrServiceTest test
```

Kết quả: **39 tests, 0 failures, 0 errors, 0 skipped — PASS**.

Bao gồm guard giữ invoice items hiện có, không ghi đè document đã duyệt và giữ text có dấu khi response mới bị suy giảm dấu.

### Full backend suite

Lệnh: `mvnw.cmd test` với `RUNTIME_DB_TEST=true`, PostgreSQL thật và `TESSDATA_PATH` test.

Kết quả: **159 tests, 1 failure, 0 errors, 1 skipped — FAIL do lỗi cũ không liên quan OCR**.

Failure còn lại:

```text
RuntimePersistenceVerificationTest.actualPostgresPersistsOcrAiStructuresAndHandlesReprocessSafely:76
expected: <PROCESSED> but was: <NEED_REVIEW>
```

Failure charset `UTF8`/`UTF-8` đã hết.

## E. Đánh giá định lượng

Fixture đã có ground truth thủ công: VAT tiếng Việt rõ, một trang, gồm tên công ty, địa chỉ, MST, ngày, thuế và tổng tiền.

| Phương án đã đo | CER | WER | Thời gian |
|---|---:|---:|---:|
| OCR `vie+eng` qua pipeline hiện tại | 3,7975% | 6,5217% | 720–2.766 ms tùy warm-up JVM/native |

Field checks trên fixture: MST, ngày, tổng tiền, tiêu đề hóa đơn, tên công ty và địa chỉ trọng yếu đều PASS assertion. Chưa tính một tỷ lệ Field Accuracy tổng quát vì repository chưa có đủ tập ground truth cho bán lẻ, phiếu thu/chi, điện thoại, nhiều trang và nhiều VAT. Không tạo số liệu giả cho các nhóm thiếu fixture.

Không có baseline định lượng đáng tin cậy trước sửa vì thay đổi chính sửa false assertion/diagnostic console, không thay traineddata hay thuật toán nhận dạng. Do đó không tuyên bố CER/WER đã cải thiện so với trước.

## F. Vấn đề còn tồn tại

1. **PARTIAL:** cảnh báo DPI native vẫn xuất hiện trên ảnh không có metadata DPI dù có hint; OCR vẫn tự estimate và pass.
2. **NOT VERIFIED:** chất lượng trên ảnh hóa đơn thật, nghiêng/nhiễu/bóng đổ/điện thoại chưa có ground truth đủ rộng.
3. **NOT VERIFIED:** Field Accuracy tổng hợp và so sánh OCR với Vision fallback chưa đủ fixture.
4. **BLOCKED:** không có CLI Tesseract/combine_tessdata trong PATH để kiểm tra version metadata của traineddata; không tải thêm theo yêu cầu.
5. **PARTIAL:** console Windows vẫn có thể render log Unicode sai. Diagnostic test đã chuyển sang Base64; API/database string không bị chứng minh là hỏng.
6. **UNRELATED FAIL:** full suite còn sai trạng thái `PROCESSED`/`NEED_REVIEW` trong runtime persistence test.

## G. Kết luận

- **FIXED:** false failure `UTF8` so với `UTF-8`.
- **FIXED:** test runtime hiện phát hiện thật sự `U+FFFD`, đo CER/WER và cung cấp bytes UTF-8 không phụ thuộc console.
- **FIXED:** metadata Tess4J version và PSM/DPI config thiếu ở đường extraction phụ.
- **VERIFIED:** OCR tiếng Việt trong Java string giữ dấu; PostgreSQL UTF8; English/numeric/PDF không regression; AI/persistence regression xanh.
- **PARTIAL:** chất lượng OCR tổng thể và DPI warning.
- **NOT VERIFIED:** tập hóa đơn thực tế rộng và Vision field accuracy.

Phán quyết: vấn đề “mất dấu” được báo từ output console là **FIXED/VERIFIED ở lớp dữ liệu**, không phải lỗi OCR persistence. Chất lượng nhận dạng tiếng Việt hiện đạt CER 3,7975% và WER 6,5217% trên fixture rõ duy nhất, nên tối ưu preprocessing/Vision trên dữ liệu thực tế vẫn là **PARTIAL**, chưa đủ bằng chứng để tuyên bố hoàn tất toàn hệ thống.

## H. Kiểm chứng bổ sung trên hóa đơn thật document 263

Người dùng cung cấp ground truth và cho phép thực hiện ngày 13/10/2026. PDF `nal 25 dk.pdf` được đọc từ MinIO thành bản sao trong `target`; document 263, OCR row và InvoiceData trong PostgreSQL không bị sửa hoặc reprocess.

### Benchmark OCR

| DPI | Profile | Quality score cũ | Tesseract confidence | Trường xác nhận đúng/9 | Thời gian |
|---:|---|---:|---:|---:|---:|
| 180 | NORMAL | 0,970574 | 92,299 | **5/9** | 1.876 ms |
| 180 | ENHANCED | 0,954720 | 89,298 | **5/9** | 1.748 ms |
| 180 | AGGRESSIVE | 0,793869 | 59,636 | 4/9 | 3.625 ms |
| 300 | NORMAL | 0,932910 | 85,817 | 4/9 | 2.651 ms |
| 300 | ENHANCED | 0,923491 | 84,113 | 4/9 | 2.345 ms |
| 300 | AGGRESSIVE | 0,753123 | 54,022 | 1/9 | 5.722 ms |

Kết luận: preprocessing mạnh và tăng DPI không cải thiện hóa đơn này. Score/confidence cũ đánh giá quá cao kết quả có các cụm `CONG TY`, `NOI THAT`, `Don vi`, `Dia chi`, `lam trieu`.

### Thay đổi quality gate

`DocumentAiProcessingService.looksLikeLostVietnameseDiacritics()` nay:

- vẫn fallback khi OCR tiếng Việt mất toàn bộ dấu và có ít nhất một cụm nghiệp vụ đáng ngờ;
- fallback khi văn bản còn một phần dấu nhưng có từ ba cụm nghiệp vụ Việt trở lên bị mất dấu;
- không fallback chỉ vì một tên/nhãn ASCII đơn lẻ.

Không tự thêm dấu và không sửa raw OCR. Kết quả chỉ quyết định dùng text extraction hay ảnh gốc qua Vision.

### Ollama Vision thật

`qwen3-vl:8b` đọc ảnh trang PDF ở 180 DPI trong 93.764 ms. Assertions PASS cho:

- MST người bán `0319174875`;
- MST người mua `0318334251`;
- tổng thanh toán `15.984.000`;
- tên người bán `CÔNG TY TNHH SX TM TRANG TRÍ NỘI THẤT ĐẠI PHÚC LONG`;
- tên người mua `CÔNG TY TNHH TRANG TRÍ NỘI THẤT NAL`;
- số tiền bằng chữ giữ `Mười lăm triệu...`.

Test: `RealInvoiceOcrBenchmarkTest`, `RealInvoiceVisionBenchmarkTest`, và ba trường hợp quality gate trong `DocumentVisionFallbackPageSelectionTest`.

Full regression sau thay đổi: **164 tests, 0 failures, 0 errors, 13 skipped — BUILD SUCCESS**. Các runtime benchmark bị skip mặc định và chỉ chạy khi truyền file test/model qua environment variable.
