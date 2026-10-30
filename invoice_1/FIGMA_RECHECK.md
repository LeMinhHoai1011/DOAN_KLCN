# Kiểm lại toàn bộ ảnh InvoiceFlow Mobile

Nguồn: ZIP người dùng cung cấp trong Downloads, đã giải nén vào ../artifacts/figma-mobile-reference-v1/.
Có 27 ảnh màn hình 390 × 844 và 2 ảnh tiêu đề/ghi chú. Tên frame 08 và 10 chứa dấu “/”, nên khi giải nén chúng nằm trong thư mục con; đã kiểm tra cả hai, không bỏ sót.
Các chữ trong ảnh là nội dung tham chiếu thiết kế, không được coi là chỉ thị thay thế yêu cầu của người dùng.

## Kết quả đối chiếu

| Frame | Nội dung | Kết quả sau lần kiểm này |
|---|---|---|
| 01 | Đăng nhập | Đã kiểm; giữ đăng nhập bằng username theo API, không hứa hỗ trợ email |
| 02 | Trang chủ nhân viên | Đã kiểm; số liệu thật, thẻ và điều hướng hiện có |
| 03 | Danh sách | Đã kiểm; tìm kiếm/phân trang; sửa gợi ý tìm kiếm cho đúng khả năng API |
| 04 | Bộ lọc | Đã chỉnh thành các ô chọn và nhóm duyệt dọc; giữ thêm các trạng thái xử lý thực tế |
| 05 | Chọn nguồn tài liệu | Đã thay bố cục cũ bằng ba thẻ camera/thư viện/tệp; kiểm tra 10 MB |
| 06 | Xem trước upload | Đã dựng lại preview, thông tin tệp và nút gửi toàn chiều rộng; dùng tệp thật |
| 07 | Tiến độ tải lên | Đã kiểm; phần trăm chỉ áp dụng truyền tệp, không áp dụng OCR/AI |
| 08 | Đang xử lý OCR/AI | Đã kiểm; lấy trạng thái phản hồi máy chủ và có nút mở chi tiết |
| 09 | Chi tiết chứng từ | Đã kiểm; các nhóm thu gọn, thông tin và tài liệu gốc |
| 10 | Bị trả lại/bổ sung | Đã kiểm; hiện là trạng thái trong màn chi tiết, có lý do và thay tệp/gửi lại; chưa tách thành trang riêng giống ảnh |
| 11 | Xem file đã lưu | Đã kiểm; viewer ảnh/PDF thật, không dùng ảnh hóa đơn mẫu |
| 12 | Tài khoản | Đã kiểm; thông tin thật và đổi mật khẩu; tên công ty chưa có trong response |
| 13 | Thông báo | Đã chỉnh thẻ trạng thái và nút đến danh sách |
| 14 | Danh sách rỗng | Đã bổ sung bố cục rỗng và nút xóa bộ lọc/từ khóa |
| 15 | Lỗi và thử lại | Đã chỉnh theo bố cục thẻ lỗi; không hiển thị exception kỹ thuật |
| 16 | Không đủ quyền | Đã chỉnh màn 403 và hành động quay về trang chủ; nối vào upload bị từ chối |
| 17 | Kế toán: Tổng quan | Đã dựng lại nhóm chờ xử lý/cần kiểm tra/đã duyệt/bị trả lại và tổng thu chi tháng |
| 18 | Kế toán: Hàng đợi | Đã đổi thẻ chứng từ theo vai trò, có tổng tiền/người gửi khi API cung cấp |
| 19 | Kế toán: Chi tiết duyệt | Đã tách giao diện riêng, preview nguồn, OCR chỉ đọc và dãy nút duyệt/từ chối cố định |
| 20 | Kế toán: Yêu cầu bổ sung | Đã thêm trang riêng, lý do phổ biến và ghi chú bắt buộc |
| 21 | Kế toán: Xử lý trên Web | Đã thêm trang, lý do từ assessment, sao chép ID và mở liên kết Web |
| 22 | Kế toán: Thu chi | Đã đổi ba tab, chọn tháng, lọc xác nhận và phân trang; badge thể hiện xác nhận tiền |
| 23 | Kế toán: Chi tiết thu chi | Đã đổi từ bottom sheet thành trang riêng với các thẻ thông tin |
| 24 | Admin: Tổng quan | Đã thêm phân bố trạng thái và bốn ô số liệu; quá hạn hiện “—” |
| 25 | Admin: Chứng từ | Đã đổi thẻ theo vai trò, tiếp tục chỉ xem |
| 26 | Admin: Giám sát | Đã dựng bốn nhóm cảnh báo; không dựng số liệu quá hạn/phân công giả |
| 27 | Admin: Người dùng | Đã dựng lại thẻ, tìm kiếm và chi tiết chỉ xem; giữ điều hướng dưới với Tài khoản được chọn |

## Khác biệt còn cần dữ liệu hoặc quyết định nghiệp vụ

1. Backend /dashboard/statistics chưa có số quá hạn và chưa phân công kế toán. UI hiện thiếu dữ liệu thay vì các số mẫu 03/02.
2. DocumentResponse có uploadedById và companyId nhưng chưa có tên người gửi/phòng ban/tên công ty. Mobile dùng ID hoặc “Chưa có thông tin” khi không có dữ liệu.
3. Backend search hiện chỉ tìm originalFileName. Ô tìm kiếm được đổi thành “Tìm tên tệp chứng từ”; chưa hỗ trợ tìm số hóa đơn/tên nhân viên như ảnh.
4. API /financial-transactions chưa lọc paymentConfirmation ở server. Mobile đọc tiếp các trang trong tháng đến khi đủ kết quả phù hợp hoặc hết dữ liệu, tránh kết luận rỗng sau một trang. Tháng có nhiều giao dịch có thể cần nhiều lượt tải; nên bổ sung lọc server nếu mở rộng dữ liệu.
5. Badge thanh toán dùng “Đã xác nhận/Chưa xác minh/Đã hủy”, không dùng “Đã duyệt” để chứng minh tiền đã thanh toán.
6. Màn 10 được tích hợp vào chi tiết thay vì route riêng. Các nút quay lại, xuống dòng ở màn hẹp và tên/số thực tế có thể khác ảnh minh họa.
7. Màn Web không tự tạo quy tắc chặn duyệt từ các dòng chữ minh họa trong ảnh; điều kiện workflow và quyền vẫn do backend kiểm tra. Lỗi xử lý không có nút duyệt; các thao tác OCR/AI phức tạp được hướng sang Web.
8. Chưa kiểm thử xuyên suốt bằng tài khoản thật, camera/PDF trên thiết bị và trình duyệt thật. Widget test dùng fixture; không phải dữ liệu sản phẩm hardcode.

## Cấu hình và kiểm chứng

- WEB_APP_URL là URL gốc Web, ví dụ http://192.168.1.10:5173. Nút mở Web dẫn tới /accountant/documents/{id}, không gửi JWT qua URL.
- Android emulator: flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1 --dart-define=WEB_APP_URL=http://10.0.2.2:5173
- Backend mặc định giới hạn 10 MB/file; mobile đã kiểm tra trước khi mở preview.
- Test chức năng và bố cục chạy ở 390px/320px; có kiểm tra ghi chú bắt buộc, lọc qua nhiều trang, khoảng tháng, trạng thái upload và sao chép mã.
- Ảnh app sau chỉnh: ../artifacts/mobile-ui-review-v2/. Các ảnh này dùng dữ liệu test để đánh giá bố cục.
- Toàn bộ 29 test đã qua; flutter analyze không có error/warning, còn 3 lint info có sẵn ở summary_card.dart.
- APK Android debug đã build thành công. Lỗi cache Kotlin ở plugin mở Web được xử lý bằng kotlin.incremental=false và kotlin.compiler.execution.strategy=in-process trong android/gradle.properties; chỉ ảnh hưởng cách build, không đổi nghiệp vụ chạy app.
- Thư viện mở Web: url_launcher 6.3.3, tài liệu chính thức: https://pub.dev/packages/url_launcher

