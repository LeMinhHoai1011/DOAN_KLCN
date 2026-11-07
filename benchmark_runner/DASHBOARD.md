# Dashboard benchmark cục bộ

Chạy từ `D:\maudoan_2\DOAN_KLCN`:

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py serve
```

Mở **http://127.0.0.1:8765**. Dừng bằng Ctrl+C. Đổi cổng bằng `serve --port 8766`.
Không cần Node, Java hoặc JWT để xem báo cáo. Dashboard độc lập với ứng dụng quản trị, chỉ lắng nghe trên loopback.

Chọn run rồi bấm **Cập nhật báo cáo** để tạo báo cáo Excel/summary từ evidence đã lưu và GT hiện tại; không chạy OCR/AI lại. Báo cáo mới giữ nguyên báo cáo cũ. Ba thẻ tổng quan dùng Core Micro; biểu đồ nhóm dùng phạm vi GT đủ điều kiện của mỗi nhóm. `null` hiển thị chưa có điểm, không đổi thành 0. GT chưa đủ core sẽ không có Core Micro dù các nhóm khác có điểm. Document Macro F1, TP/FP/FN và số mẫu được hiển thị riêng.

Mục OCR lấy bản OCR.summary.json mới nhất cho từng run, ghi rõ run nguồn; không giả định cùng run AI. Để tạo báo cáo OCR:

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py export-ocr --run ocr-full-001
```

Import file ngoài trước bằng kiểm tra offline:

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-external-gt --excel "D:\baocao\GTDOAN.xlsx" --run ai-full-001
```

Sau khi đã đối chiếu bản gốc, dùng username đúng Người xác nhận trong Excel:

```powershell
$env:BENCHMARK_JWT = '<token thật>'
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py import-external-gt --excel "D:\baocao\GTDOAN.xlsx" --run ai-full-001 --commit --reviewed-original
```

Quay lại dashboard, bấm **Cập nhật báo cáo**. Ô trống của GT ngoài mặc định loại khỏi điểm; không tự coi là không có. Chi tiết quy ước trong AI_XLSX.md.

Nếu cần chạy benchmark mới (cần Java adapter và JWT ADMIN), dùng run-id mới để giữ evidence cũ:

```powershell
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py health
.\benchmark_runner\.venv\Scripts\python.exe .\benchmark_runner\main.py run --run-id ai-full-002 --mode ai
```

Dashboard hỗ trợ tìm file/trường, lọc sai/bỏ sót hoặc trường chưa chấm, xem tối đa 500 dòng phù hợp để trình duyệt phản hồi nhanh. UNREADABLE/NOT_SUPPORTED cũng nằm trong bộ lọc chưa có GT/điểm; không có nghĩa là lỗi AI. Các mẫu lỗi có bảng trạng thái riêng. Không nhập token trên giao diện và không có thao tác chạy model từ trình duyệt.
