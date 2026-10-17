# Yêu cầu 05 — Khảo sát và thiết kế category_type trước cổng duyệt cuối

Ngày khảo sát: 2026-10-16. Branch xác minh: dev/ai.
Trạng thái: DESIGN/DRAFT ONLY. Chưa triển khai chức năng Yêu cầu 05, chưa thay Entity hoặc code ứng dụng, chưa chạy migration.

Theo xác nhận bổ sung của người dùng: chuẩn bị SQL và kế hoạch tương thích, sau đó dừng để duyệt lần cuối. Các thay đổi chưa commit của Yêu cầu 02–04 được giữ nguyên.

## 1. Bằng chứng PostgreSQL chỉ đọc

psql 18 kết nối database invoice_db, schema public, localhost/::1, port 5432, server_version=18.6. PGOPTIONS bật default_transaction_read_only=on và statement_timeout=10000; SELECT current_setting('transaction_read_only') trả on. Mật khẩu chỉ truyền trong môi trường tiến trình con, không có trong command line hoặc đầu ra. Không đọc password hash, token, nội dung hóa đơn hoặc chứng từ.

| Bảng | Số dòng tại thời điểm SELECT |
|---|---:|
| accounting_categories | 4 |
| ai_classifications | 66 |
| accounting_entries | 30 |
| financial_transactions | 32 |
| classification_correction | 30 |

| Company ID | Tổng danh mục | Đang hoạt động |
|---|---:|---:|
| 1 | 0 | 0 |
| 2 | 0 | 0 |
| 3 | 0 | 0 |
| 13 | 4 | 4 |

| Category ID | Company | Code | Active | Classification tham chiếu | Entry tham chiếu | FinancialTransaction tham chiếu |
|---|---|---|---|---:|---:|---:|
| 9 | 13 | SALE | true | 6 | 0 | 2 |
| 10 | 13 | PURCHASE | true | 58 | 30 | 30 |
| 11 | 13 | SERVICE | true | 0 | 0 | 0 |
| 12 | 13 | OFFICE | true | 0 | 0 | 0 |

Không gán INCOME cho SALE hoặc EXPENSE cho PURCHASE từ tên/mã/tham chiếu: cả bốn category_type phải tiếp tục NULL sau migration. Hai danh mục đầu đang được tham chiếu nên tuyệt đối không xóa hoặc đổi FK lịch sử.

Kiểm tra tổng hợp bổ sung:
- Không có mã trùng khi nhóm theo company_id + upper(trim(category_code)).
- Không có category tham chiếu sai Company trong ba bảng ai_classifications, accounting_entries, financial_transactions tại snapshot này.
- Có 2 giao dịch INCOME và 30 giao dịch EXPENSE, cả 32 có FK danh mục. Đây là transaction_type hiện hành, không phải dữ liệu dùng để tự backfill category_type.
- ADMIN assignment hiện có thuộc Company 13. Vì thế không được kết luận tài khoản ADMIN thực tế thiếu Company là nguyên nhân đã xác minh.
- Không có flyway_schema_history hoặc databasechangelog. pom.xml/resources chưa cấu hình Flyway/Liquibase; một file SQL có sẵn không đồng nghĩa đã tự thực thi.

### Schema accounting_categories đã xác minh

| Cột | PostgreSQL | Nullable / đặc điểm |
|---|---|---|
| category_id | bigint | NOT NULL, IDENTITY BY DEFAULT, primary key |
| category_code | varchar(50) | NOT NULL |
| category_name | varchar(200) | NOT NULL |
| created_at | timestamp without time zone | NULL được phép |
| description | varchar(255) | NULL được phép |
| updated_at | timestamp without time zone | NULL được phép |
| company_id | bigint | NULL được phép, FK companies |
| active | boolean | NOT NULL DEFAULT true |

Chưa có category_type, chưa có parent_category_id. Index duy nhất được tìm thấy là primary key; chưa có UNIQUE(company_id, category_code). Entity AccountingCategory khớp các cột hiện hành và không có type. Không cần thêm parent category trong phạm vi này.

## 2. Vì sao nhóm chi phí hiển thị 0

Đã xác minh trực tiếp trong source:
1. CatalogManagement.tsx gọi getCategories() nhưng catch(() => undefined) nuốt mọi lỗi; state categories khởi tạo [] nên lỗi HTTP bị hiển thị như danh sách 0 dòng.
2. API AccountingCategoryController.findAll chỉ trả active và mặc định dùng Company của user. Không có bộ chọn Company trong tab quản trị hiện tại.
3. Database không rỗng: Company 13 có bốn danh mục. Company 1/2/3 trả [] theo scope là hợp lệ.
4. Controller hiện chỉ GET; frontend chỉ có bảng đọc và không có form/thao tác quản lý danh mục kế toán. DTO chỉ có id/code/name/description.

Chưa thu được request/response trình duyệt và log HTTP của lần hiển thị 0; không khởi động backend hoặc đăng nhập bằng mật khẩu thật. Vì vậy root cause của chính request lỗi vẫn PARTIAL: đã chứng minh lỗi trình bày và dữ liệu nền, chưa chứng minh đó là 401/403/500, sai URL hay lỗi kết nối. Không kết luận 0 là không có dữ liệu.

## 3. SQL migration dự thảo

File: docs/migration-proposals/20261016_01_accounting_category_type.sql.
Đặt ngoài src/main/resources/db/migration để không bị runner tương lai tự phát hiện trước phê duyệt. Số phiên bản không trùng các file đã khảo sát; migration JWT 20261015_01 là lịch sử riêng, không được tự chạy kèm hoặc suy diễn trạng thái thực thi.

SQL thực thi dự kiến có đúng sáu statement:

~~~sql
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE public.accounting_categories
    ADD COLUMN category_type VARCHAR(20);
ALTER TABLE public.accounting_categories
    ADD CONSTRAINT ck_accounting_categories_category_type
    CHECK (category_type IS NULL OR category_type IN ('INCOME', 'EXPENSE'));
COMMIT;
~~~

Không DEFAULT, không NOT NULL, không UPDATE/backfill, không IF NOT EXISTS để che drift. Không CREATE/DROP/TRUNCATE/DELETE/INSERT, không thay identity/sequence/FK/index hiện có. Column type là varchar để giữ cách lưu chuỗi dễ tương thích; Java enum nếu dùng sẽ @Enumerated(STRING), chỉ INCOME/EXPENSE, Java null đại diện dữ liệu cũ chưa xác định.

Ảnh hưởng dự kiến sau khi được phép chạy: thêm một cột nullable cho đúng bốn danh mục hiện có, tất cả giá trị NULL; thêm CHECK chấp nhận NULL/INCOME/EXPENSE. Category ID, mã, tên, Company, active, thời gian và mọi FK giữ nguyên. Không UPDATE updated_at. Không thay 66 classification, 30 accounting entry, 32 giao dịch hoặc 30 correction.

DDL yêu cầu khóa ACCESS EXCLUSIVE trên bảng. ADD COLUMN không default tránh backfill dữ liệu; CHECK được validate trên dữ liệu hiện có, không dùng NOT VALID để bỏ sót bước kiểm chứng. Vẫn có thể chờ hoặc chặn các giao dịch đọc/ghi; bốn dòng không có nghĩa chắc chắn không có lock contention. Khi vượt lock_timeout hoặc statement_timeout, dừng và ROLLBACK giao dịch; không tự tăng timeout hoặc chạy lại trên database thật.

## 4. Tương thích và thứ tự triển khai

1. Duyệt bản SQL/kế hoạch này; không đồng nghĩa duyệt chạy DDL.
2. Chuẩn bị code và test ứng dụng trên H2/PostgreSQL test độc lập theo quyền được duyệt riêng.
3. Backup custom pg_dump tương thích server và chứng minh restore trên PostgreSQL riêng; chưa thực hiện backup/restore ở bước này.
4. So sánh baseline, schema, tên constraint/cột và quyền; không tiếp tục nếu drift. Không chạy toàn bộ thư mục migration.
5. Chỉ sau phê duyệt DDL riêng: dừng writers theo lịch bảo trì được duyệt, xác minh identity database, chạy đúng file đã duyệt với ON_ERROR_STOP.
6. Kiểm chứng dữ liệu/cột/constraint, sau đó mới triển khai Entity mới với ddl-auto=validate. Không bật update/create.
7. Kiểm chứng API/UI trên môi trường phù hợp; không tự tạo seed danh mục hoặc gán type cho bốn dòng cũ.

Code cũ không ánh xạ cột mới nên tương thích với cột nullable được thêm. Code mới có mapping category_type sẽ không khởi động trên schema cũ dưới validate; tuyệt đối không triển khai Entity đó trước migration được phê duyệt và kiểm chứng. Frontend cần nhận categoryType nullable và hiển thị Chưa xác định, không biến null thành EXPENSE.

Rollback:
- Trước COMMIT: transaction DDL có thể ROLLBACK; phiên lỗi phải kết thúc đúng trạng thái, không tiếp tục câu lệnh khác.
- Sau COMMIT: ưu tiên quay về application version cũ, giữ nguyên cột/check/dữ liệu. Không cung cấp down migration DROP COLUMN.
- Nếu code mới đã có người dùng chọn type, rollback code vẫn phải giữ các giá trị đó; không UPDATE về NULL.
- Không restore đè invoice_db hoặc xóa cột để sửa lỗi nếu chưa có phê duyệt sự cố riêng.

## 5. Thay đổi ứng dụng dự kiến — chưa triển khai

### Category Entity / Repository / DTO / API

- AccountingCategory.java: categoryType enum/string nullable. Không thêm parent.
- AccountingCategoryRepository.java: tìm theo Company/active/type, kiểm tra code trùng chuẩn hóa trong Company. Tránh cho code đổi scope.
- Thêm AccountingCategoryService để tập trung scope, duplicate validation, mutation guard, tham chiếu và lỗi nghiệp vụ; không đặt toàn bộ logic vào controller.
- Category request: categoryCode, categoryName, description, categoryType, active; tạo mới phải chọn INCOME/EXPENSE. categoryType NULL giữ cho dữ liệu cũ, không tự suy diễn. Mã trim/uppercase, giới hạn theo schema 50/200/255.
- Response thêm companyId, categoryType nullable, active; giữ các field cũ để API consumer hiện tại còn tương thích.
- GET /api/v1/accounting-categories mặc định active để giữ dropdown cũ; tham số quản trị includeInactive/type/companyId phải được kiểm tra quyền.
- POST tạo; PUT /{id} sửa/ngừng sử dụng; không DELETE phá dữ liệu. Trang quản trị hiện ADMIN-only nên đề xuất giữ ADMIN quản lý catalog; ACCOUNTANT sửa lựa chọn trên chứng từ như hiện tại, không tự mở rộng quyền quản lý danh mục.
- ADMIN chọn Company rõ ràng; ACCOUNTANT chỉ Company hiện tại; không cho request đổi Company của category đã có.
- Ngừng sử dụng không xóa các tham chiếu lịch sử; danh mục inactive vẫn hiển thị trong lịch sử, không nhận assignment mới. Mã/Company không đổi khi đã được tham chiếu trong classification/correction/entry/transaction.
- Type NULL -> giá trị do người dùng chọn là hành động rõ ràng, không phải backfill tự động; kiểm tra xung đột với giao dịch đã tham chiếu. Không tự sửa các giao dịch đó.

Database chưa có unique code theo Company. Kiểm tra service kèm khóa Company có thể tuần tự hóa các API ghi cùng Company, nhưng không ngăn writer bên ngoài service. Nếu cần bảo đảm tuyệt đối tại DB, cần duyệt riêng UNIQUE index chuẩn hóa theo Company sau kiểm tra duplicate; KHÔNG lén ghép vào migration category_type đã được duyệt thiết kế. Quy tắc case-insensitive/trim và xử lý Company NULL lịch sử phải chốt trước SQL uniqueness.

### AI / final category / audit

Đã có:
- DocumentAiProcessingService lấy danh mục active đúng Company qua findByCompanyIdAndActiveTrueOrderByCategoryCodeAsc.
- AiDocumentResultValidator kiểm tra mã trong allowlist; mã không hợp lệ sinh INVALID_ACCOUNTING_CATEGORY và bỏ assignment.
- ClassificationService.correction kiểm tra category active/Company, có guard document của Yêu cầu 04.

Còn thiếu/rủi ro:
- Prompt hiện cho phép accountingAccount từ visible hoặc strongly implied; chưa có cấu hình mapping account được xác minh. Phải dừng suy luận này và trả account null khi không có mapping được phê duyệt.
- category string và category_id hiện bị thay cùng nhau khi sửa; chưa có trường riêng tin cậy để đọc đề xuất AI ban đầu. Có classification_correction (30 dòng) nhưng đường correction hiện tại chưa ghi đầy đủ audit cho thao tác mới.
- Persistence tra active tại lúc ghi nhưng có thể mất FK nếu mã bị ngừng hoạt động sau khi lập prompt; cần revalidate và warning thay vì giữ code có vẻ hợp lệ.

Thiết kế không cần thêm cột AI trong migration này:
1. Giữ category_id là lựa chọn hiệu lực; response category/accountingCategoryCode tương thích tiếp tục phản ánh lựa chọn này.
2. Lưu snapshot AI proposal riêng bằng event có cấu trúc trong processing_logs đang có, version payload, liên kết document/attempt/classification. Bao gồm proposedCode và kết quả kiểm tra; dùng cơ chế chunk/log hiện có nếu cần, không nhét JSON quá chiều dài message. Kết quả invalid chỉ là đề xuất, không assignment, không tạo catalog.
3. Thêm trường response aiSuggestedCategory và finalCategory/confirmationSource từ nguồn có bằng chứng; không coi chuỗi category của các bản ghi đã bị sửa trong lịch sử là AI gốc.
4. Manual correction ghi classification_correction: old/new FK, correctedBy, thời gian, reason; giữ snapshot AI. Không ghi đè invoice hay dữ liệu đã xác nhận khi reprocess.
5. Danh mục type NULL chưa xác định được hiển thị rõ và không tự trở thành EXPENSE. Đề xuất không đưa danh mục chưa xác định type vào lựa chọn AI cho giao dịch Thu/Chi mới; tài khoản quản trị phải chọn type rõ ràng trước.
6. category_type chỉ là cách phân nhóm catalog, không chứng minh có giao dịch tiền, không quyết định direction và không tạo FinancialTransaction. Không tự chuyển category_type sang transactionAssessment.

### Workflow APPROVE / FinancialTransaction / AccountingEntry

- Giữ role/permission, khóa và lịch sử phê duyệt của Yêu cầu 04; không tự duyệt theo category/confidence.
- Trước khi tạo financial effect, kiểm tra lại danh mục được chọn còn active, cùng Company và type tương thích với transaction_type. Nếu đã chọn category NULL type, không tự điền type; trả lỗi yêu cầu xác định danh mục. Nếu workflow hiện cho phép không chọn category, việc bắt buộc chọn mới là quyết định nghiệp vụ riêng, không âm thầm bổ sung.
- Khi không hợp lệ, giao dịch phê duyệt phải rollback toàn bộ; không để APPROVED thành công rồi mới phát hiện không tạo được financial effect.
- FinancialTransactionService.apply hiện chỉ so Company khi category.company khác null, chưa chặn inactive/Company-null/type mismatch. Cần dùng validator chung. Giữ FK lịch sử, không reassign 32 giao dịch.
- createFromApprovedDocument hiện lấy FK classification hiệu lực, không lấy type từ catalog để tự tạo thu/chi. Giữ nguyên nguyên tắc này, chỉ thêm validation.
- AccountingService.createEntry hiện chưa kiểm tra active hoặc category Company so với document Company. Cần bổ sung guard; entry không có transaction_type nên không tự suy Thu/Chi từ entry.
- Khi category đã được reference, cấm xóa và đổi Company/mã có thể làm đứt chuỗi tra cứu. Ngừng sử dụng chỉ chặn assignment mới; không xóa/sửa entry/transaction/report lịch sử.

### Dashboard và báo cáo

- DashboardService.financial dùng FinancialTransactionRepository.summarize và expensesByCategory. Thu/Chi đang dựa vào transaction_type, không được đổi sang category_type.
- expensesByCategory hiện group theo categoryName, có thể gộp hai category trùng tên hoặc hai Company khi ADMIN xem tổng. Nên group theo ID/Company/name rồi mở rộng DTO giữ field category hiện tại để frontend tương thích.
- Báo cáo đọc FK final trên FinancialTransaction, không đọc đề xuất AI để tính tiền. Inactive hoặc type NULL lịch sử không bị lọc mất khỏi tổng cũ.
- Không tính lại hoặc remap giao dịch cũ khi người dùng chỉnh catalog; category_type=EXPENSE không tạo chi phí hoặc PAID.

### Frontend dự kiến

Giữ nguyên trang/tab Loại chứng từ. Tab Nhóm chi phí kế toán có loading/error/empty riêng, không dùng 0 để thay lỗi. ADMIN chọn Company qua API companies hiện có. Hiển thị code/name/type/description/active và tạo/sửa/ngừng sử dụng, cùng modal/style hiện hành. NULL hiển thị Chưa xác định. Phân biệt số lượng đã tải với số lượng đang active.

Trong chi tiết chứng từ: AI đề xuất và kế toán xác nhận riêng; không có snapshot AI cũ thì Chưa có dữ liệu, không bịa proposal. Dropdown chỉ active/cùng Company, nhưng lựa chọn inactive lịch sử vẫn hiển thị để giải thích lỗi và yêu cầu chọn lại.

### File cần sửa/thêm khi được duyệt triển khai code

Backend (đều dưới backendqt/backendqt/src/main/java/com/example/invoice):
- entity/AccountingCategory.java; enum type mới nếu chọn enum.
- repository/AccountingCategoryRepository.java; các exists/count tham chiếu trong ClassificationRepository, ClassificationCorrectionRepository, AccountingEntryRepository, FinancialTransactionRepository.
- dto/accounting/AccountingCategoryResponse.java; request DTO mới.
- service/AccountingCategoryService.java mới; controller/AccountingCategoryController.java.
- ai/AiDocumentPromptFactory.java, ai/AiDocumentResultValidator.java và DTO validated proposal nếu cần.
- service/DocumentAiProcessingService.java, DocumentAiResultPersistenceService.java, ProcessingLogService.java.
- service/ClassificationService.java; dto/classification/ClassificationResponse.java; reuse ClassificationCorrectionRepository.
- service/FinancialTransactionService.java, AccountingService.java; ReviewWorkflowService chỉ khi cần tích hợp validation atomic, không đổi chính sách quyền.
- repository/FinancialTransactionRepository.java, dto/FinancialDashboardResponse.java và DashboardService.java nếu mở rộng group theo ID/Company.

Frontend dưới quan tri/src:
- services/accountingCategoryService.ts, services/classificationService.ts, services/dashboardService.ts nếu DTO báo cáo mở rộng.
- pages/admin/CatalogManagement.tsx; component quản lý category riêng để không thay form loại chứng từ.
- pages/accountant/AccountantDocumentDetail.tsx, FinancialTransactions.tsx, Reports.tsx nếu consumer DTO thay đổi.
- Các unit/integration/regression test liên quan, không mock business data trong chức năng thực tế.

## 6. Checklist kiểm thử cần chạy sau phê duyệt

Chưa chạy các test chức năng mới vì chưa triển khai code.

- Migration trên bản restore PostgreSQL 18.6 riêng: 4 category giữ nguyên ID/code/Company/active/FK, type NULL; giữ các count baseline, xác nhận CHECK/nullable/no default.
- INSERT/UPDATE chỉ trên database test: INCOME/EXPENSE/NULL hợp lệ; giá trị lạ bị CHECK từ chối. Thử rollback DDL trên database test riêng.
- API: ADMIN đúng Company tạo/sửa/ngừng dùng; ACCOUNTANT không vượt scope; role không hợp lệ bị từ chối; code trùng/case/trim/concurrent create được xử lý.
- NULL legacy hiển thị đúng; không tự backfill; thiếu migration thì validate thất bại rõ ràng trong test, không fallback update.
- Category đang dùng không bị xóa/đổi Company/mã; inactive không được gán mới nhưng transaction/report cũ còn nguyên.
- AI chỉ active/đúng Company/đúng quy tắc type; mã sai có warning, không tạo catalog; code bị inactive giữa prompt và persist bị từ chối.
- Kế toán sửa category: AI proposal không mất, final FK/correction actor/time đúng, reprocess không ghi đè final.
- Không có cấu hình account -> accountingAccount null, không suy diễn từ tên/mã/loại.
- Workflow: kiểm tra category trước financial effect, lỗi rollback cả quyết định; duyệt lặp/concurrency vẫn như Yêu cầu 04.
- Giao dịch: inactive/cross-company/Company-null/type mismatch bị chặn theo chính sách; APPROVED không suy PAID.
- Báo cáo: nhóm theo final category, không nhầm AI proposal; category cùng tên không bị gộp sai, NULL/inactive lịch sử không biến mất.
- Hồi quy backend, frontend unit test/build; runtime trên môi trường riêng. Không dùng invoice_db để thử ghi.

## 7. Kiểm tra thực sự đã chạy trong bước thiết kế

- SELECT metadata/count/scope/constraints/index: thành công, read-only=on, không có DDL/DML gửi tới PostgreSQL.
- Kiểm tra tĩnh file SQL bằng Node: sáu statement, đúng nullable/check, không có DROP/TRUNCATE/DELETE/UPDATE/INSERT/DEFAULT/NOT NULL/IF NOT EXISTS; file không ở thư mục migration runtime. PASS tĩnh, KHÔNG chứng minh migration đã chạy hoặc restore thành công.
- git diff --check: PASS. invoice_1/, Entity, cấu hình/resources không có thay đổi mới trong bước này.
- Không chạy build/test chức năng mới hoặc backend runtime. Kết quả 350 PASS/13 SKIP backend và 20 PASS frontend của Yêu cầu 04 là lịch sử, không dùng để tuyên bố Yêu cầu 05 PASS.
- Không backup/restore, không cấp quyền DB, không seed, không migration, không commit/push.

## 8. Cổng duyệt cuối / trạng thái

Đã chuẩn bị hai file: báo cáo này và SQL draft. Chưa thay production code/test/UI.

Cần duyệt riêng:
1. SQL draft category_type và kế hoạch code ở trên; xác nhận quy tắc NULL legacy, quyền quản lý ADMIN và guard type khi ghi giao dịch mới.
2. Nếu muốn UNIQUE code được DB bảo đảm, duyệt thiết kế/migration uniqueness riêng; chưa có trong SQL draft này.
3. Cho phép triển khai code và kiểm thử mock/H2/PostgreSQL riêng; không đồng nghĩa cho phép migration invoice_db.
4. Backup/restore test độc lập và cổng phê duyệt DDL trên invoice_db sau khi có bằng chứng kiểm thử.

DATABASE READ-ONLY AUDIT: VERIFIED.
SCHEMA CHANGE: DRAFT ONLY.
MIGRATION: NOT EXECUTED.
APPLICATION IMPLEMENTATION: PENDING FINAL APPROVAL.
REQUEST 05 FUNCTIONAL COMPLETION: NOT YET VERIFIED.
