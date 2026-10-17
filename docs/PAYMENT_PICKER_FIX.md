# Sửa chọn hóa đơn và tạo phiếu Thu/Chi — 17/10/2026

## Nguyên nhân và thay đổi

- `quan tri/src/components/PaymentLinkPicker.tsx:22`: chuyển chế độ trước đây chỉ đổi loại danh sách, không xóa lựa chọn và preview cũ. Nay xóa dữ liệu liên kết/gợi ý, giữ ngày và loại phiếu người dùng chọn; phân biệt loading, lỗi và danh sách rỗng.
- `PaymentLinkPicker.tsx:25`: danh sách hóa đơn có nhãn và ID hóa đơn riêng; chứng từ liên quan chỉ hiển thị tham chiếu.
- `quan tri/src/utils/paymentAutofill.ts:6,29`: chọn chứng từ gửi documentId; chọn hóa đơn gửi invoiceId. Không tự gửi cả hai ID từ client; backend vẫn xác minh và lưu quan hệ chuẩn của cùng một hồ sơ.
- `paymentAutofill.ts:12`: không tự đổi Thu sang Chi theo gợi ý. Picker hiển thị cảnh báo và nút chuyển loại rõ ràng; chiều xác định bằng MST không phù hợp sẽ chặn lưu.
- `paymentAutofill.ts:18` và `quan tri/src/pages/accountant/FinancialTransactions.tsx`: hiển thị lỗi từng trường, đặc biệt phương thức thanh toán và xác nhận thực thu/chi; bảo vệ dữ liệu người dùng sửa trước response muộn. Khóa form trong khi gửi.
- `paymentAutofill.ts:30`: phân biệt lỗi HTTP 400/403/409/500 và lỗi kết nối; PAYMENT_SCHEMA_REVIEW_REQUIRED giải thích ràng buộc hiện tại. Lỗi kết nối giữ requestKey để thử lại an toàn.

Không sửa backend production, API, migration hoặc database trong lượt này. Các thay đổi có sẵn được giữ nguyên.

## API tái sử dụng

- GET `/api/v1/financial-transactions/link-options/documents`
- GET `/api/v1/financial-transactions/link-options/invoices`
- GET `/api/v1/financial-transactions/payment-preview`
- API tạo/sửa phiếu hiện có trong financialTransactionService.

Company scope, RBAC, số dư, idempotency và xác nhận thanh toán tiếp tục được backend kiểm tra; frontend không tự xác nhận thanh toán.

## Bằng chứng runtime chỉ đọc

- PostgreSQL invoice_db/public, port 5432, phiên bản 18.6; các phiên kiểm tra dùng default_transaction_read_only=on.
- Truy vấn thực bằng Hibernate/Spring Data cùng điều kiện tìm kiếm: company 13 có 13 lựa chọn chứng từ và 12 hóa đơn, ID hóa đơn đầy đủ; chứng từ 218 liên kết hóa đơn 118, tổng 15.984.000.
- SELECT cuối lượt: hồ sơ 218/118 đã có 1 giao dịch, payment_confirmation=CONFIRMED, tổng đã xác nhận 15.984.000; không có giao dịch legacy trên hồ sơ này. Do đó không còn số dư để tạo phiếu mới. Đây là dữ liệu được quan sát, không phải phiếu do kiểm thử tạo.
- UNIQUE(document_id) và UNIQUE(invoice_id) dạng partial index vẫn tồn tại; PaymentSchemaGuard nhận diện cả hai. Thanh toán bổ sung trên hồ sơ đã có giao dịch có thể bị chặn với PAYMENT_SCHEMA_REVIEW_REQUIRED. Không gỡ index; cần phê duyệt migration riêng.
- GET trực tiếp không có phiên đăng nhập tới invoice-options và preview trả HTTP 401. Chưa kiểm chứng HTTP có xác thực trên backend thật; không thử mật khẩu thật.

## File thay đổi trong lượt

1. `quan tri/src/components/PaymentLinkPicker.tsx`
2. `quan tri/src/utils/paymentAutofill.ts`
3. `quan tri/src/pages/accountant/FinancialTransactions.tsx`
4. `quan tri/src/utils/paymentAutofill.test.mjs`
5. `backendqt/backendqt/src/test/java/com/example/invoice/PaymentIntegrationTest.java`
6. Báo cáo này.

## Kiểm thử

- Backend clean test: 438 tổng, **425 PASS, 0 FAIL/Error, 13 SKIP**; H2/mock, JVM UTC. Thêm integration test lựa chọn hóa đơn và tạo cả INCOME/EXPENSE, liên kết chính đúng, phiếu thủ công không mang ID; hồi quy quyền, số dư, requestKey và concurrency hiện có đạt.
- Frontend: **62 PASS, 0 FAIL**; production build thành công. Còn cảnh báo bundle lớn hơn 500 kB.
- git diff --check đạt. Không commit/push; không thay invoice_1.
- Browser test dùng component FinancialTransactions thật với Axios adapter cô lập toàn bộ API: chọn invoice 118 chỉ gửi invoiceId; chọn document chỉ gửi documentId; giữ ngày; đổi chế độ xóa dữ liệu cũ; Thu không tự chuyển Chi; thiếu phương thức/xác nhận chặn nút; tạo Chi liên kết và Thu thủ công nhận 201 mô phỏng; 409 schema hiển thị rõ và giữ form. Không có HTTP ghi thật hoặc giao dịch thật được tạo.
- Fixture browser cố ý dùng hồ sơ chưa thanh toán để kiểm thử thao tác; không đại diện số dư thực tế của hồ sơ 218/118 đã thanh toán đủ.

## Bằng chứng lưu cục bộ

Các artifact bị Git ignore trong `.runtime/payment-picker/`: backend-test.log, frontend-test.log, build.log, postgres-options.log, selected-link-counts.txt, http-without-session.json, browser-test-evidence.json, invoice-ready.png, rejected-409.png. Git baseline: `.runtime/payment-picker-git-before.txt`; Git sau: `.runtime/payment-picker/git-after.txt`.

## Giới hạn và bước còn bị chặn

Không tuyên bố tạo phiếu thành công trên PostgreSQL thật. Cần kiểm thử phiên có quyền trên môi trường độc lập và phê duyệt migration thanh toán nhiều lần trước khi áp dụng; không được tạo thêm phiếu vượt số dư hồ sơ 218/118. Backend đang chạy không bị khởi động lại; Vite test 5179 đã dừng và harness tạm đã xóa. Không thay đổi PostgreSQL, dữ liệu cũ hoặc ràng buộc.
