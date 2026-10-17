# Yêu cầu 05 — Chuẩn bị code category_type nullable và sửa lỗi tải danh mục

Ngày hoàn tất kiểm thử: 2026-10-16. Branch dev/ai. Phạm vi theo xác nhận mới: triển khai code/test, chưa được chạy migration trên invoice_db.

## Kết quả chính

- Frontend không còn biến lỗi tải danh mục thành danh sách rỗng/0; phân biệt loading, 401, 403, lỗi API/network và 200 với [] thật.
- Enum/mapping mới AccountingCategoryType = INCOME/EXPENSE; Java null và PostgreSQL NULL được giữ cho dữ liệu cũ. Không suy từ SALE/PURCHASE hoặc tên danh mục.
- Controller chuyển logic sang service kiểm tra quyền/Company. Đọc mặc định active, thêm tham số includeInactive và type tùy chọn.
- Có API ADMIN tạo/sửa/ngừng sử dụng. Danh mục mới phải được chọn Thu/Chi rõ ràng. Danh mục cũ NULL sửa tên/mô tả/trạng thái mà vẫn NULL nếu không chọn type.
- Không có luồng nào tạo FinancialTransaction từ category_type. FinancialTransactionService, workflow, AI/OCR và báo cáo không sửa trong lượt này.
- Chỉ chuẩn bị source và build/test. Không triển khai runtime, không migration, không gửi DDL/DML vào PostgreSQL thật.

## Hiện trạng và nguyên nhân đã xác minh

Khảo sát PostgreSQL read-only trước đó ghi nhận invoice_db/public, PostgreSQL 18.6, Company 13 có 4 danh mục active; Company 1/2/3 có 0. Chưa có category_type trong schema thật tại thời điểm khảo sát. Không thay đổi snapshot dữ liệu này trong lượt triển khai.

CatalogManagement từng catch(() => undefined) khi GET categories lỗi; state khởi tạo [] nên lỗi bị hiển thị 0. Trang FinancialTransactions có cùng cách nuốt lỗi và cũng đã sửa phần đọc danh mục. Chưa có request HTTP thật của lần lỗi ban đầu để kết luận chính xác 401/403/500 hay lỗi mạng. Test H2 xác minh backend mới phân biệt được những trường hợp này; không coi đó là kiểm chứng runtime invoice_db.

## Quy tắc API / scope / dữ liệu cũ

| API | Quyền | Hành vi |
|---|---|---|
| GET /api/v1/accounting-categories | ACCOUNTING_VIEW hoặc ADMIN hiện hành | Mặc định Company của user và active=true. Không tự trả dữ liệu tất cả Company |
| GET với companyId | ADMIN được chọn Company; người có ACCOUNTING_VIEW chỉ đúng Company hiện tại | Vượt scope trả 403; Company không tồn tại trả 404; chưa chọn/không có Company trả COMPANY_REQUIRED/400 |
| GET includeInactive=true | Vẫn kiểm tra quyền đọc và Company | Trả cả active/inactive, giữ danh mục lịch sử |
| GET type=INCOME/EXPENSE | Vẫn kiểm tra scope | Chỉ lọc theo type đã được lưu, không suy diễn; bỏ tham số để giữ NULL trong danh sách |
| POST | ADMIN có role active | Tạo danh mục vào Company đã tồn tại; type bắt buộc; không tạo Company/giao dịch |
| PUT /{id} | ADMIN có role active | Sửa/ngừng dùng/kích hoạt; không chuyển Company; không DELETE |

Request: categoryCode, categoryName, description, categoryType nullable khi sửa, active, companyId. Enum ngoài INCOME/EXPENSE bị 400 với mã INVALID_ACCOUNTING_CATEGORY. Body/query sai định dạng được handler cục bộ của Controller xử lý, không đổi xử lý authentication chung.

Response giữ id/categoryCode/categoryName/description và bổ sung companyId/categoryType/active. categoryType=null được trả nguyên giá trị. Frontend hiển thị Chưa xác định. Client tương thích response active-only cũ bằng cách coi active bị thiếu là true (vì API cũ vốn chỉ trả active), categoryType thiếu thành null; không suy type từ tên/mã. includeInactive không được giả lập khi backend cũ chưa hỗ trợ.

Type null hoặc bị bỏ trong request cập nhật có nghĩa giữ type hiện tại: danh mục NULL vẫn NULL, danh mục đã có type không bị xóa type do client cũ bỏ field. Không có API xóa type đã xác định ở bước này. Tạo mới thiếu type trả CATEGORY_TYPE_REQUIRED/400.

Validation mã/tên/mô tả theo độ dài schema: 50/200/255. Mã chuẩn hóa uppercase/trim ở service, không cho ký tự ngoài chữ/số/gạch dưới. Mã trùng trong Company trả DUPLICATE_CATEGORY_CODE. Service khóa Company để tuần tự hóa create/update cùng Company; khóa và refresh category khi cập nhật. Không thay đổi code của danh mục đã có tham chiếu từ classification/correction/accounting entry/financial transaction.

Danh mục đã được tham chiếu có type xác định không được đổi sang type khác. NULL -> type chỉ xảy ra khi ADMIN chọn rõ; nếu không khớp transaction_type đã tham chiếu thì CATEGORY_TYPE_CONFLICT. Đây là kiểm tra lựa chọn do người dùng gửi, không phải dùng giao dịch để tự gán type. Ngừng sử dụng không xóa/sửa các FK hoặc giao dịch lịch sử.

Chưa có UNIQUE(code,Company) trong DB. Khóa Company bảo vệ các writer qua service hiện tại và đã có test concurrent create; writer ngoài ứng dụng vẫn có thể tạo trùng. Không thêm index/migration uniqueness ngoài thiết kế đã được duyệt.

## Frontend

- Giữ nguyên bảng/form Loại chứng từ; phần danh mục kế toán được tách thành AccountingCategoryPanel, dùng cùng style SmartInvoice.
- ADMIN chọn Company rõ ràng qua GET companies. Mặc định dùng Company tài khoản nếu có, không tự chọn Company 13 chỉ vì có bốn danh mục.
- Số đếm là — khi chưa tải/đang tải/lỗi/không có quyền; chỉ 200 thành công với [] mới hiển thị 0.
- Danh sách có mã/tên/Thu–Chi/mô tả/trạng thái và tạo/sửa/ngừng sử dụng/kích hoạt. Các thao tác gọi API thật; không có mock data nghiệp vụ.
- Chặn response cũ của Company trước ghi đè state Company mới sau khi đổi selector.
- Lỗi tải Company và danh mục có thông báo/retry riêng. Lỗi danh mục không bị lỗi tải document types che hoặc biến thành empty.
- Trang giao dịch không nuốt lỗi tải category, báo lỗi và disable dropdown chưa tải được. Dropdown chi tiết chứng từ/giao dịch hiển thị type hoặc Chưa xác định; không đổi loại giao dịch theo category.

## File mới

Backend dưới backendqt/backendqt/src/:
- main/java/com/example/invoice/entity/AccountingCategoryType.java
- main/java/com/example/invoice/dto/accounting/AccountingCategoryRequest.java
- main/java/com/example/invoice/service/AccountingCategoryService.java
- test/java/com/example/invoice/AccountingCategoryIntegrationTest.java

Frontend dưới quan tri/src/:
- components/AccountingCategoryPanel.tsx
- utils/accountingCategoryPresentation.ts
- utils/accountingCategoryPresentation.test.mjs

Báo cáo này: docs/REQUEST05_CATEGORY_IMPLEMENTATION.md.

## File sửa trong lượt này

Backend main/java/com/example/invoice/:
- entity/AccountingCategory.java — thêm nullable EnumType.STRING category_type, length=20; không default/backfill/parent.
- dto/accounting/AccountingCategoryResponse.java — field bổ sung, giữ constructor tương thích.
- controller/AccountingCategoryController.java — GET/POST/PUT, delegating service, RBAC, handler enum/query/body invalid.
- repository/AccountingCategoryRepository.java — scoped search, type/active filter, normalized duplicate check, row lock.
- repository/CompanyRepository.java — khóa Company khi ghi danh mục.
- repository/ClassificationRepository.java, ClassificationCorrectionRepository.java, AccountingEntryRepository.java — exists tham chiếu.
- repository/FinancialTransactionRepository.java — chỉ thêm exists tham chiếu/type conflict; không đổi query dashboard/report hoặc ghi giao dịch.

Frontend:
- quan tri/src/services/accountingCategoryService.ts — DTO nullable, API thật, validation shape mảng, CRUD/Company.
- quan tri/src/services/financialTransactionService.ts — tái sử dụng client/DTO danh mục, giữ nguyên API giao dịch.
- quan tri/src/pages/admin/CatalogManagement.tsx — bỏ catch nuốt lỗi, panel/count riêng, không đổi bảng loại chứng từ.
- quan tri/src/pages/accountant/FinancialTransactions.tsx — hiện lỗi category/retry/disabled dropdown, nhãn nullable type.
- quan tri/src/pages/accountant/AccountantDocumentDetail.tsx — nhãn type trong dropdown hiện có.
- quan tri/package.json — thêm test presentation.

Giữ các thay đổi chưa commit của Yêu cầu 02–04. Không thay invoice_1/, SecurityConfig/JWT/AuthService, schema runtime configuration hoặc migration lịch sử. SQL draft 20261016_01_accounting_category_type.sql không đổi, vẫn ở docs/migration-proposals, SHA256 6e0320b20364d1e83c5246c42146b519e0523a3d186c1257d79694b71e5d7845.

## Kiểm thử đã chạy

Backend Maven offline, Java 21, profile test H2:
- Tổng 373 test: 360 PASS, 0 FAIL, 0 ERROR, 13 SKIP; BUILD SUCCESS.
- Trong đó 10 test AccountingCategoryIntegrationTest trên H2 mem category_nullable_test riêng; login/JWT/filter/method security thật với fixture chỉ dành cho H2. Không dùng PostgreSQL thật.

Mười test mới kiểm chứng:
1. ADMIN và ACCOUNTANT đúng Company 13 nhận đủ bốn danh mục active, type NULL; filter INCOME không tự coi SALE là INCOME.
2. Không xác thực 401; EMPLOYEE không có quyền 403; ACCOUNTANT vượt Company 403; ADMIN Company rỗng 200 []; Company không tồn tại 404.
3. Legacy NULL giữ nguyên khi đổi tên/ngừng sử dụng; active filter/includeInactive đúng; không thêm FinancialTransaction.
4. Tạo mới cần type rõ; mã trùng case-insensitive trong Company bị chặn; cùng mã ở Company khác hợp lệ.
5. ACCOUNTANT không được gọi API quản lý catalog; type/body/query không hợp lệ không ghi category.
6. Category đã có giao dịch không đổi mã/type xung đột; ngừng dùng giữ nguyên FK/transaction/type NULL.
7. Chọn type rõ ràng không sửa type ba category còn lại hoặc tạo FinancialTransaction. Test cố ý chọn type theo request, không dùng ý nghĩa mã để quyết định.
8. Hai thread create cùng mã/cùng Company có đúng một winner.
9. ADMIN thiếu Company nhận COMPANY_REQUIRED; chọn Company 13 thì nhận bốn dòng, không trả [] giả.
10. Request bỏ type không xóa type đã xác định; mã sai bị từ chối, không ghi row mới.

Frontend:
- npm test: 23 PASS, 0 FAIL/SKIP.
- npm run build (tsc + Vite): PASS. Còn cảnh báo chunk lớn >500 kB.
- Test presentation xác minh lỗi HTTP/network không thành 0, trạng thái loading/idle không thành empty, legacy null/missing type không suy từ SALE/PURCHASE.
- git diff --check: PASS.

Chưa chạy kiểm thử trình duyệt với API runtime thật hoặc migration trên PostgreSQL riêng. Không dùng kết quả H2 để tuyên bố migration PostgreSQL đã được kiểm chứng. Log cục bộ: backendqt/backendqt/request05-test.log; không commit log.

## Bảo vệ runtime và điều kiện migration

Trước khi sửa/build: kiểm tra không có Java backend hoặc listener 8081. Sau hồi quy: PORT_8081_LISTENERS=0, INVOICE_BACKEND_JVMS=0. Chỉ build/test, không spring-boot:run, không package/deploy/copy artifact tới runtime, không restart tiến trình nghiệp vụ.

Entity mới cần category_type khi Hibernate validate hoặc ORM SELECT. Không được chạy bản code mới với invoice_db còn thiếu cột. Giữ ddl-auto=validate; không có compatibility fallback update/create, không tự tạo cột. Đây là code đã chuẩn bị, chưa phải bản được phép triển khai production.

Các điều kiện trước khi chạy migration thật:
1. Phê duyệt riêng việc chạy SQL draft trên invoice_db; phê duyệt thiết kế/code hiện tại không bao hàm DDL.
2. Xác minh lại identity/schema/baseline và không có drift; không dùng IF NOT EXISTS che sai khác.
3. Backup bằng pg_dump và kiểm chứng restore hoàn chỉnh trên PostgreSQL riêng; chưa làm trong bước này.
4. Thử đúng migration trên PostgreSQL 18.6 test độc lập, so sánh ID/Company/FK/count, type NULL, constraint/default/nullable; kiểm tra rollback và lock timeout. Chưa được tự tạo DB test hoặc restore nếu chưa được duyệt các thao tác đó.
5. Lập cửa sổ bảo trì/dừng writers được duyệt; áp dụng duy nhất migration này với timeout/transaction/ON_ERROR_STOP.
6. Xác minh đủ bốn legacy category vẫn NULL và các bảng tham chiếu không bị sửa. Không tự gán Thu/Chi cho bốn dòng.
7. Sau kiểm chứng schema mới mới được khởi động backend validate, rồi test API/UI trên môi trường được duyệt.

Rollback ưu tiên code cũ và giữ cột nullable/check/giá trị đã lưu. Trước commit DDL có thể rollback transaction; sau commit không tự drop cột hoặc restore đè invoice_db. Không chạy kèm migration JWT/legacy khác.

## Phần còn lại ngoài lượt nullable/scope/error này

- Chưa triển khai riêng snapshot AI đề xuất vs final selection và audit bổ sung của toàn bộ Yêu cầu 05; giữ kế hoạch trong REQUEST05_CATEGORY_TYPE_DESIGN.md. Không tuyên bố toàn bộ Yêu cầu 05 hoàn tất.
- Chưa thay quy tắc mapping tài khoản kế toán hoặc sửa group báo cáo theo ID/Company; queries hiện hành được giữ nguyên và suite regression đã chạy.
- Chưa bổ sung constraint UNIQUE DB, chưa sửa/backfill dữ liệu cũ hoặc quan hệ Company.
- Chưa xác định mã lỗi HTTP thật của lần UI hiển thị 0 ban đầu; đã sửa đường code che lỗi và có test phân biệt HTTP/scope.

CODE/TEST PREPARATION: COMPLETE FOR THIS REQUEST.
MIGRATION: NOT EXECUTED.
PRODUCTION DATABASE WRITES: NONE.
BACKEND RUNTIME: NOT STARTED/NOT DEPLOYED.
POSTGRESQL MIGRATION RUNTIME VERIFICATION: PENDING SEPARATE APPROVAL AND TEST ENVIRONMENT.
