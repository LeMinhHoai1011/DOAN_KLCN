# Chẩn đoán lỗi tải Dashboard / Thu–Chi / Báo cáo — 17/10/2026

## Kết luận có bằng chứng
**Root cause query: CONFIRMED — PostgreSQL SQLSTATE 42P18.**
Repository dùng (:dateFrom is null or t.transactionDate >= :dateFrom) và tương tự dateTo. Hibernate/PG JDBC sinh các placeholder khác nhau; PostgreSQL không suy ra kiểu cho placeholder dùng riêng trong IS NULL, dù tham số ngày có giá trị.

Khi tái hiện bằng Repository hiện tại, naming strategy giống Spring Boot:
- entity_findAll: OK 33 (đọc toàn bộ mapping Entity).
- summarize_2026-10-01 / dailyTotals_2026-10-01 / expenses_2026-10-01: ERROR PSQLException, SQLSTATE=42P18, could not determine data type of parameter $3.
- Cả ba query với ngày 2026-10-17: cùng lỗi.
- summarize_null_dates: OK. Đây là lý do test/luồng không lọc ngày có thể không phát hiện lỗi.
- countUnverified(13): OK 32.
Không quy các lỗi của harness phát triển ban đầu thành lỗi ứng dụng; bằng chứng trên lấy sau khi cấu hình đúng CamelCaseToUnderscoresNamingStrategy.

**Sau sửa:** cùng những SELECT trên PostgreSQL thật đều OK, không còn 42P18. Tổng Company 13: Thu 9,363,000.00; Chi 0; dòng tiền 9,363,000.00 cho cả 01–17/10/2026 và riêng ngày 17/10/2026. dailyTotals trả ngày 2026-10-17 với đúng số tiền đó.

## Trạng thái runtime / HTTP
- PostgreSQL 18.6 chạy cổng 5432; identity đã xác minh: invoice_db / public / ::1:5432. TimeZone server: Asia/Bangkok.
- Không có listener backend 8081 lúc kiểm tra. Frontend .env dùng http://localhost:8081. Không tự khởi động/dừng backend hay tiến trình Java khác.
- GET thực tế tới /api/v1/dashboard/financial, /financial/time-series và /financial-transactions đều ECONNREFUSED, status=null, không có response HTTP. Không ghi chúng thành HTTP 500.
- Không có terminal backend gắn với chat; log PostgreSQL hiện tại 17/10 là file rỗng. Không thu được stack trace của phiên Spring Boot đã dừng. Root cause được tái hiện bằng các query Repository trên đúng PostgreSQL, không suy diễn từ ảnh lỗi UI.
- GlobalExceptionHandler.java:74–83 ánh xạ DataAccessException thành HTTP 500, error DATABASE_ERROR, message Thao tác với cơ sở dữ liệu thất bại; stack/root detail chỉ vào log. Test MockMvc xác nhận response này và không lộ SQLSTATE/secret. Đây là HTTP trong test, không phải response lấy từ runtime đã dừng.

## Truy vết frontend → PostgreSQL
| Màn hình / API | Controller → Service → Repository | Ảnh hưởng |
|---|---|---|
| Dashboard kế toán: GET /api/v1/dashboard/statistics | DashboardController.statistics → DashboardService.statistics | KPI chứng từ, không phải query tiền gây 42P18 |
| Cả ba: GET /api/v1/dashboard/financial?companyId=13&dateFrom=2026-10-01&dateTo=2026-10-17 | DashboardController.financial → DashboardService.financialScoped → summarize / expensesByCategory / countUnverified | Tổng thu/chi thất bại khi các query ngày lỗi |
| Dashboard + Báo cáo: GET /api/v1/dashboard/financial/time-series?...&interval=DAILY | DashboardController.timeSeries → DashboardService.timeSeriesScoped → dailyTotals → FinancialSeries | Biểu đồ thất bại cùng lỗi định kiểu ngày |
| Thu/Chi: GET /api/v1/financial-transactions?... | FinancialTransactionController → FinancialTransactionService.findAll → Specification transactionDate/company | Điều kiện động không có nullable IS NULL kiểu trên; một giao dịch có thể vẫn hiện trong khi KPI lỗi |
| Dashboard: GET /api/v1/documents | DocumentController → DocumentService.findPage | Tải hàng đợi chứng từ riêng; không xác nhận response runtime do backend dừng |
| Excel: GET /api/v1/exports/financial-report | ExportController → ExcelExportService.financialReportScoped → FinancialTransactionService.findAll | Giữ cùng ngày/công ty và chỉ cộng CONFIRMED; hồi quy đã chạy |

Dashboard và Reports gom Promise.all, vì vậy một API tài chính thất bại làm hiện lỗi tổng quát toàn màn hình. Không có bằng chứng rằng các API thành phần khác đều lỗi.

## Mapping FinancialTransaction ↔ schema hiện tại
Tất cả 17 cột được kiểm tra metadata và đọc qua Entity:
| Entity | PostgreSQL | Kết quả |
|---|---|---|
| id | financial_transaction_id bigint PK/identity | Khớp |
| company | company_id bigint NOT NULL, FK companies(company_id) | Khớp |
| document | document_id bigint nullable, FK documents(document_id) | Khớp |
| invoice | invoice_id bigint nullable, FK invoice_data(invoice_id) | Khớp |
| accountingCategory | accounting_category_id bigint nullable, FK accounting_categories(category_id) | Khớp |
| createdBy | created_by bigint nullable, FK users(user_id) | Khớp |
| transactionType | transaction_type varchar(50) NOT NULL | Khớp |
| transactionDate | transaction_date date NOT NULL | Khớp LocalDate |
| amount | amount numeric(19,2) NOT NULL | Khớp |
| description | description text nullable | Khớp |
| paymentMethod | payment_method varchar(50) nullable | Khớp |
| status | status varchar(30) NOT NULL | Khớp |
| paymentConfirmation | payment_confirmation varchar(20) nullable | Khớp |
| requestKey | request_key varchar(36) nullable | Khớp |
| requestFingerprint | request_fingerprint varchar(64) nullable | Khớp |
| createdAt / updatedAt | created_at / updated_at timestamp without time zone NOT NULL | Khớp LocalDateTime hiện tại; không tự đổi kiểu |

accounting_categories.category_type varchar(20) nullable và users.token_version/row_version bigint NOT NULL cũng đã tồn tại. Không cần ADD COLUMN để sửa lỗi đang tái hiện.

## Baseline và phạm vi tính tiền
- Tổng dữ liệu tài chính hiện 33 giao dịch, đều Company 13.
- 32 legacy payment_confirmation=NULL (2 ACTIVE, 30 COMPLETED): không tính là thanh toán.
- 1 CONFIRMED / ACTIVE ngày 17/10/2026, Thu 9,363,000.00, thuộc khoảng đang lọc.
- Query kiểm tra request provenance: không có bản ghi thiếu requestKey/fingerprint theo kiểm tra NULL/độ dài/hash; không in giá trị khóa/hash, mô tả giao dịch hay tài khoản.
- Không đổi điều kiện CONFIRMED, công thức Thu/Chi, phép trừ dòng tiền hoặc scope. ADMIN/ACCOUNTANT và company resolver giữ nguyên; test chặn công ty khác còn đạt.
- Ngày dùng DATE/LocalDate với >= và <=, không qua timestamp/UTC. Asia/Bangkok và Asia/Ho_Chi_Minh đều UTC+07 ở các ngày này; query không phụ thuộc offset. Không tự sửa TimeZone database hay timestamp cũ.

## Code sửa sau khi xác minh
- FinancialTransactionRepository.java:23,26,29: đổi nhánh nullable thành cast(:dateFrom as date) is null / cast(:dateTo as date) is null. Đây là cast tham số trong SELECT, không ALTER cột và không đổi giá trị ngày.
- quan tri/src/utils/apiLoadError.ts: hiển thị HTTP status/mã lỗi an toàn, phân biệt 403 và mất kết nối; không đưa SQL/stack/raw message ra UI.
- AccountantDashboard.tsx, Reports.tsx: dùng mô tả lỗi trên; vẫn reject API lỗi, không đổi thành dữ liệu rỗng.
- FinancialTransactions.tsx: KPI báo Lỗi tải dữ liệu khi lỗi, Đang tải... lúc chờ, không dùng 0/Chưa xác định để che lỗi; danh sách và KPI phân biệt lỗi riêng.
- Test mới: apiLoadError.test.mjs, FinancialDatabaseErrorResponseTest.java; thêm bài test khoảng ngày thiếu một/hai đầu trong PaymentIntegrationTest.java; package.json bổ sung test.
- docs/diagnostics/financial_transactions_readonly.sql: chỉ SELECT để tái kiểm tra.
- Báo cáo này. Không sửa Entity, datasource, schema, migration cũ hoặc invoice_1; không commit/push.

## Schema drift khác — cần duyệt riêng, không phải nguyên nhân 42P18
Hiện có ux_financial_transactions_document và ux_financial_transactions_invoice, là unique partial index chỉ theo document_id/invoice_id. Chúng chặn nhiều phiếu cho cùng chứng từ/hóa đơn. Chưa có ux_financial_payment_request(company_id,request_key) và ck_financial_payment_provenance như thiết kế Yêu cầu 06.

File draft 20261016_02_confirmed_payments.sql cũ không thể chạy nguyên trạng: cột đã có và preflight của file từ chối hai unique index legacy. Không chạy file này để sửa lỗi tải số liệu.

Phương án migration cần phê duyệt riêng, chưa thực thi:
1. Backup/restore thử độc lập; xác minh lại schema/index definition, duplicate company_id/request_key, provenance và giao dịch đang hoạt động.
2. Chỉ sau phê duyệt nghiệp vụ nhiều thanh toán, gỡ hai unique index document/invoice đã xác minh; giữ nguyên ID/FK/dữ liệu.
3. Tạo unique partial index trên (company_id,request_key) WHERE request_key IS NOT NULL; thêm index không unique (company_id,document_id)/(company_id,invoice_id) nếu chưa có sau đối chiếu.
4. Thêm CHECK provenance theo thiết kế đã duyệt, legacy NULL/NULL/NULL vẫn được giữ, không UPDATE/backfill; kiểm tra NOT VALID/VALIDATE trong giao dịch và timeouts đã duyệt.
5. Rollback ưu tiên dừng ghi thanh toán và dùng bản tương thích, giữ dữ liệu. Không tự tạo lại unique index một-payment khi đã phát sinh nhiều phiếu; không restore đè database thật.
Không tạo một file DDL tự chạy hoặc dùng IF NOT EXISTS che drift. Migration này không cần cho hotfix SELECT hiện tại.

## Kiểm chứng
- PostgreSQL thật: standalone Hibernate + Spring Data Repository, physical naming giống Boot, hbm2ddl=none; connection options default_transaction_read_only=on; xác minh identity/read-only trước mỗi query; rollback transaction. Không Spring Boot/startup hooks, không DDL/DML.
- Các query trước lỗi đã chạy thành công sau sửa: findAll, summarize, dailyTotals, expensesByCategory, countUnverified, không ngày và ADMIN tổng hợp.
- Backend full clean test offline Java21, test JVM UTC: 433 total = 420 PASS, 0 FAIL/Error, 13 SKIP.
- Test HTTP lỗi mới xác nhận 500 DATABASE_ERROR cho hai endpoint tài chính, không trả SQLSTATE/private token. H2 integration kiểm tra ngày 01–17/10, riêng ngày, xuyên tháng/năm, nhuận, scope, legacy/hủy, Excel và nullable date filters.
- Frontend: 53 PASS, 0 FAIL/SKIP; build thành công, warning chunk lớn có sẵn. git diff --check không lỗi.
- Bằng chứng kỹ thuật nằm trong .runtime/debug-financial: jpa-before-fix.log, jpa-probe.log, postgres-evidence.txt, http-evidence.json, backend-test.log, frontend-test.log, build.log. Không lưu mật khẩu vào log.

## Chưa giải quyết / điều kiện runtime
- Backend thật vẫn dừng, nên chưa xác nhận browser E2E hoặc HTTP authenticated của ba màn hình sau hotfix. Không tuyên bố giao diện runtime đã hoạt động.
- Cần khởi động lại backend bằng bản build mới, cấu hình ddl-auto=validate hiện tại và các guard startup đang được duyệt; sau đó dùng phiên đăng nhập hợp lệ để kiểm tra cả hai khoảng ngày. Không tự chạy migration khi khởi động.
- Schema/index hardening của thanh toán nhiều lần cần phê duyệt riêng như trên; không dùng giao dịch legacy làm tiền đã xác nhận.
