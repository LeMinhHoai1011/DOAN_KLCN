# Ground Truth qua Excel — V1.1

Không thay đổi Java OCR/AI, REST API, database hoặc React trong V1.1. Excel là công cụ đối chiếu; JSON prediction vẫn là nguồn gốc bất biến. Các lệnh đánh giá chỉ đọc kết quả cũ.

## Chạy đủ 7 bước

Cài dependencies và bật Java benchmark adapter theo [README.md](README.md). Từ gốc dự án:

```powershell
Set-Location benchmark_runner
$benchPython = '.\.venv\Scripts\python.exe'
$env:BENCHMARK_JWT = '<JWT của ADMIN>'

# 1. Chép JPG/PNG/PDF vào dataset (giữ nguyên file gốc).
# 2–3. Gọi Java tuần tự và tự xuất workbook khi tất cả mẫu có kết quả, kể cả mẫu lỗi.
& $benchPython main.py run --run-id invoice-001 --mode ai

# 4. Mở workbook được in tại review_excel; lần đầu là reports/initial_results.xlsx.
# Lưu bản kế toán sửa thành reviewed_results.xlsx, giữ bản ban đầu để đối chiếu.

# 5. Import workbook đã kiểm tra bằng tài khoản đang xác thực.
& $benchPython main.py import-ground-truth --excel .\reports\reviewed_results.xlsx

# 6–7. Tính lại metric và xuất evaluation_report.xlsx, không gọi OCR/AI.
& $benchPython main.py evaluate --run latest
```

`evaluate --run invoice-001` chọn rõ run cần đo. `latest` chọn theo `started_at` trong config, không theo thời gian sửa file. Nếu workbook cần tạo lại từ run V1.0 hoặc sau khi retry:

```powershell
& $benchPython main.py export-review --run invoice-001
```

Không ghi đè workbook cũ: khi `reports/initial_results.xlsx` đã tồn tại, file mới nằm tại `reports/<run_id>-<UTC timestamp>/initial_results.xlsx`. `evaluation_report.xlsx` áp dụng cùng quy tắc. CLI in chính xác đường dẫn; mỗi run cũng ghi `review_exports.jsonl`.

## Cách điền workbook

Các ô màu vàng được nhập. Cột dữ liệu gốc được khóa, không có password. Nếu cần thêm dòng, dùng **Review → Unprotect Sheet**, thêm rồi bảo vệ lại nếu muốn. Bảo vệ Excel chỉ giúp tránh sửa nhầm; importer luôn kiểm tra độc lập với snapshot trên filesystem và JSON evidence, kể cả khi đã bỏ bảo vệ.

Nếu dataset có file trùng SHA-256, workbook thêm sheet **Duplicates** chỉ đọc. Sheet này ghi `original_filename`, `file_hash`, `duplicate_of`, `SKIPPED_DUPLICATE` và relative path để phân biệt tên giống nhau. Chỉ bản đại diện được nhập GT ở 5 sheet chính; import trả trạng thái `SKIPPED_DUPLICATE` cho alias. Không xóa sheet hoặc đổi liên kết duplicate_of. Workbook V1.1 cũ có 5 sheet vẫn được kiểm tra với snapshot tương ứng; các mẫu trùng trong workbook cũ cũng không tạo GT lặp.

| Sheet | Thao tác |
|---|---|
| Documents_Review | Chọn `document_type` đúng thực tế; `reviewed_original=YES`, `items_reviewed=YES` sau khi kiểm xong; điền `confirmed_by` đúng username tài khoản import; cuối cùng đặt `confirmation_status=CONFIRMED` |
| AI_Fields_Review | Đọc `ai_value`, nhập riêng `confirmed_value` và `field_status`. Bao gồm core theo loại, invoice header, các trường AI đã trả và phân loại |
| AI_Items_Review | Một dòng mỗi item, mỗi cell có giá trị AI/giá trị xác nhận/trạng thái riêng. Dùng `KEEP`, `EXTRA`, `ADD`; không xóa dòng AI gốc |
| OCR_Review | Đối chiếu và nhập bản chép chữ vào `confirmed_text`; chỉ đặt `gt_checked=YES` khi đã kiểm toàn bộ văn bản. Mặc định không tính CER/WER |
| Run_Info | Metadata bất biến: run/review/sample ID, filename/hash, model/digest, thời gian và hash result. Không sửa |

Các trạng thái trường/cell:

- `UNCHECKED`: chưa kiểm, không thể xác nhận GT.
- `PRESENT`: phải nhập `confirmed_value` đọc được trên chứng từ. Giá trị ban đầu để trống, không tự chép AI thành GT.
- `ABSENT`: chứng từ không có trường này; để trống giá trị xác nhận. Khi lưu JSON sẽ thành `NOT_PRESENT` để tương thích V1.0.
- `UNREADABLE`: có nhưng không đọc được; để trống giá trị xác nhận; không tính F1 giá trị.

Giữ số hóa đơn/MST/ký hiệu là **Text**, ví dụ `00001234`; nhập số Excel cho số tiền cũng được. Ngày hỗ trợ Excel date hoặc các định dạng của normalizer V1.0. Import từ chối ngày/số không hợp lệ, công thức và ô Excel có lỗi; không đọc cached formula thành GT.

### Dòng hàng

- Sửa item có sẵn: giữ `item_id=pred_000001...`, `row_action=KEEP`; nhập tất cả cell xác nhận và trạng thái. `gt_order` là số nguyên dương không trùng trong mỗi chứng từ.
- AI tự thêm dòng không tồn tại: giữ nguyên dòng prediction, đặt `row_action=EXTRA`, xóa các ô **confirmed_***. Không cần xác nhận các cell của dòng đã loại; các trạng thái cũ không được đưa vào GT.
- Thêm dòng AI bỏ sót: sao chép 4 cột định danh đúng mẫu; `item_id=new_<mã riêng>`, `row_action=ADD`, `gt_order` mới; các ô **ai_*** phải trống. Điền đủ 7 giá trị/trạng thái. Ví dụ `new_1`, `new_2` có nội dung giống nhau vẫn là 2 dòng GT riêng.
- Có thể sắp xếp lại thứ tự dòng worksheet; importer ghép bằng sample/item ID, không lấy vị trí sheet làm định danh.
- Nếu không có hàng hóa, xác nhận `items_reviewed=YES` sau khi đối chiếu chứng từ, không tự tạo dòng rỗng.

### Trường bổ sung và sửa loại chứng từ

Có thể thêm dòng `AI_Fields_Review` với 4 cột định danh của mẫu, `field_name` CamelCase, `field_group=Core/Optional/Extended`, `ai_value` trống; nhập giá trị/trạng thái xác nhận. Không được xóa dòng trường ban đầu.

Nếu sửa loại chứng từ thì sửa cả `Documents_Review.document_type` và giá trị xác nhận của field `documentType`. Import yêu cầu đầy đủ core theo **loại đã sửa**, không tin `field_group` của AI. Nếu thiếu, lỗi liệt kê tên trường để bổ sung. Receipt có `receiptKind=PAYMENT` cần `payerName/totalAmount/currency`.

### Văn bản OCR dài

Văn bản trên 30.000 ký tự được tách thành nhiều `part` của cùng mẫu để tránh giới hạn 32.767 ký tự của Excel, không cắt bỏ nội dung. Khi đo OCR, tất cả part phải có `gt_checked=YES`; importer nối trực tiếp `confirmed_text` theo `part`, không tự thêm dấu xuống dòng. Nếu bản chuẩn ngắn hơn, để trống các đoạn chuẩn dư. Có thể thêm part liên tiếp: copy định danh/source, `ocr_text` trống. Không sửa văn bản OCR gốc. Nếu `gt_checked=NO`, văn bản xác nhận không được dùng tính CER/WER.

## Import, xác thực và lỗi

Lệnh import gọi `/api/v1/users/me`, chỉ chấp nhận ADMIN/ACCOUNTANT. Username Excel phải trùng tài khoản đã xác thực; ID và timestamp xác nhận được lấy từ API và thời gian import UTC, không tin một tên tự nhập. Cần Java API hoạt động để xác thực khi import; lệnh `evaluate` chạy offline.

Importer kiểm tra đủ 5 sheet/header, run/review/sample identity, filename/hash, nguồn file hiện tại, metadata, toàn bộ giá trị AI và sự hiện diện của dòng gốc. Snapshot nằm tại `results/<run_id>/reviews/<review_id>.json`; checksum trong workbook không được coi là bằng chứng. Filesystem results/GT vẫn phải được bảo vệ quyền ghi như V1.0; đây không phải chữ ký số chống người sửa cả snapshot và evidence.

- Sửa dữ liệu bất biến, đổi metadata, trộn run, trùng key hoặc xóa dòng gốc: từ chối **toàn workbook trước khi ghi GT**.
- Mẫu DRAFT: trả `UNCONFIRMED`, không tạo GT.
- Mẫu CONFIRMED thiếu trạng thái/giá trị/trường, sai người xác nhận, item order trùng hoặc OCR check mâu thuẫn: trả `INVALID` kèm sheet/dòng/trường/lý do; không tạo GT cho mẫu đó.
- Các mẫu CONFIRMED hợp lệ khác vẫn được import. CLI trả mã 2 nếu có mẫu chưa xác nhận/không hợp lệ; đọc báo cáo để biết phần nào đã nhập.
- Kết quả import lưu trong `reports/ground_truth_import_<UTC timestamp>.json`, có phiên bản và đường dẫn GT từng mẫu. Chạy lại đúng file đã import trả `UNCHANGED`; bản sửa mới tạo phiên bản mới, giữ nguyên GT cũ và prediction gốc.
- Retry tạo prediction/checkpoint mới sau khi xuất Excel: workbook cũ bị từ chối; dùng `export-review` để đối chiếu đúng attempt mới.

GT được lưu tại `ground_truth/<sample_id>/v000001.json`, thêm `review_source` chứa run_id, review_id, Excel SHA-256 và notes. `evaluation_report.xlsx` giữ 7 sheet báo cáo V1.0 và snapshot GT `.gt.json`; các metric chỉ dùng GT xác nhận, không tự chạy lại OCR/AI.

## Kiểm thử

```powershell
& $benchPython -m pytest tests -q
```

Đã chạy **49 test Python**, gồm 33 trường hợp Excel và 8 trường hợp kiểm tra file trùng; toàn bộ pass. Các test dùng fixture được ghi rõ, không phải kết quả đo Qwen thực tế. Kiểm thử HTTP Java/Python V1.0 cũng được chạy lại sau thay đổi xuất tự động. Xem [VERIFICATION.md](VERIFICATION.md).
