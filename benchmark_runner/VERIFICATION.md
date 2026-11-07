# Báo cáo triển khai và kiểm chứng

Ngày: 2026-11-05. Đây là kết quả kiểm thử phần mềm, **không phải độ chính xác thực nghiệm của Qwen3-VL trên chứng từ thật**.

## Thay đổi Java

- Thêm `controller/BenchmarkController.java`: `/api/benchmark/health`, `/ocr`, `/ai`; opt-in, JWT ADMIN, multipart giới hạn kích thước, timeout, gate tuần tự và lỗi có mã.
- Thêm `service/BenchmarkAdapter.java`: không repository/MinIO, giữ nguyên OCR/provider/prompt/parser/validator và helper chọn nhánh.
- Thêm `service/BenchmarkLogPrivacy.java`: chặn nội dung log trên riêng worker benchmark.
- Sửa `service/DocumentAiProcessingService.java`: chỉ đổi visibility 5 helper từ private sang package-private. Diff không đổi thân hàm/threshold/prompt/fallback/aggregation.
- Thêm test `BenchmarkAdapterTest`, `BenchmarkHttpTest`, `BenchmarkLogPrivacyTest`.
- Danh sách source đã kiểm tra và tác dụng phụ: [SOURCE_AUDIT.md](SOURCE_AUDIT.md).

## Python

Tạo `main.py`; `src/api_client.py`, `dataset_loader.py`, `benchmark_runner.py`, `ocr_evaluator.py`, `ai_evaluator.py`, `field_normalizer.py`, `ground_truth_manager.py`, `excel_reporter.py`, `checkpoint_manager.py`; cấu hình YAML, requirements, tests và tài liệu. Các bước cài Windows, JWT, dataset, OCR-only, AI, draft/confirm GT, metric và Excel có trong [README.md](README.md).

## Đã chạy

- **8 test Python: pass**, bao gồm normalize, TP/FP/FN, UNREADABLE/unconfirmed, duplicate/reordered items một-một, CER/WER, version GT, lỗi mạng/timeout/schema, tên file trùng, resume/khôi phục checkpoint, không ghi đè evidence và Excel 7 sheet đọc lại bằng openpyxl.
- **30 test Java trong 7 class: pass**, gồm 9 test adapter/HTTP/privacy mới và 21 regression test helper/parser/prompt/PDF/pipeline hiện có. Không có test bị skip trong nhóm được chọn.
- OCR native Tesseract thật + adaptive OCR trên **JPG, PNG, PDF scan hai trang** tự sinh; có assert đọc được `INVOICE` và đúng số trang. Đây là smoke test bằng chứng từ thử, không có metric luận văn.
- PDFBox native text và PDFBox render thực thi; test chọn text-only cho PDF digital, Vision cho OCR kém, text-first cho OCR tốt, fallback khi JSON text sai, giữ nguyên hành vi gộp dòng PDF.
- **Python gọi Java qua HTTP thật**, embedded Tomcat cổng ngẫu nhiên, JWT filter và quyền từ code hệ thống thật; cả OCR-only/AI và xuất Excel được thực thi. Processor/AI trong test HTTP là fixture có chủ đích, không gọi Qwen thật.
- JWT thiếu hoặc sai role bị từ chối. Context HTTP test không có `DataSource` hoặc `MinioClient`.
- Test log xác nhận chỉ worker benchmark bị chặn log, log ngoài worker giữ nguyên.
- `compileall` Python và `git diff --check`: pass.

## Ma trận yêu cầu kiểm thử

| Tình huống | Bằng chứng đã chạy |
|---|---|
| JPG/PNG | Native Tesseract + adaptive OCR thật, ảnh sinh thử |
| PDF text layer | PDFBox thật, AI fixture, không OCR/render khi tất cả trang có lớp chữ dùng được |
| PDF scan/nhiều trang | Render PDFBox + OCR native thật với PDF scan 2 trang; kiểm nhánh AI bằng fixture |
| Thiếu trường | Unit test FN/GT thiếu scope/UNREADABLE |
| Nhiều dòng/dòng trùng | Python ghép một-một; Java kiểm hành vi distinct của aggregator gốc |
| JSON sai | Parser regression thật + adapter nhận lỗi provider mô phỏng |
| OCR không đọc được | Adapter nhận OcrPageResult unavailable và trả FAILED |
| Ollama timeout | Exception provider mô phỏng, không gây timeout Qwen thật |
| API ngừng/mất kết nối | requests ConnectionError/Timeout injection, sanitize mã lỗi; runner tiếp tục mẫu khác |
| Resume | Checkpoint còn/mất, khôi phục durable entry, không lặp mẫu thành công, retry lỗi |
| GT chưa xác nhận/version mới | Không tính metric draft; lưu v1/v2 và đọc bản mới nhất, giữ bản cũ |
| File trùng tên | Hai path cùng basename có sample_id khác; hash nhận diện nội dung |
| Không ghi nghiệp vụ | Adapter không có dependency ghi; HTTP context không có datasource/MinIO |

## Chưa kiểm chứng / giới hạn

- **Chưa chạy suy luận Qwen3-VL thật trên bộ dữ liệu người dùng**, chưa có GT kế toán đã xác nhận; không công bố Precision/Recall/F1 thực nghiệm.
- Chưa chạy nguyên ứng dụng nghiệp vụ với DB/MinIO thực để đếm bản ghi trước/sau. Đã kiểm tra kiến trúc không có dependency ghi và test context không có DB; không đồng nhất điều đó với đo trên DB production.
- Chưa chạy toàn bộ test suite ứng dụng/UI; đã chạy các regression trực tiếp liên quan. Không sửa React.
- Không thêm Streamlit; dùng chỉnh/xác nhận JSON qua CLI như phương án ưu tiên.
- Raw response của lần provider/parser lỗi trước khi trả kết quả không được service gốc expose; có mã lỗi, nhánh thử và các trang đã thành công. Không tạo raw giả.
- Phiên bản native Tesseract chưa được truy vấn riêng; health trả phiên bản Tess4J manifest nếu có và hash adapter. Dữ liệu local GT cần quyền filesystem phù hợp; chưa có chữ ký số chống giả mạo bởi người có quyền sửa file.

**Không thêm/sửa/xóa bảng, cột, entity hoặc migration phục vụ benchmark. Không ghi benchmark vào DB/MinIO. Không commit/push các thay đổi trong lượt triển khai này.**

## Bổ sung V1.1 — Excel Ground Truth

- Thêm `src/review_workbook.py`: xuất 5 sheet, khóa cột AI, snapshot bất biến phía filesystem, import có kiểm tra metadata/evidence/hash file gốc và validate từng mẫu.
- Sửa `src/benchmark_runner.py`: tự xuất `initial_results.xlsx` sau khi hoàn tất dataset. Sửa `main.py`: thêm `export-review`, `import-ground-truth`, `evaluate --run latest`.
- Sửa `src/ground_truth_manager.py`: dùng chung hàm `confirm_value` để lưu GT từ JSON hoặc Excel, không thay quy tắc phiên bản V1.0.
- Sửa `src/excel_reporter.py`: hỗ trợ tên `evaluation_report.xlsx`, vẫn giữ snapshot GT và báo cáo 7 sheet V1.0.
- Thêm `tests/test_excel_review.py`: **33 test mới**, tổng **41 test Python pass**. Bao gồm import hợp lệ, version/idempotency, ABSENT/UNREADABLE, readonly AI/metadata bị sửa, source JSON/file thay đổi, thiếu dòng gốc, trùng sample/key/order, thêm dòng mới/dòng thừa/dòng trùng, type thiếu core, số/ngày/ID lỗi, công thức Excel, OCR dài tách part, lỗi từng mẫu và CLI import/evaluate offline.
- Chạy lại **2 test BenchmarkHttpTest: pass**, trong đó Python thực sự gọi Java REST với JWT và xuất workbook tự động mới; context vẫn không có datasource/MinIO. AI trong test HTTP tiếp tục là fixture, không phải suy luận Qwen thật.
- Không sửa runtime Java, DB, React, OCR, model hoặc prompt thêm trong V1.1. Không chạy lại OCR/AI khi import/evaluate; import chỉ gọi API xác thực `/api/v1/users/me`.
- Hướng dẫn đầy đủ: [EXCEL_REVIEW.md](EXCEL_REVIEW.md). Chưa có workbook kế toán thực đã duyệt hoặc số liệu F1 thực nghiệm để công bố; các workbook trong test được tạo ở thư mục tạm bằng fixture.

## Bổ sung kiểm tra file trùng SHA-256

- Quét đủ file JPG/JPEG/PNG/PDF trước khi gọi API, chọn bản đại diện theo relative path và chỉ xử lý mỗi hash một lần trong run. Alias giữ sample_id riêng, liên kết `duplicate_of` và checkpoint `SKIPPED_DUPLICATE`; không có API call/raw prediction cho alias.
- Ghi `duplicates.json`, metadata manifest và sheet `Duplicates` trong cả review/evaluation Excel. Không sửa/xóa/di chuyển file nguồn.
- Report lọc bằng SHA-256 ngay cả khi manifest/GT cũ có cả bản trùng; mẫu, runtime, lỗi, CER/WER, field/item và metric tổng hợp chỉ tính bản đại diện. Import không tạo GT alias. Không dựa vào số hóa đơn hoặc prediction để gộp file.
- Thêm `tests/test_dataset_duplicates.py`: **8 test mới**, tổng **49 test Python pass**. Kiểm tra khác tên/cùng nội dung, trùng tên/khác nội dung, giữ nguyên bytes/mtime, không tạo raw alias, resume và phục hồi checkpoint không gọi lại, run-id mới chạy lại, metric không đếm GT trùng, bảo vệ sheet Duplicates, đọc manifest cũ và retry bản đại diện lỗi.
- Test dùng API fixture có cùng prediction cho hai file hash khác nhau để chứng minh chúng vẫn được xử lý độc lập; không phải số liệu suy luận thực nghiệm. Không thay đổi Java, DB, model, prompt hoặc logic OCR/AI trong phần bổ sung này.

## Ai.xlsx — đã xuất từ dữ liệu invoice-001

- File bàn giao: `reports/invoice-001-20261105T052050330597Z/Ai.xlsx` (118.559 bytes). Bản xuất trước ở `reports/Ai.xlsx` vẫn được giữ; không ghi đè.
- Đọc kết quả thật: **10 mẫu, 9 SUCCESS, 1 FAILED/OLLAMA_TOKEN_LIMIT; 31 dòng hàng AI; 634 dòng kiểm tra; 370 ô AI có giá trị**. Không gọi lại Java/Ollama để xuất.
- Trạng thái cần kiểm: **38 ô NOT_RETURNED_NEEDS_REVIEW**, **181 ô NOT_SUPPORTED**, **45 ô RUN_FAILED**. Đây là số ô, không phải số trường duy nhất hoặc kết luận AI bỏ sót; chưa có GT thực tế.
- `Ai.summary.json` có thống kê theo tên trường và nhóm; `Ai.verification.json` ghi **21.877 ô** được so sánh với snapshot, **10 evidence JSON** được kiểm tra. Tên sheet chính xác `Ai`, `Hang_hoa`, `Kiem_tra_GT`; đã render và xem cả 3 sheet. Identifier như MST vẫn là text và giữ số 0 đầu khi đọc bằng openpyxl.
- Không có Ground Truth đã xác nhận trong dataset này: toàn bộ Precision/Recall/F1 thực nghiệm để trống, candidate từ AI không được tự xác nhận.
- **68 test Python pass**, gồm 12 trường hợp mới của `tests/test_ai_workbook.py`; kiểm tra import/phiên bản, không sửa raw AI, reuse GT/OCR cũ, tiền chênh lệch tách F1, dòng GT thiếu/thừa/trùng, không suy ra Nợ/Có/thanh toán, sample filter và mẫu thất bại.
- Source thêm `src/ai_workbook.py`; CLI `export-ai`, `import-ai`, `run --sample-id`; hướng dẫn [AI_XLSX.md](AI_XLSX.md). Không sửa runtime Java hoặc database trong phần này. Import dùng danh tính API thực khi người dùng xác nhận; test import dùng fixture, không tạo GT giả cho invoice-001.

## OCR.xlsx / Ai.xlsx cùng bộ dữ liệu

- Thêm `src/ocr_workbook.py`, `src/ocr_ground_truth.py`, `src/paired_export.py`; lệnh `export-ocr`, `import-ocr`, `export-pair`, chế độ `run --mode both`.
- Giữ `ai` là Java SYSTEM (có thể OCR nội bộ), không giả nhận Vision-only. `both` gọi `/ai` đúng một lần/mẫu, tách hai kết quả; không đổi Java/prompt/parser/DB/workflow.
- OCR GT có namespace và version độc lập, chỉ tính CER/WER sau xác nhận danh tính và văn bản. Có import trực tiếp OCR_Review của Excel V1.1 và fallback GT JSON cũ đã checked; không tự xác nhận AI. Các nhóm F1 chỉ dùng mẫu có đủ GT trong đúng phạm vi nhóm.
- Bổ sung chẩn đoán VAT/chiết khấu/tổng đã điều chỉnh, riêng với F1; không tự điền dữ liệu thiếu thành 0. Sửa so sánh timing chỉ cho sai số serialization nhỏ của Excel; prediction vẫn kiểm tra nguyên giá trị. Chuẩn LF cho view OCR để tránh XML tạo double newline; raw JSON không đổi.
- **19 test mới**, tổng **87 test Python pass**, trong `tests/test_ocr_pair.py`: OCR/AI GT độc lập, CER/WER có trọng số, source/candidate integrity, GT Excel cũ, CRLF, timing, OCR dài không cắt, AI lỗi nhưng OCR thành công, both một API call/mẫu, mismatched dataset, coverage GT và dataset mở rộng. Đã chạy lại 2 test Java HTTP/JWT thành công; không có datasource/MinIO trong context test.
- Cặp thực tế đã xuất tại `reports/paired-20261105T054705758349Z/OCR.xlsx` và `Ai.xlsx`. **OCR: 10 mẫu thành công, 11 trang PDF text layer**; **AI: 9 thành công, 1 lỗi token limit, 31 dòng hàng, 634 dòng kiểm tra**. Không gọi lại OCR/AI để xuất.
- Kiểm tra đọc lại: **250 ô OCR và 22.080 ô AI**, đối chiếu snapshot và 20 evidence JSON từ hai run; `verification.json` lưu kết quả. Có render QA; không có GT thực tế nên CER/WER/F1 chính thức trống.
- `ocr-001`/`invoice-001` khớp đúng 10 sample_id/hash/path. Dataset hiện có **64 file/64 hash**, thêm **54 file** chưa có trong hai run; không thay đổi/xóa 10 file cũ. `dataset_comparison.json` ghi current_dataset_matches=false và danh sách đầy đủ, không nhận vơ đã xử lý 64 mẫu.
- Hướng dẫn và phần đã có/bổ sung/giới hạn: [PAIRED_BENCHMARK.md](PAIRED_BENCHMARK.md). Adapter lấy GT kế toán từ API ứng dụng chưa triển khai; schema có review_source/version/hash để bổ sung sau. Chưa có Vision-only thuần vì không đổi API Java SYSTEM.
