# Tự điền thông tin phiếu Thu/Chi

## Khảo sát và phạm vi
- Git dev/ai đã có thay đổi trước lượt này; giữ nguyên, không commit/push/reset, không sửa invoice_1.
- PaymentLinkPicker có link-options và payment-summary nhưng chưa điền amount; dữ liệu mô tả/danh mục cũ có thể giữ khi đổi hồ sơ. Summary chỉ có quyền VIEW và không báo khả năng tạo phiếu theo schema.
- FinancialTransactionService đã có khóa công ty, tính lại số dư, requestKey/fingerprint, xác nhận tiền, lưu audit, update/cancel có expectedUpdatedAt. Tái sử dụng các cơ chế này.
- Không đọc/chỉnh sửa file chứng từ, OCR/AI, JWT/RBAC hay dữ liệu lịch sử trong lượt này.

## API dùng
- GET /api/v1/financial-transactions/link-options/documents|invoices: tìm hồ sơ đã duyệt trong company scope; dùng API cũ.
- GET /api/v1/financial-transactions/payment-preview?documentId=... hoặc invoiceId=...&companyId=...: API mới chỉ đọc vì các API cũ thiếu tổng hợp quyền tạo, trạng thái hồ sơ, gợi ý chi tiết và cảnh báo UNIQUE. Không tạo giao dịch qua GET.
- editingId tùy chọn cho form sửa: kiểm tra phiếu thuộc công ty, CONFIRMED và quyền ACCOUNTING_UPDATE, chỉ trừ phiếu đó khi tính giới hạn sửa. Preview tạo mới bắt buộc ACCOUNTING_CREATE; ADMIN giữ quyền hiện hành.
- Preview trả link canonical document/invoice, tổng hồ sơ, confirmedPaid, remaining, availableAmount, gợi ý danh mục/Thu-Chi/mô tả, nguồn gợi ý và canCreate/blockedCode/userMessage.
- POST/PUT /api/v1/financial-transactions và GET danh sách: giữ API hiện tại.
- payment-methods / accounting-categories giữ API thật hiện tại. payment-summary và đối soát cũ vẫn tương thích.

## Hành vi frontend
- Chọn hồ sơ mới xóa amount/description/categoryId/paymentMethod và xác nhận cũ; thay đồng thời documentId/invoiceId canonical; tạo requestKey mới cho lựa chọn mới. Giữ transactionDate đang chọn.
- Tải preview, gợi ý số còn lại dương, danh mục phù hợp và mô tả; không gán số âm/0 thành khoản thanh toán và không lấy ngày hóa đơn thay ngày giao dịch.
- Theo dõi trường người dùng đã sửa, kể cả xóa trắng. Response chỉ áp dụng vào đúng selection và các trường chưa sửa; reload cùng selection không tự điền lại. Response của hồ sơ cũ không được áp dụng vào hồ sơ mới.
- Form sửa giữ dữ liệu phiếu hiện tại; preview chỉ cung cấp giới hạn mới, có cộng lại số của chính phiếu khi phù hợp.
- Phương thức thanh toán phải chọn; paymentConfirmed mặc định false, không nhận true từ preview. Sau tự điền phải xác nhận thực thu/chi; thay trường form yêu cầu xác nhận lại.
- Hiển thị tổng hồ sơ/đã xác nhận/còn lại, nguồn gợi ý, cảnh báo đủ tiền/vượt tiền/schema. Loading, lỗi API và không có hồ sơ tách biệt.
- Nút ghi nhận bị khóa khi chưa có preview hợp lệ cho liên kết, số tiền/ngày/phương thức/danh mục sai, chưa xác nhận, metadata/schema bị chặn hoặc đang gửi. Có khóa in-flight phía client và idempotency phía backend.
- Người dùng được sửa số tiền (thanh toán một phần), mô tả, ngày, danh mục. Đổi Thu/Chi xóa danh mục không tương thích và yêu cầu xác nhận lại.

## Nghiệp vụ / bảo mật
- Xác định mua/bán đáng tin bằng MST Company khớp bên mua hoặc bên bán duy nhất trên Invoice: bên bán → INCOME, bên mua → EXPENSE. Không dùng tên file/tên loại để suy luận.
- Nếu không đối chiếu được MST, chỉ hiển thị gợi ý transactionAssessmentType đã lưu, ghi rõ nguồn đề xuất đánh giá chứng từ; chưa có căn cứ thì người dùng chọn Thu/Chi. Không coi đánh giá AI là bằng chứng đã thanh toán.
- Khi có căn cứ MST rõ ràng, backend từ chối loại Thu/Chi trái bên mua/bán; xung đột với phiếu CONFIRMED khác vẫn bị chặn theo rule hiện hành.
- Danh mục gợi ý phải active, cùng company và tương thích hướng đề xuất nếu đã biết; category_type NULL cũ không bị suy diễn.
- Chỉ liên kết hồ sơ APPROVED và PROCESSED/COMPLETED, tổng tiền dương hợp lệ và VND. Hóa đơn phải thuộc chứng từ và company được phép.
- Số dư chỉ cộng payment_confirmation=CONFIRMED; legacy NULL và CANCELLED không được tính. APPROVE không tạo phiếu và preview không xác nhận tiền.
- Lúc lưu vẫn kiểm tra quyền, khóa công ty, tải lại liên kết, kiểm tra tổng thanh toán, loại tiền, danh mục và requestKey/fingerprint. Metadata UNIQUE được kiểm tra lại trước save, trong transaction đã khóa. Request trùng hợp lệ trả phiếu cũ trước guard thêm phiếu; không bị tạo lặp.

## UNIQUE hiện tại — phần còn chặn
SELECT metadata và PaymentSchemaGuard trên kết nối PostgreSQL read-only xác nhận:
- ux_financial_transactions_document: UNIQUE(document_id) WHERE document_id IS NOT NULL.
- ux_financial_transactions_invoice: UNIQUE(invoice_id) WHERE invoice_id IS NOT NULL.
- Guard nhận Limits(documentUnique=true, invoiceUnique=true).
Khi đã có bản ghi chiếm khóa (kể cả legacy/hủy), không hứa tạo thêm phiếu: preview canCreate=false / PAYMENT_SCHEMA_REVIEW_REQUIRED, save trả 409 trước INSERT. Metadata không đọc được → 503 PAYMENT_SCHEMA_CHECK_FAILED, không ghi.
Các bài test nhiều lần thanh toán chạy trên schema H2 độc lập hỗ trợ nhiều phiếu; chưa kích hoạt nhiều phiếu trên PostgreSQL thật.

Phương án cần phê duyệt riêng: backup và kiểm chứng restore, kiểm tra lại đúng index/constraint/provenance/duplicate requestKey; thay hai unique index bằng index tra cứu không unique, thêm unique company_id/request_key và CHECK provenance theo thiết kế được duyệt. Không ADD lại ba cột đã có. Không UPDATE/backfill/xóa giao dịch cũ. Rollback không tự re-add UNIQUE khi đã có nhiều phiếu và không restore đè database thật.
File migration draft cũ không chạy nguyên trạng trên schema hiện tại; xem DEBUG_FINANCIAL_LOADING.md. Lượt này không sửa hoặc thực thi migration.

## File thay đổi
Backend trong backendqt/backendqt:
- dto/accounting/PaymentPreview.java (mới).
- service/PaymentSchemaGuard.java (mới): chỉ đọc JDBC index metadata, guard trước ghi.
- service/PaymentLinkService.java: preview, gợi ý hướng theo bên mua/bán, category/số dư.
- service/FinancialTransactionService.java: kiểm tra UNIQUE trước create/update và hướng theo MST.
- controller/FinancialTransactionController.java: GET payment-preview.
- src/test/java/com/example/invoice/PaymentIntegrationTest.java: thêm 4 test preview/quyền/số dư/index guard; kiểm tra không tự xác nhận, lưu rồi đọc danh sách.
Frontend trong quan tri:
- src/components/PaymentLinkPicker.tsx: tải preview, trạng thái, tự điền an toàn.
- src/pages/accountant/FinancialTransactions.tsx: dirty fields, validation, chống submit song song, xác nhận.
- src/services/financialTransactionService.ts: DTO và GET preview.
- src/utils/paymentAutofill.ts, paymentAutofill.test.mjs: helper và 6 test.
- package.json thêm test. Báo cáo này.

## Kiểm thử và giới hạn
- Full backend clean test Java21/H2, JVM UTC: 437 total = 424 PASS, 0 FAIL/Error, 13 SKIP. Bao gồm preview chưa/đã một phần/đủ, sửa loại Thu/Chi theo MST, hồ sơ khác công ty, EMPLOYEE/VIEW không có CREATE, tổng không hợp lệ, guard unique, retry và concurrency sẵn có.
- Lượt test tăng cường kiểm tra POST không xác nhận bị từ chối và GET danh sách giữ ngày giao dịch 17/10 trong khi ngày hóa đơn 16/10 được chạy clean riêng sau full suite: 22 PASS, 0 FAIL/Error/SKIP.
- Một lượt incremental giữa hai lần clean gặp class bytecode có Unresolved compilation problems / missing DTO trong target. Không sửa source để né lỗi này; clean compile/test lại. Không coi lượt lỗi là PASS.
- Frontend 59 PASS, build đạt; test trường sửa/xóa trắng, response cũ, chọn hồ sơ mới, amount 100/60/0, category_type NULL, ngày VN và định dạng VNĐ. Cảnh báo chunk lớn có sẵn.
- PostgreSQL thật chỉ đọc metadata/SELECT, không tạo phiếu, không khởi động backend, không thay schema/dữ liệu. Guard metadata mới đã được thực thi riêng trên kết nối read-only để kiểm chứng nhận diện index.
- Chưa browser E2E với backend runtime và chưa tạo phiếu trên PostgreSQL thật. Không tuyên bố thanh toán nhiều lần sẵn sàng trên schema hiện tại.
- Chưa có nghiệp vụ ngoại tệ, trả thừa, hoàn tiền hoặc tự xác minh ngân hàng; vẫn yêu cầu người dùng xác nhận tiền thực thu/chi.
