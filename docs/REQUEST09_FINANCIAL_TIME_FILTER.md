# Đồng bộ thời gian Dashboard — Báo cáo — Thu/Chi

## Khảo sát trước sửa
- Branch dev/ai; Git đã có nhiều file sửa và file chưa theo dõi từ các yêu cầu trước. Giữ nguyên, không commit/push/reset. Bản status trước/sau nằm trong .runtime/request09-financial (ignored).
- AccountantDashboard chỉ chuyển interval, không gửi khoảng ngày; nhãn kỳ không phản ánh dữ liệu API.
- Reports có từ/đến nhưng luôn tổng hợp MONTHLY, chỉ tải khi bấm áp dụng; trạng thái riêng từng trang.
- Sổ quỹ đã nhận ngày ISO, có company scope và xuất Excel; bộ lọc riêng không đồng bộ Dashboard.
- API /financial và /financial/time-series đã tồn tại. Query chỉ cộng payment_confirmation=CONFIRMED, bao gồm cả hai đầu transactionDate. Hủy hiện chuyển paymentConfirmation thành CANCELLED; giao dịch cũ NULL không được tính.
- API time-series trước đây không có companyId tương ứng API totals, dùng SQL PostgreSQL date_trunc/to_char và chỉ trả mốc có dữ liệu.

## Thay đổi
- Component FinancialTimeFilter có MONTH/DAY/RANGE, chọn tháng/năm và nút trước/sau.
- Giữ độc lập month/day/from/to: đổi chế độ không ghi đè các lựa chọn ẩn.
- Mặc định RANGE đầu tháng đến hôm nay theo Asia/Ho_Chi_Minh. Không hardcode tháng 10/2026 trong production; test truyền clock ngày 2026-10-17.
- URL lưu timeMode,month,day,rangeFrom,rangeTo cùng dateFrom/dateTo đang có hiệu lực; giữ query khác (companyId, liên kết phiếu…). SessionStorage theo user ID dùng để khôi phục khi đi sang trang tài chính khác không có query. URL có lựa chọn được ưu tiên, hỗ trợ link/back/forward theo URL.
- Dashboard, Reports, FinancialTransactions dùng cùng hook/component. Ngày hợp lệ gửi yyyy-MM-dd; draft sai/thiếu/ngược khoảng chặn API thống kê/export.
- MONTH: đầy đủ từng ngày trong tháng. DAY: một bucket ngày, ba chỉ số Thu/Chi/dòng tiền, không nhóm giờ vì transactionDate là LocalDate. RANGE: DAILY đến 93 ngày, MONTHLY nếu dài hơn (số ngày chênh >92).
- API time-series mở rộng companyId tùy chọn, dùng cùng resolver scope với totals. Không tạo endpoint mới, giữ đường gọi timeSeries 3 tham số để tương thích nội bộ.
- Repository dailyTotals dùng JPQL nhóm theo LocalDate; cộng BigDecimal giữ nguyên công thức/điều kiện CONFIRMED. FinancialSeries gộp tháng và bổ sung mốc 0 chỉ trong khoảng được truy vấn. Không phát sinh bản ghi cho mốc 0.
- Nếu client cũ không truyền đủ hai đầu: chỉ lấp khoảng dựa trên dữ liệu quan sát được; không có dữ liệu trả danh sách rỗng. Khoảng >10001 bucket bị từ chối để tránh tạo phản hồi vô hạn.
- Frontend phân biệt loading, API error và không phát sinh số tiền trong kỳ. Request cũ không được ghi đè kết quả request mới khi đổi bộ lọc (effect cleanup, sequence cho Sổ quỹ).
- Xuất Excel Dashboard/Reports/Sổ quỹ gửi cùng ngày và company đang chọn. Sheet tổng hợp tiếp tục chỉ tính CONFIRMED; sheet loại trừ giữ giao dịch chưa xác minh/hủy.
- Các KPI/tables chứng từ trên Dashboard vẫn là dữ liệu hiện có toàn thời gian; không giả vờ đã lọc theo ngày thu/chi. Hint tổng tiếp nhận đã ghi rõ phạm vi này.

## File sửa trong lượt bổ sung này
Backend (backendqt/backendqt):
- src/main/java/com/example/invoice/controller/DashboardController.java: timeSeries thêm companyId.
- src/main/java/com/example/invoice/service/DashboardService.java: financialScoped/timeSeries/timeSeriesScoped/resolveFinancialCompany.
- src/main/java/com/example/invoice/repository/FinancialTransactionRepository.java: dailyTotals thay query native timeSeries.
- src/main/java/com/example/invoice/util/FinancialSeries.java: nhóm/lấp mốc thời gian, giữ số tiền chính xác.
- src/test/java/com/example/invoice/PaymentIntegrationTest.java: thêm ba bài test API/DB H2/Excel, không sửa chính sách fixture cũ.
Frontend (quan tri):
- src/utils/financialTimeFilter.ts, financialTimeFilter.test.mjs.
- src/hooks/useFinancialTimeFilter.ts.
- src/components/FinancialTimeFilter.tsx, FinancialTimeFilter.test.mjs.
- src/pages/accountant/AccountantDashboard.tsx, Reports.tsx, FinancialTransactions.tsx.
- src/services/dashboardService.ts: hỗ trợ companyId time-series.
- package.json: bổ sung test.
- Báo cáo này.

## Kiểm thử và bằng chứng
- Backend clean test Java21 offline, JVM test -Duser.timezone=UTC, datasource H2 độc lập: 431 total = 418 PASS, 0 FAIL/Error, 13 SKIP.
- Ba integration test mới trong PaymentIntegrationTest:
  1. 01/10/2026–17/10/2026 có đủ 17 bucket. Thu 100.25, Chi 30.10; loại giao dịch ngoài ngày, khác công ty, chưa xác minh, đã hủy. Tổng bucket bằng query summarize trên DB test, bằng API totals và workbook Excel. Kiểm tra ngày Excel và số dòng chi tiết.
  2. 31/10–01/11, 31/12–01/01, 29/02 năm nhuận; DAILY/MONTHLY, đủ 29 ngày tháng 2/2024 và ngày không phát sinh bằng 0.
  3. ACCOUNTANT bị chặn công ty khác; EMPLOYEE không có quyền; ADMIN chọn đúng công ty; ngày đảo ngược và HOURLY không hợp lệ trả 400.
- Frontend 50 PASS, 0 FAIL/SKIP: giữ test ngày ở UTC/Los Angeles/Tokyo, thêm test default clock, URL round-trip/giữ query và pagination, đổi chế độ, tháng/năm/năm nhuận, render và event điều hướng tháng.
- Frontend build đạt; còn warning chunk lớn có sẵn. git diff --check không lỗi.

## Tương thích và giới hạn
- Không chạy backend thật, truy vấn/ghi PostgreSQL thật, migration hoặc sửa dữ liệu/schema. Không thay đổi invoice_1, Auth/JWT/RBAC, OCR/AI, công thức thanh toán hay chính sách xác nhận.
- Đối chiếu DB là H2 test độc lập, CHƯA xác nhận tổng tiền invoice_db hiện tại. JPQL mới chưa kiểm chứng trực tiếp PostgreSQL 18.6.
- Không chuyển LocalDate thành timestamp để gửi API. Business timezone được xác định tường minh ở frontend; date grouping backend độc lập timezone, đã chạy suite JVM UTC.
- Không đổi kiểu LocalDateTime lịch sử hoặc gắn offset giả. Chuẩn hóa toàn bộ thời điểm tạo/cập nhật sang Instant cần khảo sát/migration riêng như báo cáo Request09 trước.
- Test UI là SSR/event + kiểm tra URL thuần, chưa phải browser E2E click điều hướng thực. Không kết luận đã kiểm chứng runtime production.
- Scope ADMIN không truyền companyId giữ hành vi API tổng hợp mọi công ty như trước. Frontend ưu tiên companyId trên URL, mặc định công ty tài khoản. ADMIN không có công ty cần companyId rõ ràng để xuất sổ quỹ theo chính sách cũ; không thay policy này.
- UnverifiedTransactionCount hiện vẫn là số lượng cũ toàn công ty, không phải số giao dịch trong kỳ; không cộng vào Thu/Chi.
- Khi có ghi đồng thời giữa hai request totals/time-series, hai phản hồi có thể thuộc hai thời điểm đọc khác nhau; không thêm snapshot báo cáo mới trong yêu cầu này.
