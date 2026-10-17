# Phân biệt loại hóa đơn và chiều Thu/Chi — 17/10/2026

## Kết luận hồ sơ 218 / hóa đơn 118

SELECT chỉ đọc trên invoice_db/public, PostgreSQL 18.6, transaction_read_only=on xác nhận:

- company_id=13, document_type=VAT_INVOICE, review_status=APPROVED.
- MST công ty, bên bán, bên mua đều có; MST công ty KHÔNG khớp bên bán, khớp bên mua.
- transaction_assessment_type=EXPENSE; nhãn AI VAT_INVOICE; danh mục AI liên kết là Mua hàng, category_type=NULL.

Theo dữ liệu hóa đơn đã duyệt, gợi ý Phiếu Chi là đúng. Không có bằng chứng để đổi hồ sơ này sang Thu. Không xuất MST đầy đủ hoặc dữ liệu tài khoản. Bằng chứng SELECT: `.runtime/payment-direction/identity.txt`. Kết luận phụ thuộc độ đúng của dữ liệu được người dùng duyệt; không phải xác minh hóa đơn với cơ quan thuế.

## Luồng và nguyên nhân

`FinancialTransactionController.java:35–36` kiểm tra RBAC và chuyển payment-preview sang PaymentLinkService; Controller không ánh xạ VAT thành EXPENSE.

Trước sửa, `PaymentLinkService.direction` so khớp MST trước, nhưng nếu không xác định được thì fallback sang transactionAssessmentType của chứng từ. `search` còn trả thẳng đề xuất AI, khác logic preview. Không tìm thấy phép ánh xạ VAT_INVOICE mặc định EXPENSE trong luồng này.

`preview` lấy danh mục từ ai_classifications mới nhất và cho phép category_type=NULL; frontend cũng chấp nhận danh mục gợi ý không có loại. Vì vậy danh mục Mua hàng chưa xác định Thu/Chi vẫn tự điền. Đây là vấn đề đã xác minh, khác với gợi ý Chi đúng theo MST trên hồ sơ 218.

## Quy tắc sau sửa

- Loại VAT_INVOICE/SALES_INVOICE không quyết định chiều giao dịch; không dùng đề xuất AI làm fallback chiều.
- Dùng Company của hồ sơ đã kiểm tra company scope; chỉ dùng MST hóa đơn khi chứng từ APPROVED. Hóa đơn chuẩn lưu tại invoice_data; không thay bằng kết quả OCR/AI thô.
- Cả ba MST phải có định dạng 10 hoặc 13 chữ số sau chuẩn hóa dấu cách/dấu gạch. Công ty khớp riêng bên bán → INCOME; riêng bên mua → EXPENSE.
- Thiếu/không hợp lệ/không khớp/khớp đồng thời hai bên hoặc hồ sơ chưa duyệt → UNKNOWN, giao diện yêu cầu kế toán kiểm tra và xác nhận chiều. Kiểm tra này không phải kiểm chứng checksum pháp lý của MST.
- Search và preview dùng chung direction và bộ lọc danh mục.
- Chỉ đề xuất danh mục active, cùng công ty, category_type khác NULL và khớp chiều đã xác minh. Không suy đoán theo tên/mã; danh mục vẫn là gợi ý để kế toán kiểm tra nghiệp vụ thực tế.
- Frontend giữ loại phiếu đã chọn; không tự đổi Thu/Chi. Danh mục NULL không tự điền, nhưng dữ liệu cũ và lựa chọn thủ công hợp lệ vẫn được giữ tương thích.
- Cơ chế backend hiện có tại FinancialTransactionService.validateLimit tiếp tục từ chối chiều ngược bằng PAYMENT_DIRECTION_MISMATCH; frontend chặn theo cùng nguồn INVOICE_COMPANY_TAX_CODE. Khi UNKNOWN, kế toán tự chọn loại phiếu và xác nhận khoản thực thu/chi qua form hiện có; không tự xác nhận.

## File thay đổi

1. `backendqt/backendqt/src/main/java/com/example/invoice/service/PaymentLinkService.java`: direction, tax, suggestedCategory, search, preview.
2. `quan tri/src/utils/paymentAutofill.ts`: siết điều kiện tự điền danh mục.
3. `quan tri/src/components/PaymentLinkPicker.tsx`: thông báo chiều chưa xác định.
4. `backendqt/backendqt/src/test/java/com/example/invoice/service/PaymentDirectionTest.java`: unit test VAT/bán hàng đầu vào và đầu ra; thiếu, sai, không khớp, mâu thuẫn MST; hồ sơ chưa duyệt và mã chi nhánh.
5. `backendqt/backendqt/src/test/java/com/example/invoice/PaymentIntegrationTest.java`: hồi quy preview/search và danh mục NULL, ngược chiều, đúng chiều; giữ test từ chối chiều sai khi lưu.
6. `quan tri/src/utils/paymentAutofill.test.mjs`: không tự điền danh mục chưa rõ và giữ chiều người dùng chọn.
7. Báo cáo này.

Không sửa Controller, DTO, Entity, migration hoặc FinancialTransactionService; không thay đổi dữ liệu PostgreSQL, không commit/push, không thay invoice_1. Không khởi động lại backend thật để áp dụng thay đổi. Không tạo phiếu thật.

## Kiểm thử và giới hạn

Backend clean test: **443 tổng, 430 PASS, 0 FAIL/Error, 13 SKIP; BUILD SUCCESS**. Frontend: **63 PASS, 0 FAIL**, production build thành công (còn cảnh báo chunk trên 500 kB). `git diff --check` đạt. Git trước/sau lưu tại `.runtime/payment-direction-git-before.txt` và `.runtime/payment-direction/git-after.txt`.

Kết quả test và build được lưu ở `.runtime/payment-direction/`. Các test backend sử dụng H2/mock; frontend test helper và build, chưa kiểm thử browser có xác thực trên runtime thật cho bản sửa này. Bản đang chạy phải được triển khai lại có kiểm soát để nhận code mới; không tự khởi động ở lượt này. Không cần migration cho thay đổi này. Ràng buộc UNIQUE thanh toán nhiều lần và số dư hồ sơ đã thanh toán đủ vẫn giữ nguyên.
