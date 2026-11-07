# Xuất và xác nhận Ai.xlsx

## Import file GT ngoài (GTDOAN.xlsx)

Từ gốc dự án, kiểm tra offline trước (không ghi GT, không cần JWT):

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-external-gt --excel "D:\baocao\GTDOAN.xlsx" --run ai-full-001
```

Hỗ trợ hai sheet `GT_Chung_tu`, `GT_Hang_hoa`, ghép theo Mã mẫu/Tên file và STT nguyên dương duy nhất mỗi mẫu. Chỉ nhập chứng từ CONFIRMED; loại không hợp lệ trả INVALID. File gốc được đối chiếu SHA-256 với run. Dòng hàng của GT là danh sách độc lập, cột Thao tác dòng phải là KEEP (hoặc không có); không cần số dòng GT bằng AI. Số hóa đơn và MST phải là Text. Công thức và ô lỗi bị từ chối.

Sau khi đối chiếu toàn bộ PDF/ảnh, lưu thực tế bằng tài khoản có username trùng Người xác nhận trong Excel:

```powershell
$env:BENCHMARK_JWT = '<token thật của ADMIN hoặc ACCOUNTANT>'
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-external-gt --excel "D:\baocao\GTDOAN.xlsx" --run ai-full-001 --commit --reviewed-original
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ai --run ai-full-001
```

Mặc định ô trống được loại khỏi F1 bằng trạng thái UNREADABLE tương thích schema hiện tại; không kết luận chúng thực sự không đọc được trên bản gốc. Nguồn import ghi rõ quy ước này. `--blank-policy absent` chỉ dùng nếu đã xác nhận mọi ô trống trong các cột được ánh xạ là không có trên chứng từ. Các trường không có cột luôn bị loại. Core F1 không có nếu core còn trường bị loại; xem F1 từng nhóm/trường. Cột Chiết khấu chưa ánh xạ vì chưa phân biệt tỷ lệ và số tiền. Không nhập GT OCR từ file này; giữ GT OCR đã có. Không ghi DB hay gọi lại AI.

Kết quả READY là kiểm tra thành công, IMPORTED là đã lưu phiên bản mới, UNCHANGED là không đổi; UNCONFIRMED/INVALID cần sửa. Mẫu hợp lệ vẫn được nhập nếu mẫu khác thiếu loại; mã thoát 2 báo còn mẫu chưa hợp lệ. Lỗi cấu trúc/hash/định danh chặn toàn bộ trước khi ghi. File thiếu mẫu so với run được liệt kê tại missing_samples.

`src/ai_workbook.py` đọc evidence JSON đã lưu, không gọi Java/Ollama khi xuất hay đánh giá. Không thay đổi model, prompt, OCR, parser Java, database hoặc workflow nghiệp vụ. Chưa có file mẫu đính kèm để đối chiếu layout, nên workbook dùng đúng 3 sheet và các nhóm trường đã yêu cầu.

## PowerShell từ gốc dự án

```powershell
# Xuất toàn run có sẵn, không chạy AI lại.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ai --run invoice-001

# Chỉ xuất một mẫu (sample_id xem trong results/invoice-001/config.json).
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ai --run invoice-001 --sample-id S_d294d7ef7ee4fe46a74d49c2

# Sau khi đối chiếu và lưu Excel đã sửa thành Ai_reviewed.xlsx:
$env:BENCHMARK_JWT = '<token thật của ADMIN hoặc ACCOUNTANT>'
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-ai --excel .\benchmark_runner\reports\Ai_reviewed.xlsx

# Xuất lại để xem GT và metric; không gọi OCR/AI.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ai --run invoice-001
```

File đầu tiên là `reports/Ai.xlsx`. Nếu đã tồn tại thì xuất vào `reports/<run_id>-<UTC timestamp>/Ai.xlsx`, không ghi đè. CLI in đường dẫn chính xác. Đi kèm `Ai.summary.json` (tổng hợp + trường thiếu), `Ai.gt.json` (snapshot GT đã dùng); workbook vẫn chỉ có **Ai, Hang_hoa, Kiem_tra_GT**.

Nếu cần xử lý AI mới, dùng run-id mới để giữ kết quả cũ:

```powershell
# Cả dataset; cần API benchmark ADMIN và Ollama sẵn sàng.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py run --run-id invoice-002 --mode ai

# Một mẫu; vẫn quét/hash dataset trước, alias cùng nội dung giữ SKIPPED_DUPLICATE.
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py run --run-id single-001 --mode ai --sample-id S_d294d7ef7ee4fe46a74d49c2
```

Không dùng nâng cấp exporter để ép resume run cũ có runner_hash khác; các kết quả cũ vẫn xuất được offline. File cùng SHA-256 chỉ có một mẫu đại diện để tính metric; alias được ghi trạng thái trong sheet Ai, không thêm sheet thứ tư.

## Ý nghĩa dữ liệu

- **Ai:** mỗi chứng từ một dòng, trường nghiệp vụ là cột riêng. Giá trị là AI đã parse/validate của đúng attempt trong checkpoint. Có model, lỗi, tham chiếu JSON, file gốc, F1 từng nhóm, Core F1 và các cột Run_Core_Micro_F1/Run_Document_Macro_F1. `other_fields_json` giữ nguyên các trường tự do chưa ánh xạ, kể cả tên trường trùng nhau. Không dùng tên field chung chung như “MST” để tự suy ra người bán/người mua.
- **Hang_hoa:** mỗi dòng AI một dòng riêng, có sample_id/row_id, 10 trường yêu cầu. Cột `GT - ...` hiển thị dữ liệu đã xác nhận nếu có. Dòng chỉ có trong GT vẫn hiện riêng với `new_gt_...`, AI để trống; không sửa hay thêm vào prediction gốc.
- **Kiem_tra_GT:** mỗi trường header hoặc mỗi cell item là một dòng, tách **AI Prediction**, **GT ứng viên**, **GT xác nhận**. Ứng viên chép từ AI nhưng mặc định `UNCHECKED`; GT xác nhận để trống. Các cột AI/candidate/nguồn/counter khóa và được kiểm tra với snapshot phía filesystem khi import.

Các trạng thái kiểm tra:

| Trạng thái | Ý nghĩa |
|---|---|
| PREDICTED_UNVERIFIED | AI có giá trị; chưa được coi là đúng |
| NOT_RETURNED_NEEDS_REVIEW | Schema hỗ trợ nhưng AI chưa trả giá trị; chưa biết chứng từ không có hay AI bỏ sót |
| NOT_SUPPORTED | Schema/adapter hiện tại chưa có nguồn cho trường này; không giả tạo giá trị |
| RUN_FAILED | Attempt lỗi, không biến kết quả trang dở dang thành trích xuất thành công |
| ABSENT_CONFIRMED | GT đã xác nhận không có và AI cũng không tạo giá trị |
| MISSED_BY_AI | GT xác nhận có, AI bỏ trống một trường được hỗ trợ |
| VALUE_MISMATCH | AI sai giá trị hoặc tạo giá trị khi GT xác nhận không có |
| MATCH / UNREADABLE | Khớp theo normalizer / không đọc được và loại khỏi F1 giá trị |

Schema Java chưa có `discountRate`, `lineTotal` riêng cho mỗi item; không lấy amount để tự tạo thêm “thành tiền sau thuế”. `amount` luôn là amount gốc của pipeline. Không lấy `tenderedAmount/changeAmount`, hình thức TM/CK hoặc tổng hóa đơn để suy ra đã thanh toán/còn nợ. Nợ/Có/đối tượng kế toán không được suy từ một tài khoản AI gợi ý. `transactionType` và `accountingAccountSuggestion` ghi rõ là đề xuất, đánh giá riêng nhóm Kế toán.

Chỉ ánh xạ mã tra cứu qua các tên rõ nghĩa `lookupCode/invoiceCode/searchCode`. Nếu nhiều tên trả giá trị khác nhau, giữ raw JSON và không chọn tùy tiện. Chiết khấu chỉ lấy từ giá trị explicit của extraFields hoặc `retailDetails.billDiscount/lineDiscounts` đúng rowNumber, không tính ngược tỷ lệ. Trường không được hỗ trợ nhưng người kiểm tra bổ sung GT được lưu để đánh giá phiên bản sau; không trộn vào F1 các trường đã được hỗ trợ.

## Xác nhận Ground Truth

Chỉ sửa ô vàng trong **Kiem_tra_GT**; không sửa Ai/Hang_hoa để thay prediction. Điền `GT xác nhận`, chọn trạng thái:

- `PRESENT`: có giá trị, phải nhập riêng GT xác nhận sau khi xem chứng từ.
- `ABSENT`: xác nhận không có; để trống GT xác nhận.
- `UNREADABLE`: không đọc được; để trống GT xác nhận.
- `NOT_SUPPORTED`: giữ mặc định với trường chưa hỗ trợ; không được dùng để bỏ qua trường đã hỗ trợ. Có thể chuyển thành PRESENT/ABSENT khi muốn lưu GT cho trường mở rộng.

Với mỗi mẫu, tìm dòng `kind=DOC`, `field_key=documentType`: chỉ sau khi kiểm đủ phạm vi và mọi item mới đặt **Xác nhận chứng từ=CONFIRMED**, **Đã đối chiếu bản gốc=YES**, **Người xác nhận=username API**. Chỉ dòng documentType có các ô điều khiển này. Import lấy ID người xác nhận từ `/api/v1/users/me`, không chỉ tin tên nhập Excel. Nếu chứng từ chưa xác định loại vì AI lỗi, phải nhập GT loại hợp lệ và kiểm đủ core tương ứng.

Mỗi item là một block 10 dòng cùng sample_id và row_id. Ô **Xử lý dòng** / **Thứ tự GT** chỉ sửa ở dòng productName:

- `KEEP`: sửa GT từng cell; giữ raw AI.
- `EXTRA`: dòng AI thừa; xóa các GT xác nhận trong cả block, giữ block prediction để truy vết.
- `ADD`: copy một block 10 dòng sang cuối sheet, đổi row_id thành `new_1`/`new_2` riêng, đặt ADD và thứ tự dương chưa dùng ở productName. Xóa AI Prediction, GT ứng viên và toàn bộ cột điểm/matched_gt_row/gt_version trong block mới; nguồn để trống hoặc MANUAL_ADDITION. Nhập GT và trạng thái cho đủ 7 trường item chuẩn; 3 trường mở rộng chưa hỗ trợ có thể giữ NOT_SUPPORTED. Hai block cùng nội dung với row_id khác vẫn là hai dòng GT.

Nếu cần thêm dòng/trường, dùng **Review → Unprotect Sheet** (không password); kiểm tra tính toàn vẹn vẫn hoạt động. DOC field mới dùng kind=DOC, row_id=document, field_key CamelCase mới; AI/candidate/điểm để trống. Import từ chối sửa metadata/AI, công thức Excel, xóa dòng gốc, trùng key/thứ tự hoặc GT chưa kiểm đủ. Mẫu hợp lệ lưu JSON phiên bản mới, mẫu khác báo UNCONFIRMED/INVALID; CLI trả mã 2 nếu còn lỗi. Không có dữ liệu nào ghi DB.

GT V1.0/V1.1 đã xác nhận được đọc trực tiếp từ `ground_truth/<sample_id>/v*.json`. Trường đã kiểm và văn bản OCR chuẩn được giữ; các trường mới chưa kiểm không được tự nâng thành xác nhận. `item_extensions` lưu GT chiết khấu/lineTotal riêng, 7 cell item cũ giữ tương thích. Lặp import đúng file trả UNCHANGED; mọi phiên bản trước và JSON prediction vẫn giữ nguyên.

## Đánh giá

Sau import, chạy `export-ai` lại để cập nhật workbook; sửa ô trong Excel không tự tính F1 tức thì. Chưa có GT hợp lệ thì TP/FP/FN và tỷ lệ để trống, không lấy candidate làm đáp án.

- Sai giá trị: FP+FN; bỏ sót: FN; tự tạo khi GT ABSENT: FP. Exact match theo normalizer V1.0, thêm quy tắc discountRate như taxRate và lineTotal như amount.
- Item ghép Hungarian một-một theo tên chính xác sau chuẩn hóa; không gộp dòng trùng, không chỉ ghép theo vị trí. Tên sai có thể tạo một dòng thừa và một dòng thiếu theo quy tắc công bố này.
- F1 riêng Chứng từ, Hàng hóa, Tiền, Thanh toán, Kế toán đề xuất. Core Micro-F1 chỉ dùng mẫu đủ core đọc được; Document Macro-F1 là trung bình Core F1 hợp lệ. Field Accuracy tính các cell có GT đọc được và schema hỗ trợ, gồm vắng đúng; không tính trường unsupported/unreadable.
- `AI_minus_GT`, absolute_difference và money_error MAE/bias được báo riêng, không làm nới điều kiện exact F1.
- `header_arithmetic_delta = totalAmount - subtotal - vatAmount`; `item_sum_minus_subtotal`; `quantity_price_arithmetic_delta`; `payment_arithmetic_delta = paid + outstanding - total`. Chỉ tính khi có đủ số, không lấy giá trị thiếu làm 0. Đây là kiểm tra số học, không kết luận hóa đơn hợp pháp, đầy đủ dòng, đã thanh toán hay đã hiểu đúng căn cứ chiết khấu/giảm trừ.

## Bàn giao và kiểm chứng

Code: `src/ai_workbook.py`, CLI trong `main.py`, test `tests/test_ai_workbook.py`. File đã xuất từ `invoice-001`, có 10 mẫu, 9 thành công, 1 lỗi token limit, 31 dòng hàng AI, 634 dòng kiểm tra; chưa có GT xác nhận thực tế nên không công bố F1. `Ai.summary.json` ghi đầy đủ các trường chưa trả/chưa hỗ trợ/lỗi; `Ai.verification.json` ghi số cell so sánh với JSON và snapshot. Xem báo cáo thực tế đi kèm workbook.
