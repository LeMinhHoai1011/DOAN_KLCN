# Python benchmark runner

Công cụ độc lập điều phối **OCR/AI Java hiện tại**, không ghi dữ liệu benchmark vào PostgreSQL hoặc MinIO. Xem [SOURCE_AUDIT.md](SOURCE_AUDIT.md) trước khi so sánh với nghiệp vụ. Không có số liệu độ chính xác thực nghiệm kèm theo; chưa có bộ GT thực được xác nhận.

**V1.1:** lệnh `run` tự xuất `initial_results.xlsx` để kế toán đối chiếu; `import-ground-truth --excel ...` tạo GT JSON đã xác nhận; `evaluate --run latest` xuất `evaluation_report.xlsx` mà không gọi lại OCR/AI. Xem quy trình đầy đủ tại [EXCEL_REVIEW.md](EXCEL_REVIEW.md). Quy trình chỉnh JSON V1.0 bên dưới vẫn dùng được.

**Ai.xlsx (3 sheet):** dùng `export-ai --run invoice-001`, kiểm tra ứng viên/GT và `import-ai --excel ...`, rồi xuất lại offline để xem metric. Xem [AI_XLSX.md](AI_XLSX.md) về ánh xạ trường, dữ liệu chưa hỗ trợ và cách thêm dòng hàng mà giữ prediction gốc.

**Cặp OCR.xlsx / Ai.xlsx:** `export-pair --ocr-run ocr-001 --ai-run invoice-001` kiểm tra chung dataset và xuất hai file độc lập; `import-ocr` xác nhận văn bản riêng, không xác nhận AI. Có `run --mode both` nhưng vẫn giữ Java SYSTEM, không phải Vision-only. Xem [PAIRED_BENCHMARK.md](PAIRED_BENCHMARK.md).

## Cài và chạy trên Windows 11

Từ gốc repository:

```powershell
python -m venv benchmark_runner\.venv
.\benchmark_runner\.venv\Scripts\python.exe -m pip install -r benchmark_runner\requirements.txt
New-Item -ItemType Directory -Force benchmark_runner\dataset

# Chỉ bật adapter trên phiên backend dùng cho benchmark.
$env:BENCHMARK_ENABLED = 'true'
$env:BENCHMARK_TIMEOUT_SECONDS = '900'
.\scripts\start.ps1 backend

# JWT của tài khoản ADMIN đã đăng nhập hệ thống. Không ghi token vào config/file/git.
$env:BENCHMARK_JWT = '<JWT ADMIN>'
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py health

# Chỉ OCR (không yêu cầu model AI sẵn sàng).
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py run --run-id ocr-001 --mode ocr

# OCR + AI: một request /ai cho mỗi file, OCR được trả kèm, không chạy OCR hai lần.
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py run --run-id system-001 --mode ai
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py run --run-id system-001 --resume
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py run --run-id system-001 --resume --retry-failed
```

Backend cần các dịch vụ/cấu hình khởi động bình thường; `scripts/start.ps1 backend` không tự bật PostgreSQL/MinIO/Ollama. Không khởi động bản backend thứ hai trùng port. Nếu backend đã chạy trước khi đặt `BENCHMARK_ENABLED`, cần khởi động lại backend để đăng ký endpoint. Không thay đổi cấu hình nghiệp vụ trong repository. Bỏ biến `BENCHMARK_ENABLED` rồi khởi động lại để tắt adapter.

Đo thời gian trên cùng máy và cấu hình, hạn chế tác vụ nghiệp vụ/Ollama chạy đồng thời để tránh sai lệch do tranh tài nguyên.

Endpoint `GET /api/benchmark/health`, `POST /api/benchmark/ocr`, `POST /api/benchmark/ai` đều yêu cầu ADMIN. POST multipart `file` + chuỗi JSON `options`; `/ai` chỉ nhận `mode=SYSTEM`. Không nhận documentId/path server/prompt/model tùy ý. Byte signature JPG/PNG/PDF được kiểm tra; kích thước không vượt `ai.maxImageSizeBytes` và giới hạn multipart của Spring. PDF giữ giới hạn trang/DPI của backend. Worker tuần tự toàn adapter; trả 429 khi đang bận, 504 khi quá timeout. Native OCR không dừng được thì giữ gate đến khi worker thoát, không xếp hàng hàng loạt request.

## Dataset và cấu hình

Chép JPG/JPEG/PNG/PDF vào `benchmark_runner/dataset/`, có thể chia thư mục con. Không tự đổi file gốc. Tên trùng ở hai thư mục có ID khác nhau; cùng nội dung vẫn được ghi hash để nhận diện. ID được tính từ relative path + SHA-256 nội dung, không phụ thuộc thứ tự liệt kê.

**File trùng nội dung:** runner quét và tính SHA-256 toàn bộ file hỗ trợ trước khi gọi OCR/AI. Mỗi hash chỉ chọn file đầu theo thứ tự relative path làm bản đại diện; các bản còn lại ghi `duplicate_of=<sample_id đại diện>`, trạng thái `SKIPPED_DUPLICATE`, không gọi API và không tạo raw prediction riêng. Danh sách đầy đủ nằm trong manifest, `duplicates.json` và checkpoint của run. Không xóa/di chuyển/sửa file.

Khi có file trùng, Excel review và Excel đánh giá thêm sheet `Duplicates` gồm sample_id, original_filename, relative_path, file_hash, duplicate_of và status. Chỉ bản đại diện xuất hiện trong các sheet đánh giá/nhập GT. Summary phân biệt `total_documents` (tất cả file), `unique_documents` và `skipped_duplicates`; metric, thời gian trung bình và tỷ lệ lỗi chỉ tính bản đại diện. Report cũng phân nhóm theo hash với manifest cũ chưa có cờ trùng để tránh đếm lại GT đã có.

Resume giữ checkpoint thành công và bỏ qua mọi alias, kể cả với `--rerun-success`; `--retry-failed` chỉ thử lại bản đại diện lỗi. Run-id mới được phép xử lý lại cùng nội dung để so sánh phiên bản. File cùng tên nhưng hash khác vẫn được xử lý riêng. Hai ảnh khác nhau có cùng số hóa đơn hoặc cùng JSON AI không được coi là file trùng.

`config/config.yaml` có địa chỉ API, timeout client, đường dẫn dataset/results/GT/reports và options. Đường dẫn tương đối tính từ thư mục cha của `config/` của file config. Có thể dùng `--config D:\bench\config\config.yaml` trước tên lệnh. `options.documentTypes/categories/companyName/companyTaxCode` phải tương đương catalog/company thực nếu muốn đối chiếu chính xác prompt với hệ thống. Không có categories/company thì prompt factory dùng danh mục rỗng, không tự suy đoán dữ liệu doanh nghiệp.

```text
benchmark_runner/
  dataset/<subfolder>/<original_file>
  results/<run_id>/config.json                 # dataset manifest, API/model/digest/class/config/runner hashes
  results/<run_id>/raw/<sample_id>/<attempt>/
      response.json                           # full adapter response
      ocr.json                                # OCR status/source/text/boxes/timing
      ai.json                                 # parsed/normalized/calls/raw provider responses
      entry.json                              # durable attempt completion
  results/<run_id>/checkpoint.json
  results/<run_id>/attempts.jsonl              # metadata log; no token/generated text
  results/<run_id>/duplicates.json            # aliases and canonical sample IDs
  ground_truth/<sample_id>/v000001.json
  reports/benchmark_report_<run_id>_<UTC timestamp>.xlsx
  reports/benchmark_report_<run_id>_<UTC timestamp>.gt.json
```

Mẫu lỗi không dừng các mẫu tiếp theo. Resume không chạy lại mẫu thành công; mẫu lỗi chỉ retry khi `--retry-failed`. `--rerun-success --resume` chủ động tạo attempt mới, vẫn giữ raw cũ. Mỗi attempt hoàn tất ghi `entry.json` trước checkpoint; khi bị gián đoạn runner phục hồi entry đã hoàn tất. Thay dataset/API hash/model digest/options/runner code sẽ bị từ chối resume; dùng run-id mới. Không chạy hai tiến trình Python cùng run-id.

## Ground Truth: chỉnh JSON rồi xác nhận

Lấy sample_id trong `results/<run_id>/config.json`. Draft chỉ là gợi ý, mọi trường/cell bắt đầu `UNCHECKED`.

```powershell
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py draft --run-id system-001 --sample-id S_<id> --document-type VAT_INVOICE --out D:\bench\draft.json
```

Mở chứng từ gốc và JSON; **đối chiếu từng trường**. Trong `fields` và từng cell `items`, sửa `value` rồi đặt:

- `PRESENT`: có giá trị đọc được trên chứng từ.
- `NOT_PRESENT`: trường không tồn tại, `value: null`.
- `UNREADABLE`: có nhưng không đọc được, `value: null`.

Có thể bổ sung trường vào cả `scope` và `fields`, bổ sung/xóa dòng hàng theo chứng từ thật. Không xóa trường core/header bắt buộc khỏi scope. `items_reviewed: true` chỉ sau khi kiểm đủ số dòng và từng cell, kể cả khi không có dòng nào. `document_type` và cell `documentType` phải là loại do người kiểm tra xác định. Với `RECEIPT` có `receiptKind=PAYMENT`, phải thêm và kiểm `payerName/totalAmount/currency` vào scope. Nếu đo OCR, điền bản chép chữ kiểm tra bằng mắt vào `ocr.text` rồi đặt `ocr.checked: true`; không dùng JSON AI làm văn bản chuẩn.

```powershell
# JWT có role ADMIN hoặc ACCOUNTANT được API /api/v1/users/me xác thực.
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py confirm --draft D:\bench\draft.json --reviewed-original
```

Tên người xác nhận không được lấy từ ô nhập tự do; chương trình lấy ID/username từ API. Mỗi xác nhận tạo file phiên bản mới, timestamp UTC và hash phiên bản trước; không ghi đè bản đã xác nhận hoặc prediction. Nếu không có GT xác nhận đầy đủ, report để metric trống. Filesystem phải được bảo vệ quyền ghi để audit người xác nhận có ý nghĩa.

## Metric và Excel

```powershell
.\benchmark_runner\.venv\Scripts\python.exe benchmark_runner\main.py report --run-id system-001
```

Report không gọi API, đọc evidence và GT mới nhất, kèm snapshot GT riêng để tái lập phiên bản đã đánh giá. Không ghi đè report cũ. Có 7 sheet: Summary, Documents, OCR_Detail, AI_Fields, AI_Items, Errors_Logs, Run_Config. Có filter, freeze header, % cho các tỷ lệ, màu lỗi và hyperlink file gốc. Văn bản trên 32.000 ký tự bị rút ngắn trong Excel; raw JSON giữ bản đầy đủ. OCR/AI string luôn là text để không thực thi công thức Excel.

- Đúng giá trị: TP; sai giá trị: FP+FN; bỏ sót: FN; tự thêm khi GT NOT_PRESENT: FP. UNREADABLE không tính F1, thống kê riêng.
- Trường có giá trị được model tạo ngoài scope GT đã xác nhận được tính FP trong nhóm Extended; vì thế phải bảo đảm GT thực sự đầy đủ trước khi xác nhận.
- Core Micro-F1 chỉ gộp chứng từ có đủ core đọc được theo loại. Document Macro-F1 là trung bình core F1 các chứng từ đủ điều kiện, có mẫu số dương. Chỉ số tổng field tách riêng ở từng Documents; bảng items không gộp vào Core.
- Mẫu số 0: để trống, không tự coi là 100%. Field exact accuracy tính cả trường vắng đúng, bỏ UNREADABLE. AI runtime failure được so với dự đoán rỗng khi GT hợp lệ (các giá trị GT hiện diện thành FN), đồng thời báo lỗi runtime.
- Ghép items Hungarian một-một: chỉ ghép khi tên sản phẩm bằng chính xác sau Unicode/khoảng trắng; dùng số cell khớp để phân biệt tên trùng. Không fuzzy, không ghép theo vị trí; giữ các dòng trùng riêng. Tên sai thì báo cả dòng thừa và thiếu, quy tắc nghiêm ngặt đã công bố trước.
- Chuẩn hóa NFC/khoảng trắng, ngày yyyy-MM-dd hoặc dd/MM/yyyy hoặc dd-MM-yyyy; số tiền theo dấu nhóm nghìn và dấu thập phân; taxRate 10 hoặc 10% thành 0.1. Dạng tiền chuỗi `1.100` được hiểu là 1100, `1,25` là 1.25; nếu dữ liệu mơ hồ phải nhập số JSON vào GT. Không bỏ dấu tiếng Việt/đổi hoa thường/fuzzy tên/MST/số hóa đơn.
- CER/WER chỉ dùng `ocr.checked=true`: NFC và thống nhất CRLF; CER giữ khoảng trắng, WER tách whitespace; tổng hợp bằng tổng edit distance / tổng độ dài GT, không lấy trung bình các tỷ lệ. GT text rỗng cho tỷ lệ undefined. PDF text layer ghi nguồn riêng, không giả là Tesseract.

## Kiểm thử

```powershell
.\benchmark_runner\.venv\Scripts\python.exe -m pytest benchmark_runner\tests -q
Set-Location backendqt\backendqt
.\mvnw.cmd -q "-Dtest=BenchmarkAdapterTest,BenchmarkHttpTest,BenchmarkLogPrivacyTest,DocumentAiProcessingServiceTest,PdfImageConversionServiceTest,AiDocumentResultParserTest,AiDocumentPromptFactoryTest" test
```

`BenchmarkHttpTest` cần venv đã cài ở đường dẫn trên. Test dùng cổng localhost ngẫu nhiên, JWT test riêng và context không có datasource/MinIO; xử lý AI dùng fixture. `BenchmarkAdapterTest` có OCR Tesseract thật trên ảnh JPG/PNG sinh thử, cần tessdata `eng.traineddata`. Test PDF native/scan/nhiều trang dùng PDFBox thật và AI fixture; không được dùng output test làm số liệu luận văn. Xem [VERIFICATION.md](VERIFICATION.md) về kết quả và phần chưa kiểm chứng thực nghiệm.
