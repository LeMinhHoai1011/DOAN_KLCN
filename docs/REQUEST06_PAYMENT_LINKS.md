# Yêu cầu 06 — Liên kết phiếu Thu/Chi và thanh toán V1

Ngày: 2026-10-16. Branch dev/ai. CODE/TEST PREPARATION ONLY; chưa triển khai backend, chưa chạy migration trên invoice_db.

## 1. Phạm vi được chốt

Người dùng đã xác nhận: một hồ sơ có nhiều giao dịch, có thanh toán từng phần/nhiều lần; một giao dịch chỉ gắn tối đa một hồ sơ trong V1; không trả thừa; chống gửi lặp; kiểm tra quyền và Company. Dừng tạo giao dịch khi APPROVE. Giữ nguyên mọi giao dịch cũ, chưa xác minh không được tính tiền thực thu/chi; chuẩn bị migration và test độc lập, không thực thi DDL/DML database thật.

## 2. Quan hệ thực tế đã khảo sát

Entity Invoice.java ánh xạ bảng public.invoice_data (không có bảng public.invoices). InvoiceData có invoice_id và document_id, quan hệ một hóa đơn/một Document. FinancialTransaction có FK company_id, document_id, invoice_id, accounting_category_id và created_by; không có bảng allocation nhiều hóa đơn.

PostgreSQL kiểm tra bằng SELECT, transaction_read_only=on: invoice_db/public, server 18.6, localhost:5432. Không đọc password hash, JWT secret hoặc nội dung chứng từ.

Baseline:
- 32 financial_transactions: 30 COMPLETED/EXPENSE, 2 ACTIVE/INCOME.
- Cả 32 có invoice_id; 30 có cả document_id/invoice_id, hai dòng chỉ invoice_id.
- Không có cặp invoice/document sai, không có cross-company ở snapshot, không có nhóm trùng hồ sơ chuẩn hóa; số tiền không dương: 0.
- audit_logs chỉ có một event SEED_DEMO_DATA/COMPANY; không có nguồn gốc thanh toán tin cậy cho 32 dòng.
- DB chỉ có index PK financial_transactions. Script lịch sử 20261006_03_financial_transaction_idempotency.sql đề xuất unique document_id/invoice_id nhưng các index này chưa tồn tại trong DB được khảo sát. Không coi script là schema thực tế; không tự chạy script đó.
- API cũ cấm gửi đồng thời hai ID, dù dữ liệu thật có 30 cặp hợp lệ. CreateFromApprovedDocument từng sao chép tổng Invoice thành giao dịch ACTIVE; không có xác nhận khoản tiền, request key, lịch sử sửa/hủy; DELETE cũ xóa vật lý.

Một SELECT ban đầu dùng sai tên invoices đã bị PostgreSQL từ chối; sau khi đọc Entity/FK đã đối chiếu lại đúng invoice_data. Cả hai lần đều là SELECT read-only, không có ghi dữ liệu.

Danh sách 32 ID/liên kết cần đối soát thủ công: REQUEST06_LEGACY_MANUAL_REVIEW.md. Không kết luận dòng cụ thể do seed, do approve, hoặc tiền đã thực thu/chi nếu không có chứng cứ.

## 3. Quy tắc V1 đã triển khai trong source

### Liên kết

- Tra cứu theo tên chứng từ/số hóa đơn, phân trang 20 dòng, scope Company phía backend.
- Hồ sơ dùng cho thanh toán mới phải đã APPROVED và xử lý hoàn tất (COMPLETED/PROCESSED), có tổng tiền dương hợp lệ. Thiếu tổng tiền thì từ chối liên kết; không lấy tiền phiếu để tự điền tổng hồ sơ.
- Invoice được nạp bằng ID và Company. Nếu request gửi cả documentId/invoiceId, hai ID phải cùng một hồ sơ; sai cặp trả INVOICE_DOCUMENT_MISMATCH.
- Gửi documentId có InvoiceData thì server chuẩn hóa về cùng cặp ID thật. Gửi invoiceId thì lấy Document tương ứng. Lưu hai FK cùng hồ sơ không có nghĩa một phiếu được phân bổ sang hai hồ sơ.
- Tổng liên kết truy vấn OR theo hai FK và chỉ cộng mỗi row một lần, bao gồm legacy chỉ invoice ID. Không cộng hai aggregate riêng để gây double-count.
- Cho phép phiếu không liên kết hồ sơ; vẫn cần Company, quyền, số tiền dương, phương thức và xác nhận tiền. Phiếu không liên kết không có trần Invoice để so sánh.
- Document không có InvoiceData có thể lấy totalAmount/currency từ extracted_fields, ưu tiên giá trị correction đã có; không sửa dữ liệu OCR/AI. Nếu không có số tham chiếu hợp lệ thì chỉ có thể ghi phiếu độc lập, không tự bịa số tiền.

### Xác nhận / trạng thái / tổng tiền

- Ba cột mới nullable: payment_confirmation, request_key, request_fingerprint. NULL nghĩa legacy chưa xác minh; không backfill.
- Chỉ source API tạo phiếu đã có paymentConfirmed=true và UUID requestKey hợp lệ mới ghi CONFIRMED. ACTIVE/COMPLETED của nghiệp vụ cũ không phải xác nhận tiền.
- CONFIRMED được cộng; CANCELLED và NULL không được cộng. APPROVE chỉ ghi quyết định duyệt và history, không tạo FinancialTransaction. Boundary cũ createFromApprovedDocument giữ chữ ký nhưng trả Optional.empty để không có đường nội bộ tự ghi tiền.
- confirmedPaid là tổng các phiếu CONFIRMED; remaining = total - confirmedPaid, tính bằng BigDecimal phía server.
- Hồ sơ không có legacy chưa xác minh: 0 -> UNPAID; giữa 0 và total -> PARTIALLY_PAID; bằng total -> PAID. Không lưu trạng thái thanh toán vào Document.reviewStatus hoặc classification.
- Nếu có legacy chưa xác minh: paymentStatus=null (chưa kết luận đầy đủ); verifiedPaymentStatus vẫn mô tả riêng phần đã xác nhận. UI cảnh báo số bản ghi chưa xác minh và số còn lại chỉ theo tiền đã xác nhận. Không tự nói hồ sơ cũ đã/chưa trả đủ.
- Tổng tiền phiếu mới/cập nhật không được khiến tổng CONFIRMED vượt Invoice/Document. Trả PAYMENT_EXCEEDS_TOTAL/409, không ghi hoặc cập nhật một phần.
- V1 không trộn Thu và Chi trên cùng hồ sơ đã có thanh toán xác nhận; không xử lý hoàn tiền/trả thừa/số âm/phân bổ nhiều hóa đơn/ngoại tệ. Dùng VND như sổ quỹ hiện tại; currency khác VND có chứng cứ trên hồ sơ bị từ chối, không quy đổi.
- Catalog phải active, đúng Company; nếu category_type đã có phải khớp Thu/Chi. Category NULL legacy không bị tự gán type và vẫn có thể dùng khi hợp lệ; type catalog không tạo tiền hoặc đổi hướng giao dịch tự động.

### Chống trùng / đồng thời / sửa / hủy

- Client giữ UUID requestKey cho một ý định tạo, kể cả retry do lỗi mạng. Server unique theo Company + key; fingerprint SHA-256 của payload chuẩn hóa và kiểm tra actor.
- Cùng key/cùng payload trả lại ID cũ, không tạo audit CREATE lần nữa. Cùng key/nội dung hoặc actor khác -> IDEMPOTENCY_KEY_REUSED/409.
- Fingerprint gốc không đổi sau sửa/hủy: retry request ban đầu vẫn trả cùng giao dịch hiện tại, kể cả đã CANCELLED; không tạo lại khoản tiền đã hủy.
- Mọi ghi phiếu trong cùng Company được tuần tự hóa bằng pessimistic Company lock, bao gồm tính tổng/cap/audit/commit. Cơ chế khóa nằm ở DB, không chỉ trong một JVM. Test H2 hai thread; PostgreSQL/multi-instance thật chưa được kiểm chứng.
- Update/hủy cần expectedUpdatedAt từ response; server refresh dưới khóa và từ chối dữ liệu cũ bằng PAYMENT_STALE/409. Thời gian lấy lại sau flush/refresh để khớp precision DB.
- Sửa giữ ID và ghi audit old/new; có thể đổi hồ sơ trong cùng Company nếu tất cả validation/cap đều hợp lệ. Không đổi Company của phiếu.
- Hủy có lý do, đổi phiếu mới sang CANCELLED và giữ row/FK/history. Hủy lặp trả lại trạng thái đã hủy, không thêm event trùng. DELETE cũ trả USE_CANCELLATION_ENDPOINT/400 thay vì xóa vật lý.
- Legacy NULL là read-only qua V1, không tự cập nhật/hủy/xác nhận lại. Cần quy trình đối soát được duyệt riêng.
- Quyền ACCOUNTING_CREATE/UPDATE/DELETE/VIEW hoặc ADMIN active được kiểm tra ở controller và service. Không có quyền -> 403; dữ liệu khác Company không được liên kết. Dashboard Company scope cũng không coi assignment ADMIN inactive là đặc quyền.

## 4. API bổ sung và tương thích

Dưới /api/v1/financial-transactions:
- GET /payment-methods: CASH, BANK_TRANSFER, CARD, OTHER; UI hiển thị nhãn tiếng Việt.
- GET /link-options/documents hoặc /link-options/invoices: search/companyId/documentId/page.
- GET /payment-summary: documentId/invoiceId/companyId, chỉ đọc.
- POST /: thêm requestKey/paymentConfirmed; cho phép hai FK nếu cùng hồ sơ. Tiền có thể gửi dạng chuỗi decimal để không làm tròn khi nhập ở JS.
- PUT /{id}: thêm expectedUpdatedAt và xác nhận tiền; validation/cap đầy đủ.
- POST /{id}/cancel: reason + expectedUpdatedAt.
- GET /{id}/history: DTO actor ID/thời gian/action/old/new, không serialize User Entity.
- GET /unverified: danh sách theo Company để đối soát thủ công, không ghi dữ liệu.
- POST /payment-reconciliation: chạy rõ ràng theo quyền UPDATE; GET /payment-reconciliation/history chỉ đọc.

Dashboard /financial và export /financial-report nhận Company tùy chọn, kiểm tra scope. Client cash book dùng đúng Company của hồ sơ được chọn; không export nhầm Company mặc định.

Đây là thay đổi API có chủ đích: client tạo phiếu cũ thiếu xác nhận/key bị từ chối; DELETE không còn xóa; cần release backend/frontend đồng bộ sau migration. Backend mới cần cột mới dưới ddl-auto=validate nên không được khởi động trên schema hiện tại thiếu cột. SQL không tự chạy vì nằm ngoài resources runtime.

## 5. Đối soát và lịch sử

Tái sử dụng AuditLog/audit_logs, thêm repository; không tạo bảng history mới. Create/update/cancel và audit cùng transaction. PaymentReconciliationService tách phần thanh toán khỏi đối soát số học Invoice hiện có; ReconciliationService.runPayments là boundary dùng lại.

POST đối soát khóa Company, tính số tiền/tổng thiếu-đủ-vượt, lưu snapshot summary và ID/amount/verification/updatedAt các transaction tham gia cùng actor/thời gian trong audit_logs (PAYMENT_RECONCILIATION). GET không chạy lại hoặc ghi status. Snapshot cũ không bị sửa khi phát sinh phiếu mới/hủy/sửa; có thể xem lại snapshot trong giao diện chi tiết.

Sổ quỹ/dashboard/time-series chỉ tính CONFIRMED. Legacy và CANCELLED vẫn đọc được. Xuất Excel giữ sheet tiền đã xác nhận và sheet Khong tinh thanh toan cho giá trị legacy/hủy, không làm chúng biến mất hoặc cộng vào tiền đã trả. Chi chưa phân nhóm vẫn có nhóm null qua LEFT JOIN để tổng nhóm không bỏ sót khoản tiền.

## 6. Frontend

Giữ modal và style SmartInvoice. Thu/Chi dùng chung PaymentLinkPicker, PaymentSummaryCard và các form hiện có, giữ loại và màu sắc người dùng chọn. Không tự lấy AI gợi ý loại giao dịch làm quyết định.

- Thay input số ID bằng tìm/chọn hồ sơ đúng doanh nghiệp, có phân trang; có chọn Invoice hoặc Document, tự gắn cặp ID cùng hồ sơ.
- Điền mô tả và gợi ý category active nếu có; không tự điền amount bằng tổng Invoice. Người dùng nhập số thực thu/chi và tick xác nhận.
- Methods lấy từ backend, không nhập tùy ý. Catalog lấy API hiện có; lỗi không bị coi danh sách rỗng.
- Hiện tổng/đã xác nhận/còn lại/cảnh báo legacy và vượt trần. Chỉnh phiếu chỉ cộng lại phần của chính phiếu hiện tại khi cảnh báo capacity.
- UUID giữ khi retry; form lỗi không tự sinh key mới. Modal không đóng khi submit đang chạy.
- Nút hủy có reason; legacy/cancelled không có nút sửa/hủy hoạt động. Lịch sử có modal riêng, legacy chưa có audit thì nói rõ, không bịa lịch sử.
- Chi tiết Document có card thanh toán/đối soát và link mở phiếu Thu/Chi có preset ID; ADMIN có route cash book trong AdminLayout, không đổi sidebar.
- Sau ghi/sửa/hủy, làm mới list và KPI; lỗi KPI hiển thị chưa xác định thay vì 0 giả.
- Có filter danh sách chưa xác minh; khi dùng filter này không giả vờ áp dụng các lọc loại/ngày/category của dòng tiền.

## 7. File mới và file sửa của lượt này

Backend mới, dưới backendqt/backendqt/src/main/java/com/example/invoice:
- dto/accounting/PaymentSummary.java, PaymentLinkOption.java, PaymentHistoryEvent.java.
- repository/AuditLogRepository.java.
- service/PaymentLinkService.java, PaymentReconciliationService.java.
Test mới: src/test/java/com/example/invoice/PaymentIntegrationTest.java.

Backend sửa:
- entity/FinancialTransaction.java: ba cột nullable, không sửa FK hiện có.
- dto/accounting/FinancialTransactionRequest.java, FinancialTransactionResponse.java.
- repository/FinancialTransactionRepository.java: key lookup, canonical link query, confirmed aggregates, count unverified, LEFT JOIN nhóm chưa phân loại.
- repository/InvoiceRepository.java: SpecificationExecutor để hỗ trợ truy vấn khi cần.
- service/FinancialTransactionService.java: create/update/cancel/list/history/provenance/validation/idempotency; no-op automatic payment boundary.
- controller/FinancialTransactionController.java: lookup/summary/methods/reconciliation/history/cancel và response lỗi an toàn.
- service/ReviewWorkflowService.java: bỏ lời gọi tạo tiền khi approve.
- service/ReconciliationService.java: runPayments delegate, không sửa thuật toán OCR/AI hay đối soát số học.
- service/DocumentBusinessStateService.java: payment projection từ khoản đã xác nhận, giữ unknown legacy.
- dto/FinancialDashboardResponse.java, service/DashboardService.java, controller/DashboardController.java: confirmed totals, legacy count, Company scope/role active.
- service/ExcelExportService.java, controller/ExportController.java: Company scope, cash/excluded sheets.
- Test regression sửa FinancialTransactionServiceTest, ReviewWorkflowServiceTest, WorkflowConcurrencyIntegrationTest để chứng minh APPROVE không tạo tiền.

Frontend mới dưới quan tri/src:
- components/PaymentLinkPicker.tsx, PaymentSummaryCard.tsx, PaymentHistoryModal.tsx, DocumentPaymentPanel.tsx.
- utils/paymentPresentation.ts, paymentPresentation.test.mjs.

Frontend sửa:
- services/financialTransactionService.ts, dashboardService.ts, exportService.ts.
- pages/accountant/FinancialTransactions.tsx, AccountantDocumentDetail.tsx, Reports.tsx.
- App.tsx: route cash book ADMIN theo quyền hiện hành; package.json: test mới.

Tài liệu mới: báo cáo này, REQUEST06_LEGACY_MANUAL_REVIEW.md và migration-proposals/20261016_02_confirmed_payments.sql. Giữ nguyên mọi thay đổi chưa commit Yêu cầu 02–05 và invoice_1/. Không commit/push/reset/rebase.

## 8. Migration / backup / rollback

File SQL mới là DRAFT, chưa thực thi, ngoài src/main/resources/db/migration:
- BEGIN, timeout lock 5s/statement 30s.
- Preflight từ chối nếu có unique một document hoặc invoice, vì không khớp baseline đã khảo sát. Không DROP index không rõ nguồn gốc, không sửa migration lịch sử.
- ADD ba VARCHAR nullable: payment_confirmation(20), request_key(36), request_fingerprint(64); không default/backfill.
- CHECK legacy ba cột cùng NULL; hoặc CONFIRMED/CANCELLED có key/hash/actor/method/amount/type/status hợp lệ.
- Unique Company+request_key khi key khác NULL; index Company+document/invoice hỗ trợ nhiều lần.
- Không UPDATE/INSERT/DELETE/TRUNCATE/DROP, không tự gán nguồn gốc hoặc xác nhận cho 32 dòng. Không thay active/Company/category/invoice/document dữ liệu cũ.

Không chạy lại toàn bộ migrations. Migration category_type của Yêu cầu 05 là prerequisite code/schema riêng, vẫn chưa được phép thực thi. Trước production: backup pg_dump custom + checksum/list và kiểm chứng restore đầy đủ trên PostgreSQL riêng; áp dụng đúng các draft đã duyệt trên bản test; đối chiếu 32 IDs/FKs/amount/status, ba cột mới NULL và constraints; chạy concurrent payment/idempotency tests PostgreSQL thật. Chưa thực hiện backup/restore hoặc tạo DB ở lượt này.

Khi DDL thực sự được duyệt: dừng writers theo lịch được duyệt, kiểm tra identity/schema/baseline lần nữa, ON_ERROR_STOP, timeout, transaction; kiểm chứng rồi mới chạy backend validate. Không dùng ddl-auto=update/create. Không tự drop cột hoặc restore đè nếu lỗi.

Rollback trước COMMIT: ROLLBACK. Sau COMMIT: dừng writers, giữ cột/index/data/history. Không quay mù quáng về code cũ vì nó tự tạo giao dịch khi approve, có DELETE vật lý và cộng legacy/cancelled vào tổng tiền. Cần phiên bản hotfix tương thích/read-only được review riêng; không xóa phiếu đã ghi nhận theo V1 hoặc lén tái tạo unique một-payment-per-invoice.

## 9. Kiểm thử và giới hạn

Kết quả final ngày 2026-10-16:
- Backend clean Maven test: BUILD SUCCESS; 387 test = 374 PASS, 0 FAIL, 0 ERROR, 13 SKIP. Log cục bộ backendqt/backendqt/request06-test.log.
- Trong đó PaymentIntegrationTest có 14 test tích hợp H2, bao gồm role ADMIN inactive và cash export theo Company; các test workflow/approve đã đổi kỳ vọng theo chính sách mới và đều đạt.
- Frontend: 26 PASS, 0 FAIL/SKIP; tsc + Vite production build PASS. Còn cảnh báo bundle >500 kB.
- Kiểm tra tĩnh SQL draft: PASS (ba cột nullable, không default/backfill/DML/DROP, Company+request-key unique, nằm ngoài runtime migrations). Không phải kiểm thử chạy SQL PostgreSQL.
- git diff --check: PASS; invoice_1/ và runtime resources/migration lịch sử không có diff.
- Lượt test đầu vướng bytecode báo unresolved compilation từ output IDE; clean build đã loại bỏ, các số liệu trên là lượt clean cuối.
 Không dùng H2 để tuyên bố migration PostgreSQL đã chạy hoặc production-ready. PostgreSQL thật chỉ có các SELECT metadata/ID tổng hợp, không DDL/DML, không đăng nhập bằng tài khoản thật.

Các test tích hợp dùng profile test, H2 payments_v1_test riêng và fixture giới hạn H2. Có unique request index trong H2 test; không chạy PostgreSQL draft trên H2. Login/JWT/filter/method security thật trong môi trường đó, không mock nghiệp vụ tài chính.

Các bằng chứng bắt buộc gồm: từ Invoice hoặc Document; không liên kết; 40+60/100; số lẻ 33.33+33.33+33.34; trả thừa/create/update; sai Company/cặp ID/missing Invoice; request lặp và key khác payload; hai thread tranh cap và hai thread cùng key; stale update/hủy lặp; giữ ID/history; legacy invoice-only chưa xác minh; GET không ghi; đối soát snapshot bất biến; export/cash đúng Company, role inactive không vượt scope; APPROVE không tạo phiếu.

Ngoài V1: multi-currency, refunds/trả thừa, phân bổ một phiếu nhiều Invoice, bank reconciliation/statement import, tự xác minh legacy, kỳ kế toán đã khóa, và migration PostgreSQL runtime. Request-key bảo vệ retry kỹ thuật; không thay thế mã tham chiếu ngân hàng để phát hiện hai phiếu dùng hai key khác nhau cho cùng một chuyển khoản thực tế. Khi legacy chưa được xác minh, các số còn lại chỉ dựa trên phần đã xác nhận và có cảnh báo; cần đối soát thủ công trước quyết định thanh toán ngoài thực tế.

Chưa kiểm chứng browser E2E hoặc runtime PostgreSQL/MinIO/Ollama thật. Không thay OCR/AI/MinIO, không reprocess hoặc chạy workflow trên dữ liệu thật.


SQL draft SHA256: 324ae93e88b06187795a8bf3f92c7b4e519feab6c9d036fb7ffd9ae3983af33f

V1 CODE/TEST: COMPLETE.
POSTGRESQL MIGRATION: NOT EXECUTED.
PRODUCTION DATA/LEGACY WRITES: NONE.
PRODUCTION DEPLOYMENT: NOT PERFORMED.
POSTGRESQL RUNTIME/BROWSER E2E: NOT VERIFIED.
