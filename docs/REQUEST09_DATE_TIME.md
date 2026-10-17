# Yêu cầu 09 — Ngày tháng và múi giờ

## Khảo sát và quyết định
- Git dev/ai có nhiều thay đổi từ các yêu cầu trước; giữ nguyên, không commit/push/reset. Không sửa invoice_1.
- Native input type=date phụ thuộc locale trình duyệt nên có thể hiện mm/dd/yyyy. Các formatter vi-VN trước đây không padding ngày/tháng và không chỉ định timezone.
- Thu/Chi dùng toISOString lấy ngày UTC ngay khi module nạp, có thể sai ngày Việt Nam và cũ sau nửa đêm. Đã thay bằng businessToday khi mở modal tạo.
- Dashboard/danh sách chứng từ (cả ADMIN/ACCOUNTANT/EMPLOYEE), trang xử lý dùng mapDocument chung. Upload không có ô nhập ngày riêng; ngày upload lấy createdAt qua mapper.
- Chi tiết hóa đơn, lịch sử kiểm duyệt, xử lý, lưu trữ, thu/chi, đánh giá OCR/AI đều có đường hiển thị ngày đã chuẩn hóa.
- Quản lý người dùng hiện không hiển thị createdAt/updatedAt; không thêm trường chỉ để định dạng.
- Reports/Dashboard dùng nhãn kỳ MM/yyyy hoặc dd/MM/yyyy, không sửa dữ liệu series API.

## Hợp đồng
- Ngày nghiệp vụ LocalDate: ISO yyyy-MM-dd qua API và trạng thái form; hiển thị dd/MM/yyyy. Không qua Date/UTC để tránh lùi ngày.
- Ngày giờ: dd/MM/yyyy HH:mm (diễn giải HH. trong yêu cầu thành giờ:phút). Offset/Z chuyển sang Asia/Ho_Chi_Minh.
- Timestamp LocalDateTime cũ không có offset: giữ giờ tường đã lưu, không tự suy diễn timezone. Không sửa entity/kiểu cột, không gắn +07 vào lịch sử chưa được xác minh.
- Timestamp raw expectedUpdatedAt cho kiểm tra đồng thời không được format hoặc đổi timezone trước khi gửi lại API.
- Filter Instant của đánh giá OCR/AI: từ 00:00 đến 23:59:59.999 giờ Việt Nam, gửi ISO UTC. Bộ lọc nghiệp vụ vẫn LocalDate; ngày cuối được bao gồm.
- DateInput text có placeholder dd/MM/yyyy, giữ draft chưa hợp lệ, validation lịch/tháng/năm nhuận. Không còn phụ thuộc native input theo locale. Không bổ sung thư viện lịch.
- Filter từ/đến kiểm tra định dạng và thứ tự trước API/export; backend cũng từ chối khoảng đảo ngược.
- Bộ lọc chứng từ/Thu-Chi/báo cáo lưu sessionStorage để giữ khi điều hướng; pagination không tự tính lại ngày. Nếu browser cấm storage, vẫn giữ state khi phân trang.
- Excel dùng ô ngày thật và number format dd/mm/yyyy, giữ lọc khoảng ngày bao gồm hai đầu.

## File thay đổi trong lượt này (ngoài thay đổi đã có)
Frontend dưới quan tri:
- src/utils/dateTime.ts, dateTime.test.mjs: formatter, parser, lịch, range, timezone.
- src/components/ui/DateInput.tsx, DateInput.test.mjs: component dùng chung và render/event test.
- src/hooks/useDateFilter.ts: lưu bộ lọc khi điều hướng.
- src/services/documentService.ts, dashboardService.ts, financialTransactionService.ts, exportService.ts: mapper/range guards, không đổi endpoint/payload nghiệp vụ.
- src/utils/evaluationPresentation.ts, documentArchivePresentation.ts: thời gian và snapshot date labels.
- src/components/VatInvoicePanel.tsx, BusinessStateCard.tsx, DocumentPaymentPanel.tsx, PaymentHistoryModal.tsx, ProcessingHistoryCard.tsx, OcrAiPerformance.tsx.
- src/pages/accountant/AccountantDocuments.tsx, AccountantDocumentDetail.tsx, AccountantDashboard.tsx, FinancialTransactions.tsx, Reports.tsx.
- package.json: bổ sung test, giữ các test cũ.
Backend dưới backendqt/backendqt:
- src/main/java/com/example/invoice/util/DateRanges.java.
- src/main/java/com/example/invoice/service/DocumentService.java (findPage), FinancialTransactionService.java (findAll), DashboardService.java (financialScoped/timeSeries), ExcelExportService.java (invoices/financialReportScoped/date).
- src/test/java/com/example/invoice/service/DateContractTest.java.
- Báo cáo này.

## Kiểm chứng
- Backend clean test offline Java21 mock/H2: 428 total = 415 PASS, 0 FAIL/Error, 13 SKIP. Không chạy PostgreSQL thật.
- Frontend: 46 PASS, 0 FAIL, 0 SKIP; gồm render/event DateInput, ngày đầu/cuối tháng, năm nhuận, khoảng xuyên tháng/năm, timestamp và kiểm tra kết quả giống nhau ở UTC/Los Angeles/Tokyo.
- Excel test mở workbook thực bằng POI, đối chiếu ngày biên và style. Test dùng invoiceService mock, không xuất dữ liệu thật.
- Frontend production build thành công; còn cảnh báo chunk lớn đã tồn tại.
- git diff --check: không có lỗi whitespace.

## Giới hạn và phần còn lại
- Chưa kiểm thử click toàn bộ màn hình trên browser hay export bằng tài khoản thật; test DateInput là render và event, không phải browser E2E. Chưa test UI navigation/sessionStorage bằng browser tự động.
- Không truy vấn/sửa database, không chạy migration, không khởi động hoặc triển khai backend.
- Code hiện còn nhiều LocalDateTime.now()/LocalDate.now() phụ thuộc JVM default zone. Chưa thể chứng minh múi giờ tất cả timestamp lịch sử hay đổi default toàn JVM mà không ảnh hưởng JWT/jobs. Cần xác minh JVM/OS timezone và nguồn lịch sử trước khi chuẩn hóa runtime; khuyến nghị deployment thống nhất Asia/Ho_Chi_Minh sau kiểm chứng riêng. Không tuyên bố đã giải quyết dữ liệu lịch sử thiếu offset.
- application.yml đang có ddl-auto=update từ thay đổi có sẵn của người dùng. Lượt này không sửa cấu hình đó và không khởi động backend thật.
- Không migration/đổi cột/dữ liệu; không thay RBAC, Company, JWT, OCR/AI hay công thức kế toán.
