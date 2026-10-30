# InvoiceFlow Mobile — trạng thái triển khai

Nguồn thiết kế: https://www.figma.com/design/YhWyLrrRG94Ihc0qo28yuD?node-id=0-1
Ứng dụng: invoice_1 (Flutter / Riverpod / GoRouter).

## Phạm vi đã nối với API

- Nhân viên: đăng nhập, tổng quan, danh sách, tìm kiếm, lọc xử lý và duyệt, phân trang, upload/xem trước, tiến độ truyền tệp và chờ máy chủ, chi tiết thu gọn, phản hồi kế toán, tải phiên bản/gửi lại, xem file, tài khoản, đổi mật khẩu.
- Kế toán: tổng quan, hàng đợi, bắt đầu duyệt, duyệt, từ chối, yêu cầu bổ sung với lý do bắt buộc; xem thu/chi phân trang, lọc loại, chi tiết và chứng từ liên kết.
- Admin: tổng quan, danh sách và chi tiết chứng từ chỉ xem, giám sát lỗi/cần kiểm tra, số tài khoản hoạt động, danh sách người dùng/tìm kiếm/chi tiết.
- Điều hướng dựa trên vai trò có thứ tự ưu tiên ADMIN > ACCOUNTANT > EMPLOYEE > USER.
- Quyền API cuối cùng vẫn do backend kiểm tra. Chỉ EMPLOYEE có nút upload trong thanh điều hướng; API tải phiên bản cần quyền DOCUMENT_UPDATE.
- Font Inter và Noto Symbols được đóng gói cục bộ cùng giấy phép.
- Thông báo chưa có API: hiển thị trạng thái chưa khả dụng.
- Chỉnh OCR/AI, phân quyền chi tiết và thao tác thu/chi phức tạp tiếp tục thực hiện trên web.

## Độ phủ đối chiếu Figma

Đã lấy design context và screenshot chi tiết cho các frame:
01, 02, 03, 07, 08, 09, 10, 11, 12, 15, 16.

Hạn mức MCP đã được bù bằng bộ ảnh ZIP người dùng cung cấp: đã giải nén và kiểm đủ 27 màn hình. Không có chỉnh sửa file Figma.

Đã bổ sung/chỉnh các màn 04–06, 13–14, 17–27 theo ảnh, gồm tổng thu chi theo tháng, trang duyệt riêng, ghi chú phổ biến, chuyển sang Web và biểu đồ admin. Xem [báo cáo đối chiếu từng màn](FIGMA_RECHECK.md).
Các số liệu quá hạn/chưa phân công hiện thiếu dữ liệu vì API chưa cung cấp. Trạng thái bị trả lại hiện tích hợp trong chi tiết thay vì một trang riêng; bố cục có điều chỉnh để hỗ trợ màn 320px và dữ liệu thật.

## Các hợp đồng API sử dụng

- POST /auth/login; GET /users/me; PUT /users/me/password
- GET /dashboard/statistics
- GET /documents (search, processingStatus, reviewStatus, page, size)
- POST /documents/upload; POST /documents/{id}/versions
- GET /documents/{id}, /ocr, /classification, /invoice, /extracted-fields
- GET /documents/{id}/business-state (thanh toán và lịch sử/ghi chú phản hồi)
- POST /documents/{id}/workflow (action, note)
- GET /documents/{id}/preview (bytes, kèm Bearer token)
- GET /financial-transactions (page, size, transactionType)
- GET /dashboard/financial (dateFrom, dateTo); giao dịch cũng hỗ trợ khoảng ngày
- GET /documents/{id}/assessment
- GET /users (admin)

Đếm Đã duyệt/Cần bổ sung bằng totalElements của truy vấn có reviewStatus trên server. Không suy ra từ danh sách gần đây.
Tiến độ truyền file không phải tiến độ OCR/AI. Workflow SUBMIT/RESUBMIT gọi xử lý AI ở controller backend.
APPROVED không chứng minh đã thanh toán.

## Kiểm tra

- flutter test --no-pub: kiểm tra model, hợp đồng API, trạng thái, bộ lọc/phân trang và điều hướng ba vai trò ở 390px/320px.
- flutter analyze --no-pub: mã mới không có error/warning; còn các lint info có sẵn ở summary_card.dart.
- flutter build apk --debug --no-pub.
- Ảnh kiểm tra mới: ../artifacts/mobile-ui-review-v2/ (dữ liệu fixture trong test, không phải dữ liệu app hardcode).
- Chưa chạy kiểm thử xuyên suốt bằng tài khoản thật; localhost:8081 không phản hồi trong lần kiểm tra phiên này.

## Chạy với backend

Giữ cấu hình API_BASE_URL sẵn có:
- Android emulator: flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1
- Điện thoại cùng mạng: thay host bằng IP LAN của máy chạy backend.
- Dùng mặc định localhost trên điện thoại USB cần adb reverse tcp:8081 tcp:8081.

APK debug dùng giá trị mặc định API_BASE_URL=http://localhost:8081/api/v1 nếu build không truyền dart-define.

Nút “Mở trên Web” dùng WEB_APP_URL, mặc định http://localhost:5173. Khi chạy emulator có thể dùng --dart-define=WEB_APP_URL=http://10.0.2.2:5173; điện thoại thật dùng IP LAN tương ứng hoặc adb reverse cho cổng Web. Phiên trình duyệt yêu cầu đăng nhập riêng.

