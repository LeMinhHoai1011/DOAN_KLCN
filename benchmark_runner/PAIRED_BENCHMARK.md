# OCR và AI trên cùng bộ chứng từ

## Đã có và bổ sung

| Phần | Trạng thái |
|---|---|
| Dataset SHA-256, sample_id ổn định, bỏ qua file trùng, chạy tuần tự, checkpoint/run-id | Giữ nguyên cơ chế hiện có |
| Ai.xlsx 3 sheet, candidate và GT xác nhận, nhập GT cũ, F1/core/macro/dòng hàng | Giữ và bổ sung kiểm tra độ phủ GT theo nhóm |
| OCR.xlsx độc lập, candidate text/GT text, import/xác nhận riêng, CER/WER/timing | Bổ sung `src/ocr_workbook.py`, `src/ocr_ground_truth.py` |
| Xuất cặp từ hai run cùng dataset, phát hiện thay đổi dataset hiện tại | Bổ sung `src/paired_export.py` và `export-pair` |
| Chạy `--mode both` | Một lần gọi Java SYSTEM cho mỗi mẫu; tách cả OCR và AI từ phản hồi, không gọi OCR hai lần |
| Kiểm tra số học nghiệp vụ | Thêm VAT, chiết khấu theo tỷ lệ, tổng đã điều chỉnh; tách biệt F1, không suy dữ liệu thiếu thành 0 |
| GT từ dữ liệu kế toán đã xác nhận trên ứng dụng | Schema có version/hash/provenance để mở rộng; **chưa có adapter đọc GT nghiệp vụ**. Không coi AI confidence hoặc field_verifications.VERIFIED là GT kế toán |

## Các chế độ chạy

Từ gốc dự án, sau khi cấu hình Java/JWT theo README:

```powershell
# Chỉ OCR / PDF text layer; không gọi trích xuất AI.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py run --run-id ocr-full-001 --mode ocr

# Chỉ yêu cầu bước AI ở cấp runner; Java vẫn dùng pipeline SYSTEM.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py run --run-id ai-full-001 --mode ai

# Khuyến nghị cho bộ dữ liệu mới: một pipeline, lưu cả hai và xuất hai workbook.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py run --run-id both-full-001 --mode both
```

**Giới hạn AI-only:** API hiện có `/api/benchmark/ai` chỉ nhận `mode=SYSTEM`; Java tự dùng native PDF text/OCR/Vision fallback. `--mode ai` không phải Vision-only và không tái sử dụng cache OCR của run khác. Chưa có chế độ AI đọc ảnh hoàn toàn không OCR; bổ sung chế độ đó cần API/adapter riêng, nằm ngoài ràng buộc không sửa Java của lượt này. Không ghi nhãn kết quả SYSTEM là thí nghiệm Vision-only.

`both` và `ai` đều chỉ gọi `/ai`, lấy luôn OCR trả kèm. `both` tự xuất OCR.xlsx/Ai.xlsx sau khi chạy xong; `ocr` tự xuất OCR.xlsx. Workbook review V1.1 vẫn được giữ để tương thích. Có thể chọn `--sample-id` để chạy một chứng từ; mỗi run-id mới được chạy lại cùng file, mỗi run vẫn bỏ alias SHA-256.

## Xuất offline từ kết quả có sẵn

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ocr --run ocr-001
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ai --run invoice-001

# Kiểm tra đúng cả sample_id + hash + relative_path rồi tạo hai file chung thư mục.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-pair --ocr-run ocr-001 --ai-run invoice-001
```

Không cần JWT, backend, Ollama khi xuất hoặc đánh giá cache. `export-pair` từ chối hai run khác bộ mẫu trước khi xuất. `dataset_comparison.json` ghi rõ phạm vi cũ và dataset hiện tại: added/removed/changed, số file/nội dung mới. Khi thư mục dataset không còn trên máy, vẫn xuất hai run lịch sử khớp nhau, ghi current_dataset_matches=null thay vì khẳng định đã quét bộ hiện tại.

Không ghi đè file đã có. Hai file của `export-pair` nằm tại `reports/paired-<UTC timestamp>/OCR.xlsx` và `Ai.xlsx`. File OCR chỉ có sheet OCR; file AI giữ đúng 3 sheet Ai, Hang_hoa, Kiem_tra_GT.

## Ground Truth OCR riêng

Trong OCR.xlsx, mỗi chứng từ đúng một dòng: sample ID/tên file, nguồn OCR/PDF_TEXT_LAYER/MIXED, văn bản nhận dạng, ứng viên, văn bản chuẩn, trạng thái xác nhận, người kiểm, CER/WER, thời gian ms và tham chiếu raw JSON. OCR trả về trước khi AI lỗi vẫn được ghi trạng thái OCR độc lập.

1. Mở chứng từ gốc, đối chiếu toàn bộ văn bản.
2. Nhập văn bản chuẩn vào ô vàng **Văn bản chuẩn GT**, không tự coi ứng viên đúng.
3. Đặt **Trạng thái xác nhận=CONFIRMED**, **Đã đối chiếu bản gốc=YES**, **Người xác nhận=username API**.
4. Lưu thành OCR_reviewed.xlsx rồi import bằng ADMIN/ACCOUNTANT:

```powershell
$env:BENCHMARK_JWT = '<JWT thật của người xác nhận>'
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-ocr --excel .\benchmark_runner\reports\OCR_reviewed.xlsx

# Xuất lại để tính CER/WER, không chạy OCR/AI.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-pair --ocr-run ocr-001 --ai-run invoice-001
```

Import kiểm tra workbook/snapshot/run/hash nguồn/metadata/raw text/candidate và danh tính qua API. DRAFT không tính metric; người khác username hoặc thiếu attestation bị INVALID; sửa dữ liệu nguồn bị từ chối toàn workbook. Chỉ GT hợp lệ tạo file JSON mới tại `ground_truth/ocr/<sample_id>/v000001.json`, có thời gian UTC, danh tính, hash phiên bản trước và review_source. Không tạo hoặc sửa GT các trường AI.

Văn bản dài chia cột raw_text_2/gt_candidate_2/confirmed_text_2... theo 30.000 ký tự, vẫn một dòng mỗi chứng từ, không cắt mất nội dung. Ghép GT theo thứ tự part, không thêm newline; cột chuẩn là Text, không phải công thức. Dùng F2/Formula Bar hoặc tăng chiều cao dòng để xem toàn văn bản dài trong ô.

Văn bản hiển thị dùng newline LF (CRLF→LF như normalizer CER/WER), vì một số XML/Excel writer biến CRLF thành hai lần xuống dòng. Raw JSON gốc giữ nguyên. Import file cũ chấp nhận đúng các cách biểu diễn newline do writer tạo, vẫn kiểm checksum nguồn và không chấp nhận tùy tiện sửa nội dung.

## Chuyển đổi Excel GT cũ

`import-ocr` cũng nhận workbook V1.1 có Documents_Review/OCR_Review/Run_Info và snapshot gốc:

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-ocr --excel .\benchmark_runner\reports\reviewed_results.xlsx
```

Tất cả OCR part của mẫu phải có gt_checked=YES và text đã kiểm; Documents_Review phải ghi reviewed_original=YES/confirmed_by khớp người import. Có thể để trạng thái **AI document DRAFT** vì đây là xác nhận riêng văn bản OCR; chương trình không nâng AI thành CONFIRMED.

GT JSON cũ đã xác nhận có `ocr.checked=true` được dùng trực tiếp khi chưa có GT OCR riêng. GT OCR riêng ưu tiên nếu có. GT cấu trúc AI V1.0/V1.1 vẫn được Ai.xlsx đọc như trước; `import-ai` không ghi đè namespace OCR. Không nhận một workbook tùy ý không có snapshot và metadata làm đáp án chuẩn.

## Metric và nghiệp vụ

- OCR: NFC/CRLF, CER theo ký tự và WER theo whitespace như V1.0. Tổng hợp bằng tổng edit distance / tổng độ dài GT, không lấy trung bình tỷ lệ từng mẫu. Reference rỗng cho tỷ lệ undefined; không đổi thành 0/100%. PDF text layer được phân nguồn riêng, không gọi là Tesseract.
- AI: TP/FP/FN, field/item/document F1, Core Micro-F1 và Document Macro-F1. Metric chính thức từng nhóm chỉ gộp các chứng từ có đủ GT đọc được trong phạm vi nhóm; các trường unsupported loại riêng, UNREADABLE thống kê riêng. GT V1 đã đủ core vẫn được tính core, nhưng không dùng GT thiếu các trường mới để công bố F1 toàn phạm vi mới.
- Nghiệp vụ: quantity×unitPrice, VAT amount×rate, chiết khấu base×rate, subtotal/discount/allowance/VAT/total, thanh toán và chênh lệch AI-GT. Chỉ tính khi đủ số và báo căn cứ cần kiểm; không lấy giá trị thiếu làm 0. Đây là số học chẩn đoán, không tự kết luận nghĩa vụ thuế/thanh toán/căn cứ giảm trừ và không nới F1 exact match.
- GT ứng dụng sau này phải chứng minh chứng từ/phiên bản đã được kế toán duyệt và khớp SHA-256, kèm người/thời gian xác nhận. Chỉ xác thực tài khoản không đủ chứng minh nội dung nghiệp vụ đúng. Lượt này không đọc/ghi DB nghiệp vụ hoặc sửa API Java.

## Phạm vi dữ liệu thực tế đã xuất

`ocr-001` và `invoice-001` có cùng **10 file/ID/hash**, không có bản trùng nội dung. OCR có **10 thành công/11 trang PDF text layer**, AI **9 thành công/1 lỗi OLLAMA_TOKEN_LIMIT**, **31 dòng hàng**. Chưa có GT thực xác nhận: CER/WER/F1 để trống.

Dataset hiện tại đã tăng lên **64 file, 64 nội dung riêng**, thêm **54 file mới** so với hai run cũ; không sửa hay mất 10 file cũ. Cặp workbook lịch sử không được coi là báo cáo đủ 64 file. Muốn xử lý toàn bộ 64 file, chạy `both-full-001 --mode both` bằng run-id mới; không gọi lại AI chỉ để xuất hai file lịch sử.
