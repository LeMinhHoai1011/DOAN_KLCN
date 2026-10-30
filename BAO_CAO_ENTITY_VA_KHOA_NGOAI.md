# Báo cáo rà soát entity, cột và khả năng giảm khóa ngoại

Ngày: 26/10/2026. Phạm vi: mã nguồn tại D:/maudoan_2/DOAN_KLCN.

## 1. Kết luận

**Có thể thiết kế lại để giảm khóa ngoại, nhưng chưa có khóa nào nên xóa ngay chỉ dựa vào việc nhìn sơ đồ.** Ưu tiên đánh giá hai cột `extracted_fields.invoice_id` và `financial_transactions.invoice_id`. Nếu xác nhận được tính nhất quán, ý nghĩa liên kết và cập nhật đầy đủ các nơi sử dụng, phương án này giảm từ **52 xuống 50 quan hệ FK được mapping** (khoảng 3,8%), giữ nguyên số bảng. Đây là đề xuất thiết kế có điều kiện, không phải thay đổi đã thực hiện.

Một ứng viên bổ sung là `reconciliations.invoice_id`: có thể đưa tổng số xuống 49 (giảm 5,8%), nhưng phải quyết định rõ cách giữ danh tính hóa đơn của lần đối soát cũ khi hóa đơn bị xóa/tạo lại. Không nên đánh đổi lịch sử chỉ để giảm một đường trên ERD.

Không thay đổi code, cấu hình, migration hoặc cơ sở dữ liệu. Chỉ tạo báo cáo Markdown này. Không chạy ứng dụng hay kiểm thử có thể ghi dữ liệu.

## 2. Phạm vi và cách đếm

Đã rà soát toàn bộ lớp `@Entity` trong backend, tất cả thuộc tính lưu trữ của chúng, mapping quan hệ, các service/repository liên quan và migration hiện có. DTO và model Dart/TypeScript không tạo bảng JPA nên không tính là entity cơ sở dữ liệu.

- 27 entity/bảng và 52 cột `@JoinColumn` theo mã nguồn.
- Mỗi cột FK chỉ đếm ở phía sở hữu. Collection `mappedBy` và `Document.invoice` không tạo thêm cột FK.
- `@Transient` không phải cột. Enum STRING không tạo bảng hoặc FK.
- Chưa kết nối database để kiểm kê `pg_constraint`, dữ liệu thực tế hoặc kích thước/index. 52 là số quan hệ khai báo trong entity, không khẳng định số constraint đang tồn tại trong PostgreSQL. Cấu hình hiện dùng `ddl-auto: validate`, migration có bước chạy riêng.
- Tên cột không khai báo tường minh được trình bày theo quy ước snake_case của dự án; cần đối chiếu metadata DB khi triển khai.
- Nullable/UNIQUE/độ dài trong phụ lục là khai báo JPA, không thay thế việc kiểm tra constraint DB. Với trường không khai báo độ dài, không tự suy diễn giới hạn nghiệp vụ.

## 3. Các khóa ngoại có thể xem xét giảm

### 3.1. extracted_fields.invoice_id — ưu tiên cao nhất

Hiện `ExtractedField` có `document_id` bắt buộc và `invoice_id` tùy chọn. Chính chú thích trong Document xác định Document là chủ sở hữu chuẩn của trường trích xuất; invoice chỉ là metadata tương thích. Migration 20260928 đã backfill document_id và đặt NOT NULL; migration 20261003 cho phép invoice_id NULL.

Đường thay thế: `extracted_fields.document_id → documents.document_id ← invoice_data.document_id`. Cột document_id của invoice_data là UNIQUE, vì vậy mỗi chứng từ có tối đa một hóa đơn tại một thời điểm.

Đề xuất: giữ document_id, cân nhắc bỏ invoice_id nếu trường trích xuất thực sự thuộc chứng từ và không cần phân biệt liên kết hóa đơn độc lập. Giảm 1 FK và 1 cột.

Điều kiện và ảnh hưởng:

- Kiểm tra tất cả liên kết invoice khác NULL đều cùng document; kiểm tra cả các trường có invoice NULL trên chứng từ đã có invoice, vì suy diễn tự động sẽ làm thay đổi ý nghĩa NULL hiện tại.
- Rà soát `ExtractedFieldRepository.findByInvoiceId`, `Invoice.extractedFields`, mã persist và DTO/mapper liên quan; thay truy vấn và chính sách cascade/orphanRemoval tương ứng.
- `DocumentAiResultPersistenceService.removeStaleInvoice` đang chủ động tách trường khỏi invoice trước khi xóa hóa đơn AI. Không được để xóa invoice kéo theo mất trường/correction vốn thuộc Document.
- Đường suy diễn trả hóa đơn hiện tại của chứng từ; nếu cần giữ hóa đơn lịch sử của từng lần trích xuất thì chưa đủ điều kiện bỏ.

### 3.2. financial_transactions.invoice_id — có thể giảm sau chuẩn hóa dữ liệu

`FinancialTransaction` hiện giữ company_id, document_id, invoice_id, accounting_category_id và created_by. `PaymentLinkService.resolve` suy ra chứng từ từ hóa đơn, từ chối INVOICE_DOCUMENT_MISMATCH và tự tìm hóa đơn của chứng từ nếu chưa truyền invoice. Với luồng này, lưu thêm invoice_id thường lặp lại đường liên kết qua document_id.

Đề xuất: giữ document_id làm liên kết hồ sơ; trả invoiceId cho API bằng truy vấn invoice_data theo document_id khi cần. Giảm 1 FK và 1 cột nếu không cần lưu danh tính invoice lịch sử độc lập.

Điều kiện và ảnh hưởng:

- Backfill các giao dịch cũ chỉ có invoice_id; kiểm tra mismatch, liên kết orphan và khác công ty. Không tự sửa dữ liệu không rõ nguồn gốc.
- Kiểm tra các giao dịch document != NULL, invoice == NULL khi chứng từ đã có invoice; xác nhận ý nghĩa trước khi suy diễn.
- `FinancialTransactionRepository.linked` hiện dùng điều kiện OR giữa document và invoice, thể hiện hệ thống còn đọc cả hai dạng liên kết. Không được bỏ một nhánh trước khi xử lý dữ liệu cũ.
- Cập nhật FinancialTransactionService, PaymentLinkService, PaymentSchemaGuard, repository, mapper/DTO và index trong migration thanh toán. API có thể giữ invoiceId như trường suy diễn để giảm ảnh hưởng frontend.
- Không bỏ company_id: giao dịch có thể không gắn chứng từ/hóa đơn (`resolve` cho phép cả hai NULL), đồng thời company_id phục vụ phân quyền, tổng hợp và unique request_key theo công ty.
- Không khẳng định nhanh hơn nếu chưa có EXPLAIN/đo tải: ít cột ghi hơn có thể đi kèm thêm JOIN khi đọc.

### 3.3. reconciliations.invoice_id — ứng viên có rủi ro lịch sử

Hiện mỗi dòng có reconciliation_batch_id, document_id bắt buộc, invoice_id tùy chọn, financial_transaction_id tùy chọn và reviewed_by. `ReconciliationService.save` ghi đồng thời document và invoice; tại một thời điểm invoice có thể tìm từ Document.

Có thể bỏ invoice_id nếu đối soát chỉ cần tham chiếu chứng từ và lưu snapshot đủ để diễn giải kết quả cũ. Tuy nhiên service cố ý append kết quả mỗi lần chạy, giữ quyết định thủ công; mã xử lý AI còn có khả năng xóa hóa đơn cũ. Tra invoice hiện tại không chứng minh đó là invoice ở thời điểm đối soát.

Khuyến nghị: để sau hai mục trên. Giữ document_id để truy cập kết quả cả khi invoice/transaction không tồn tại. Giữ financial_transaction_id nếu một dòng đối soát cần chỉ đích danh giao dịch; không suy ra duy nhất giao dịch từ chứng từ vì một hóa đơn hiện được thanh toán nhiều lần. Luồng PaymentReconciliationService hiện lưu snapshot tổng thanh toán trong AuditLog; đây là luồng khác với các dòng kiểm tra số học trong ReconciliationService.

## 4. Các trường hợp không nên coi là FK dư

| Cột/nhóm | Vì sao nên giữ hoặc chưa thể bỏ |
|---|---|
| documents.company_id | Chủ sở hữu chứng từ độc lập người upload; chuyển công ty của user không được đổi quyền sở hữu lịch sử. |
| accounting_categories.company_id | Danh mục thuộc doanh nghiệp; không suy ra từ người tạo hoặc từ giao dịch. |
| audit_logs.company_id | Phạm vi sự kiện tại thời điểm phát sinh; không suy từ company hiện tại của user. |
| reconciliation_batches.company_id | Phạm vi lô độc lập người tạo; một lô có thể chứa nhiều chứng từ. |
| processing_logs.document_id | ProcessingLogService.append và log đổi loại chứng từ có thể chỉ gắn Document, không có processing_job_id. Bỏ cột này sẽ làm mất đường truy cập các log đó. |
| processing_logs.processing_job_id | Nhận diện một job cụ thể trong nhiều lần chạy của cùng chứng từ; document_id không thay thế được. |
| field_corrections.corrected_by | ExtractedFieldCorrectionService ghi actor trực tiếp mà không gắn review; không suy ra từ review.reviewer được. |
| classification_correction.corrected_by/review_id | Chỉ có thể hợp nhất khi mọi correction bắt buộc thuộc một review và người sửa luôn trùng reviewer. Mapping hiện không bảo đảm điều đó. |
| classification_correction.old_category_id/new_category_id | Trạng thái trước/sau là hai vai trò khác nhau; category hiện tại của Classification không tái dựng được lịch sử. Có thể thay bằng snapshot nếu thay đổi thiết kế audit, nhưng sẽ mất ràng buộc danh mục. |
| created_by, uploaded_by, reviewed_by, assigned_by | Các vai trò người thao tác khác nhau. Không thay bằng owner của Document hoặc company; người tạo có thể khác người duyệt/người cấp quyền. |
| user_roles.user_id/role_id; role_permissions.role_id/permission_id | Cặp quan hệ cốt lõi của phân quyền nhiều-nhiều. Đổi sang @ManyToMany vẫn cần bảng nối và hai FK, đồng thời khó giữ assigned_at/assigned_by. |
| invoice_data.document_id; invoice_items.invoice_id | Liên kết sở hữu dữ liệu hóa đơn/dòng hàng; bỏ sẽ làm mất toàn vẹn và quản lý vòng đời. |
| processing_attempts.processing_job_id | Phân biệt retry của từng job; UNIQUE(job, attempt_number) là ràng buộc có ý nghĩa. |
| ai_classifications.category_id; accounting_entries.category_id; financial_transactions.accounting_category_id | Gợi ý AI, bút toán và phân loại giao dịch là các đối tượng nghiệp vụ khác nhau; không mặc định luôn có cùng category. |

`ClassificationCorrection` chưa thấy được tạo mới trong luồng sửa phân loại chính đã đọc; `ClassificationService.correction` cập nhật classification và ghi audit. Nhưng entity vẫn được tham chiếu bởi repository, collection và kiểm tra sử dụng danh mục. Không có bằng chứng dữ liệu thực tế để kết luận bảng rỗng hoặc có thể bỏ cả 5 FK. Cần kiểm kê dữ liệu và yêu cầu lịch sử trước.

## 5. Cột trùng thông tin và cột mở rộng đáng chú ý

- **documents.document_type và type_id:** cùng biểu diễn loại chứng từ; service đang đồng bộ mã và FK, vẫn có nhánh fallback khi type NULL. Nên giữ type_id và xem xét loại bỏ chuỗi document_type sau khi hết legacy, thay vì bỏ FK tới bảng danh mục đang có CRUD/active/thống kê. Việc này giảm một cột dữ liệu, không giảm FK.
- **ExtractedField và Invoice:** có thể chứa tên trường liên quan cùng hóa đơn, nhưng dữ liệu trích xuất linh hoạt, nguồn AI và lịch sử correction khác dữ liệu hóa đơn chuẩn. Không gộp/bỏ toàn bộ chỉ vì tên trường giống nhau.
- **Classification.category, predicted_label, accounting_account và category_id:** có thể là nhãn AI, giá trị hiển thị/gợi ý hoặc danh mục chuẩn. Phải phân biệt evidence gốc với giá trị hiệu lực trước khi hợp nhất.
- **documents.company_role, document_direction, transaction_assessment_*:** là kết quả đánh giá OCR/AI. Không phải FK và không thay thế thông tin giao dịch đã xác nhận.
- **financial_transactions.payment_confirmation:** phân biệt CONFIRMED với legacy/chưa xác minh; không suy từ status hoặc đánh giá AI.
- **request_key và request_fingerprint:** chống gửi trùng, phát hiện request cùng key khác nội dung; không phải khóa ngoại. Migration 20261024 có unique theo (company_id, request_key).
- **users.token_version và row_version:** lần lượt phục vụ thu hồi xác thực và optimistic locking; không phải FK và không cùng ý nghĩa.
- **DocumentVersion và Document:** tên file, object key, kích thước có thể trùng nhưng một bên là phiên bản lịch sử, một bên trạng thái hiện tại.
- **expected_amount, actual_amount, difference_amount:** difference có thể tính toán, nhưng các giá trị đang lưu là bằng chứng lần đối soát. Đây là quyết định snapshot/tính động, không phải tối ưu FK.
- Các cột confidence/model/version/raw_text/layout_json phục vụ giải thích, truy vết và đánh giá OCR/AI; không tự bỏ để làm schema ngắn hơn.

## 6. Phân biệt ba kiểu “giảm khóa ngoại”

1. Bỏ thuộc tính collection `mappedBy`: đơn giản hóa Java/ERD hiển thị, không giảm FK vật lý.
2. Đổi quan hệ entity thành Long xxxId hoặc chỉ DROP CONSTRAINT: có thể giảm quan hệ ORM/constraint nhưng giữ cột liên kết, đồng thời chuyển trách nhiệm chống dữ liệu mồ côi sang ứng dụng. Không phải xóa dữ liệu dư theo mô hình.
3. Bỏ một cột liên kết suy diễn được và cập nhật truy vấn/vòng đời: thực sự giảm cột và FK. Hai ứng viên ưu tiên thuộc nhóm này, với các điều kiện đã nêu.

Không khuyến nghị gộp bảng nghiệp vụ, bỏ lịch sử hoặc bỏ ràng buộc phân quyền chỉ để đạt số FK thấp. Không có bằng chứng rằng 52 FK hiện gây chậm; cần đo trước nếu mục tiêu thực là hiệu năng.

## 7. Kiểm tra cần làm nếu triển khai sau này (chưa thực hiện)

- Kiểm kê constraint/index thực tế và số bản ghi theo từng dạng document/invoice NULL; kiểm tra tính nhất quán document–invoice–company trên cả ba bảng ứng viên.
- Xác nhận chính sách giữ lịch sử khi AI xóa/tạo lại invoice và khi user đổi công ty; xác nhận ý nghĩa khác biệt giữa NULL và liên kết hiện tại.
- Kiểm tra các nguồn đọc/ghi ngoài backend, báo cáo/export và dữ liệu legacy trước khi chọn nguồn chuẩn.
- Thiết kế backfill và chuyển đổi tương thích, kiểm chứng truy vấn mới rồi mới bỏ cột/constraint/index không còn dùng.
- Kiểm thử hồi quy: chứng từ không phải hóa đơn, sửa trường không có review, đổi loại/xử lý lại AI, thanh toán nhiều lần, giao dịch không gắn hồ sơ, cách ly công ty, lịch sử đối soát và correction.
- Chỉ triển khai bằng migration riêng sau khi phê duyệt thay đổi; báo cáo này không thực thi migration.

## 8. Nguồn mã chính để đối chiếu

Các đường dẫn dưới đây tương đối từ thư mục dự án:

- `backendqt/backendqt/src/main/java/com/example/invoice/entity/`: toàn bộ mapping, liệt kê đầy đủ tại phụ lục.
- `service/DocumentAiResultPersistenceService.java`: persist trường/invoice và removeStaleInvoice.
- `service/PaymentLinkService.java`, `service/FinancialTransactionService.java`, `service/PaymentSchemaGuard.java`: chuẩn hóa liên kết và nghiệp vụ thanh toán.
- `repository/FinancialTransactionRepository.java`, `repository/ExtractedFieldRepository.java`: truy vấn dùng invoice/document.
- `service/ReconciliationService.java`, `service/PaymentReconciliationService.java`: kiểm tra hóa đơn và snapshot thanh toán.
- `service/ProcessingLogService.java`, `service/PipelineExecutionService.java`, `service/DocumentService.java`: log có/không có job.
- `service/ExtractedFieldCorrectionService.java`, `service/ClassificationService.java`, `service/AccountingCategoryService.java`: actor, correction và bảo vệ danh mục đang được sử dụng.
- Đường dẫn `service/` và `repository/` ở trên đều thuộc `backendqt/backendqt/src/main/java/com/example/invoice/`.
- `backendqt/backendqt/src/main/resources/db/migration/`: đặc biệt 20260928, 20261003 và 20261024.

## 9. Tổng kiểm kê theo entity


| Entity | Bảng | Số cột lưu trữ | Số FK |
|---|---|---:|---:|
| AccountingCategory | accounting_categories | 9 | 1 |
| AccountingEntry | accounting_entries | 9 | 3 |
| AuditLog | audit_logs | 11 | 2 |
| Classification | ai_classifications | 14 | 2 |
| ClassificationCorrection | classification_correction | 8 | 5 |
| Company | companies | 8 | 0 |
| Document | documents | 21 | 3 |
| DocumentReview | document_reviews | 7 | 2 |
| DocumentType | document_types | 7 | 0 |
| DocumentVersion | document_versions | 10 | 2 |
| ExtractedField | extracted_fields | 9 | 2 |
| FieldCorrection | field_corrections | 8 | 3 |
| FinancialTransaction | financial_transactions | 17 | 5 |
| Invoice | invoice_data | 22 | 1 |
| InvoiceItem | invoice_items | 9 | 1 |
| OCRResult | ocr_results | 12 | 1 |
| Permission | permissions | 6 | 1 |
| PermissionGroup | permission_groups | 5 | 0 |
| ProcessingAttempt | processing_attempts | 8 | 1 |
| ProcessingJob | processing_jobs | 10 | 1 |
| ProcessingLog | processing_logs | 12 | 2 |
| Reconciliation | reconciliations | 14 | 5 |
| ReconciliationBatch | reconciliation_batches | 9 | 2 |
| Role | roles | 7 | 0 |
| RolePermission | role_permissions | 5 | 3 |
| User | users | 13 | 1 |
| UserRoleAssignment | user_roles | 5 | 3 |
| **Tổng** | **27 bảng** | **275** | **52** |

## 10. Phụ lục đầy đủ các cột

Mỗi hàng là một thuộc tính lưu trữ; kiểu Java giúp phân biệt tiền, thời gian, enum và quan hệ. PK kiểu Long dùng IDENTITY theo entity. Cột FK lưu định danh của entity đích. Các collection và thuộc tính inverse/transient được ghi riêng, không tính là cột.


### AccountingCategory → accounting_categories

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/AccountingCategory.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `category_id` | `Long` | PK | NOT NULL |
| `company` | `company_id` | `Company` | FK → Company | nullable theo mapping |
| `categoryCode` | `category_code` | `String` | Dữ liệu | NOT NULL, length=50 |
| `categoryName` | `category_name` | `String` | Dữ liệu | NOT NULL, length=200 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping |
| `categoryType` | `category_type` | `AccountingCategoryType` | Dữ liệu | nullable theo mapping, length=20, enum STRING |
| `active` | `active` | `boolean` | Dữ liệu | NOT NULL, boolean default true |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### AccountingEntry → accounting_entries

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/AccountingEntry.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `entry_id` | `Long` | PK | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `category` | `category_id` | `AccountingCategory` | FK → AccountingCategory | NOT NULL |
| `createdBy` | `created_by` | `User` | FK → User | nullable theo mapping |
| `amount` | `amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping |
| `entryDate` | `entry_date` | `LocalDate` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### AuditLog → audit_logs

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/AuditLog.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `audit_log_id` | `Long` | PK | NOT NULL |
| `user` | `user_id` | `User` | FK → User | nullable theo mapping |
| `company` | `company_id` | `Company` | FK → Company | nullable theo mapping |
| `action` | `action` | `String` | Dữ liệu | NOT NULL, length=100 |
| `entityType` | `entity_type` | `String` | Dữ liệu | nullable theo mapping, length=100 |
| `entityId` | `entity_id` | `String` | Dữ liệu | nullable theo mapping, length=100 |
| `oldValue` | `old_value` | `String` | Dữ liệu | nullable theo mapping, text |
| `newValue` | `new_value` | `String` | Dữ liệu | nullable theo mapping, text |
| `ipAddress` | `ip_address` | `String` | Dữ liệu | nullable theo mapping, length=45 |
| `userAgent` | `user_agent` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

### Classification → ai_classifications

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Classification.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `classification_id` | `Long` | PK | NOT NULL, enum STRING |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `accountingCategory` | `category_id` | `AccountingCategory` | FK → AccountingCategory | nullable theo mapping |
| `modelName` | `model_name` | `String` | Dữ liệu | nullable theo mapping |
| `modelVersion` | `model_version` | `String` | Dữ liệu | nullable theo mapping |
| `predictedLabel` | `predicted_label` | `String` | Dữ liệu | nullable theo mapping |
| `category` | `category` | `String` | Dữ liệu | nullable theo mapping |
| `accountingAccount` | `accounting_account` | `String` | Dữ liệu | nullable theo mapping, length=100 |
| `confidence` | `confidence` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `reason` | `reason` | `String` | Dữ liệu | nullable theo mapping, text |
| `status` | `status` | `ClassificationStatus` | Dữ liệu | NOT NULL, length=30, enum STRING |
| `aiGenerated` | `ai_generated` | `boolean` | Dữ liệu | NOT NULL |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

### ClassificationCorrection → classification_correction

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/ClassificationCorrection.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `correction_id` | `Long` | PK | NOT NULL |
| `classification` | `classification_id` | `Classification` | FK → Classification | NOT NULL |
| `review` | `review_id` | `DocumentReview` | FK → DocumentReview | nullable theo mapping |
| `correctedBy` | `corrected_by` | `User` | FK → User | nullable theo mapping |
| `oldCategory` | `old_category_id` | `AccountingCategory` | FK → AccountingCategory | nullable theo mapping |
| `newCategory` | `new_category_id` | `AccountingCategory` | FK → AccountingCategory | nullable theo mapping |
| `reason` | `reason` | `String` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### Company → companies

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Company.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `company_id` | `Long` | PK | NOT NULL |
| `companyName` | `company_name` | `String` | Dữ liệu | NOT NULL, length=200 |
| `taxCode` | `tax_code` | `String` | Dữ liệu | nullable theo mapping, UNIQUE, length=50 |
| `address` | `address` | `String` | Dữ liệu | nullable theo mapping |
| `phone` | `phone` | `String` | Dữ liệu | nullable theo mapping |
| `email` | `email` | `String` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

Không tạo cột tại bảng này: `users`, `accountingCategories`.

### Document → documents

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Document.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `document_id` | `Long` | PK | NOT NULL |
| `company` | `company_id` | `Company` | FK → Company | nullable theo mapping |
| `documentType` | `document_type` | `String` | Dữ liệu | nullable theo mapping, length=50 |
| `companyRole` | `company_role` | `String` | Dữ liệu | nullable theo mapping, length=20 |
| `companyRoleConfidence` | `company_role_confidence` | `java.math.BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `companyRoleReason` | `company_role_reason` | `String` | Dữ liệu | nullable theo mapping, text |
| `documentDirection` | `document_direction` | `String` | Dữ liệu | nullable theo mapping, length=20 |
| `transactionAssessmentType` | `transaction_assessment_type` | `String` | Dữ liệu | nullable theo mapping, length=20 |
| `transactionAssessmentConfidence` | `transaction_assessment_confidence` | `java.math.BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `transactionAssessmentReason` | `transaction_assessment_reason` | `String` | Dữ liệu | nullable theo mapping, text |
| `type` | `type_id` | `DocumentType` | FK → DocumentType | nullable theo mapping |
| `originalFileName` | `original_file_name` | `String` | Dữ liệu | NOT NULL |
| `fileName` | `file_name` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `fileType` | `mime_type` | `String` | Dữ liệu | NOT NULL, length=100 |
| `fileSize` | `file_size` | `Long` | Dữ liệu | NOT NULL |
| `filePath` | `object_key` | `String` | Dữ liệu | nullable theo mapping |
| `status` | `processing_status` | `DocumentStatus` | Dữ liệu | NOT NULL, length=30, enum STRING |
| `reviewStatus` | `review_status` | `ReviewStatus` | Dữ liệu | NOT NULL, length=30, enum STRING |
| `uploadedBy` | `uploaded_by` | `User` | FK → User | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

Không tạo cột tại bảng này: `invoice`, `extractedFields`, `versions`, `ocrResults`, `classifications`, `reviews`, `processingLogs`, `accountingEntries`.

### DocumentReview → document_reviews

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/DocumentReview.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `review_id` | `Long` | PK | NOT NULL, enum STRING |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `reviewer` | `reviewer_id` | `User` | FK → User | nullable theo mapping |
| `reviewType` | `review_type` | `ReviewType` | Dữ liệu | NOT NULL, length=30, enum STRING |
| `reviewStatus` | `review_status` | `ReviewStatus` | Dữ liệu | NOT NULL, length=30, enum STRING |
| `reviewNote` | `review_note` | `String` | Dữ liệu | nullable theo mapping |
| `reviewedAt` | `reviewed_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

Không tạo cột tại bảng này: `fieldCorrections`, `classificationCorrections`.

### DocumentType → document_types

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/DocumentType.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `id` | `Long` | PK | NOT NULL |
| `code` | `code` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=50 |
| `name` | `name` | `String` | Dữ liệu | NOT NULL, length=100 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping |
| `active` | `active` | `boolean` | Dữ liệu | NOT NULL |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### DocumentVersion → document_versions

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/DocumentVersion.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `version_id` | `Long` | PK | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `createdBy` | `created_by` | `User` | FK → User | nullable theo mapping |
| `versionNumber` | `version_number` | `Integer` | Dữ liệu | NOT NULL |
| `originalFileName` | `original_filename` | `String` | Dữ liệu | nullable theo mapping |
| `contentType` | `content_type` | `String` | Dữ liệu | nullable theo mapping |
| `fileSize` | `file_size` | `Long` | Dữ liệu | nullable theo mapping |
| `fileName` | `file_name` | `String` | Dữ liệu | nullable theo mapping |
| `objectKey` | `object_key` | `String` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### ExtractedField → extracted_fields

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/ExtractedField.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `field_id` | `Long` | PK | NOT NULL |
| `invoice` | `invoice_id` | `Invoice` | FK → Invoice | nullable theo mapping |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `fieldName` | `field_name` | `String` | Dữ liệu | NOT NULL, length=100 |
| `fieldValue` | `field_value` | `String` | Dữ liệu | nullable theo mapping, text |
| `source` | `source` | `String` | Dữ liệu | nullable theo mapping |
| `confidence` | `confidence` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

Không tạo cột tại bảng này: `corrections`.

### FieldCorrection → field_corrections

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/FieldCorrection.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `correction_id` | `Long` | PK | NOT NULL |
| `field` | `field_id` | `ExtractedField` | FK → ExtractedField | NOT NULL |
| `review` | `review_id` | `DocumentReview` | FK → DocumentReview | nullable theo mapping |
| `correctedBy` | `corrected_by` | `User` | FK → User | nullable theo mapping |
| `oldValue` | `old_value` | `String` | Dữ liệu | nullable theo mapping, text |
| `newValue` | `new_value` | `String` | Dữ liệu | nullable theo mapping, text |
| `reason` | `reason` | `String` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### FinancialTransaction → financial_transactions

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/FinancialTransaction.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `financial_transaction_id` | `Long` | PK | NOT NULL |
| `company` | `company_id` | `Company` | FK → Company | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | nullable theo mapping |
| `invoice` | `invoice_id` | `Invoice` | FK → Invoice | nullable theo mapping |
| `accountingCategory` | `accounting_category_id` | `AccountingCategory` | FK → AccountingCategory | nullable theo mapping |
| `transactionType` | `transaction_type` | `String` | Dữ liệu | NOT NULL, length=50 |
| `transactionDate` | `transaction_date` | `LocalDate` | Dữ liệu | NOT NULL |
| `amount` | `amount` | `BigDecimal` | Dữ liệu | NOT NULL, precision=19, scale=2 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping, text |
| `paymentMethod` | `payment_method` | `String` | Dữ liệu | nullable theo mapping, length=50 |
| `status` | `status` | `String` | Dữ liệu | NOT NULL, length=30 |
| `paymentConfirmation` | `payment_confirmation` | `String` | Dữ liệu | nullable theo mapping, length=20 |
| `requestKey` | `request_key` | `String` | Dữ liệu | nullable theo mapping, length=36 |
| `requestFingerprint` | `request_fingerprint` | `String` | Dữ liệu | nullable theo mapping, length=64 |
| `createdBy` | `created_by` | `User` | FK → User | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

### Invoice → invoice_data

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Invoice.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `invoice_id` | `Long` | PK | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL, UNIQUE |
| `invoiceNumber` | `invoice_number` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `invoiceSeries` | `invoice_series` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `invoiceDate` | `invoice_date` | `LocalDate` | Dữ liệu | nullable theo mapping |
| `sellerName` | `seller_name` | `String` | Dữ liệu | nullable theo mapping, text |
| `sellerTaxCode` | `seller_tax_code` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `sellerAddress` | `seller_address` | `String` | Dữ liệu | nullable theo mapping, text |
| `sellerPhone` | `seller_phone` | `String` | Dữ liệu | nullable theo mapping, length=100 |
| `buyerName` | `buyer_name` | `String` | Dữ liệu | nullable theo mapping, text |
| `buyerTaxCode` | `buyer_tax_code` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `buyerAddress` | `buyer_address` | `String` | Dữ liệu | nullable theo mapping, text |
| `subtotal` | `total_before_tax` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `vatAmount` | `tax_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `totalAmount` | `total_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `paymentMethod` | `payment_method` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `amountInWords` | `amount_in_words` | `String` | Dữ liệu | nullable theo mapping, text |
| `taxAuthorityCode` | `tax_authority_code` | `String` | Dữ liệu | nullable theo mapping, length=255 |
| `signDate` | `sign_date` | `LocalDate` | Dữ liệu | nullable theo mapping |
| `aiGenerated` | `ai_generated` | `boolean` | Dữ liệu | NOT NULL |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

Không tạo cột tại bảng này: `items`, `extractedFields`.

### InvoiceItem → invoice_items

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/InvoiceItem.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `item_id` | `Long` | PK | NOT NULL |
| `invoice` | `invoice_id` | `Invoice` | FK → Invoice | NOT NULL |
| `productName` | `item_name` | `String` | Dữ liệu | NOT NULL, text |
| `quantity` | `quantity` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `unit` | `unit` | `String` | Dữ liệu | nullable theo mapping, length=100 |
| `unitPrice` | `unit_price` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `taxRate` | `tax_rate` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `taxAmount` | `tax_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `amount` | `total_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |

### OCRResult → ocr_results

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/OCRResult.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `ocr_id` | `Long` | PK | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `ocrEngine` | `ocr_engine` | `String` | Dữ liệu | nullable theo mapping |
| `modelVersion` | `model_version` | `String` | Dữ liệu | nullable theo mapping |
| `language` | `language` | `String` | Dữ liệu | nullable theo mapping |
| `sourceType` | `source_type` | `String` | Dữ liệu | nullable theo mapping |
| `rawText` | `raw_text` | `String` | Dữ liệu | NOT NULL, text |
| `layoutJson` | `layout_json` | `String` | Dữ liệu | nullable theo mapping, text |
| `confidence` | `confidence` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `processingTime` | `processing_time` | `Long` | Dữ liệu | nullable theo mapping |
| `status` | `status` | `String` | Dữ liệu | nullable theo mapping |
| `processedAt` | `processed_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### Permission → permissions

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Permission.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `permission_id` | `Long` | PK | NOT NULL |
| `group` | `group_id` | `PermissionGroup` | FK → PermissionGroup | NOT NULL |
| `code` | `code` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=100 |
| `name` | `name` | `String` | Dữ liệu | NOT NULL, length=100 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping |
| `active` | `active` | `boolean` | Dữ liệu | NOT NULL |

Không tạo cột tại bảng này: `rolePermissions`, `roles`.

### PermissionGroup → permission_groups

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/PermissionGroup.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `group_id` | `Long` | PK | NOT NULL |
| `code` | `code` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=50 |
| `name` | `name` | `String` | Dữ liệu | NOT NULL, length=100 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping |
| `active` | `active` | `boolean` | Dữ liệu | NOT NULL |

Không tạo cột tại bảng này: `permissions`.

### ProcessingAttempt → processing_attempts

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/ProcessingAttempt.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `processing_attempt_id` | `Long` | PK | NOT NULL |
| `processingJob` | `processing_job_id` | `ProcessingJob` | FK → ProcessingJob | NOT NULL |
| `attemptNumber` | `attempt_number` | `Integer` | Dữ liệu | NOT NULL |
| `status` | `status` | `String` | Dữ liệu | NOT NULL, length=30 |
| `startedAt` | `started_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `completedAt` | `completed_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `errorMessage` | `error_message` | `String` | Dữ liệu | nullable theo mapping, text |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

Ràng buộc bảng: UNIQUE(processing_job_id, attempt_number).

### ProcessingJob → processing_jobs

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/ProcessingJob.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `processing_job_id` | `Long` | PK | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `jobType` | `job_type` | `String` | Dữ liệu | NOT NULL, length=50 |
| `status` | `status` | `String` | Dữ liệu | NOT NULL, length=30 |
| `priority` | `priority` | `Integer` | Dữ liệu | nullable theo mapping |
| `startedAt` | `started_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `completedAt` | `completed_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `errorMessage` | `error_message` | `String` | Dữ liệu | nullable theo mapping, text |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

Không tạo cột tại bảng này: `attempts`, `logs`.

### ProcessingLog → processing_logs

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/ProcessingLog.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `log_id` | `Long` | PK | NOT NULL |
| `processingJob` | `processing_job_id` | `ProcessingJob` | FK → ProcessingJob | nullable theo mapping |
| `document` | `document_id` | `Document` | FK → Document | nullable theo mapping |
| `processType` | `process_type` | `String` | Dữ liệu | nullable theo mapping |
| `agentStep` | `agent_step` | `String` | Dữ liệu | nullable theo mapping |
| `modelName` | `model_name` | `String` | Dữ liệu | nullable theo mapping |
| `modelVersion` | `model_version` | `String` | Dữ liệu | nullable theo mapping |
| `status` | `status` | `String` | Dữ liệu | nullable theo mapping |
| `confidence` | `confidence` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=5, scale=2 |
| `executionTime` | `execution_time` | `Long` | Dữ liệu | nullable theo mapping |
| `message` | `message` | `String` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

### Reconciliation → reconciliations

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Reconciliation.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `reconciliation_id` | `Long` | PK | NOT NULL |
| `reconciliationBatch` | `reconciliation_batch_id` | `ReconciliationBatch` | FK → ReconciliationBatch | NOT NULL |
| `document` | `document_id` | `Document` | FK → Document | NOT NULL |
| `invoice` | `invoice_id` | `Invoice` | FK → Invoice | nullable theo mapping |
| `financialTransaction` | `financial_transaction_id` | `FinancialTransaction` | FK → FinancialTransaction | nullable theo mapping |
| `expectedAmount` | `expected_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `actualAmount` | `actual_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `differenceAmount` | `difference_amount` | `BigDecimal` | Dữ liệu | nullable theo mapping, precision=19, scale=2 |
| `status` | `status` | `String` | Dữ liệu | NOT NULL, length=30 |
| `comment` | `comment` | `String` | Dữ liệu | nullable theo mapping, text |
| `reviewedBy` | `reviewed_by` | `User` | FK → User | nullable theo mapping |
| `reviewedAt` | `reviewed_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

### ReconciliationBatch → reconciliation_batches

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/ReconciliationBatch.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `reconciliation_batch_id` | `Long` | PK | NOT NULL |
| `company` | `company_id` | `Company` | FK → Company | NOT NULL |
| `batchCode` | `batch_code` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=100 |
| `periodFrom` | `period_from` | `LocalDate` | Dữ liệu | NOT NULL |
| `periodTo` | `period_to` | `LocalDate` | Dữ liệu | NOT NULL |
| `status` | `status` | `String` | Dữ liệu | NOT NULL, length=30 |
| `createdBy` | `created_by` | `User` | FK → User | nullable theo mapping |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

Không tạo cột tại bảng này: `reconciliations`.

### Role → roles

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/Role.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `role_id` | `Long` | PK | NOT NULL |
| `code` | `code` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=50 |
| `name` | `role_name` | `String` | Dữ liệu | nullable theo mapping, length=100 |
| `description` | `description` | `String` | Dữ liệu | nullable theo mapping |
| `active` | `active` | `boolean` | Dữ liệu | NOT NULL |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |

Không tạo cột tại bảng này: `userRoles`, `rolePermissions`, `permissions`.

### RolePermission → role_permissions

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/RolePermission.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `role_permission_id` | `Long` | PK | NOT NULL |
| `role` | `role_id` | `Role` | FK → Role | NOT NULL |
| `permission` | `permission_id` | `Permission` | FK → Permission | NOT NULL |
| `assignedAt` | `assigned_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `assignedBy` | `assigned_by` | `User` | FK → User | nullable theo mapping |

Ràng buộc bảng: UNIQUE(role_id, permission_id).

### User → users

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/User.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `tokenVersion` | `token_version` | `Long` | Dữ liệu | NOT NULL |
| `rowVersion` | `row_version` | `Long` | Dữ liệu | NOT NULL, optimistic locking |
| `id` | `user_id` | `Long` | PK | NOT NULL |
| `company` | `company_id` | `Company` | FK → Company | nullable theo mapping |
| `username` | `username` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=100 |
| `password` | `password_hash` | `String` | Dữ liệu | NOT NULL |
| `fullName` | `full_name` | `String` | Dữ liệu | NOT NULL, length=150 |
| `email` | `email` | `String` | Dữ liệu | NOT NULL, UNIQUE, length=150 |
| `phone` | `phone` | `String` | Dữ liệu | nullable theo mapping |
| `avatar` | `avatar` | `String` | Dữ liệu | nullable theo mapping |
| `status` | `status` | `UserStatus` | Dữ liệu | NOT NULL, length=20, enum STRING |
| `createdAt` | `created_at` | `LocalDateTime` | Dữ liệu | NOT NULL |
| `updatedAt` | `updated_at` | `LocalDateTime` | Dữ liệu | NOT NULL |

Không tạo cột tại bảng này: `userRoles`, `roles`.

### UserRoleAssignment → user_roles

Nguồn: `backendqt/backendqt/src/main/java/com/example/invoice/entity/UserRoleAssignment.java`.

| Thuộc tính Java | Cột | Kiểu Java | Vai trò | Khai báo đáng chú ý |
|---|---|---|---|---|
| `id` | `user_role_id` | `Long` | PK | NOT NULL |
| `user` | `user_id` | `User` | FK → User | NOT NULL |
| `role` | `role_id` | `Role` | FK → Role | NOT NULL |
| `assignedAt` | `assigned_at` | `LocalDateTime` | Dữ liệu | nullable theo mapping |
| `assignedBy` | `assigned_by` | `User` | FK → User | nullable theo mapping |

Ràng buộc bảng: UNIQUE(user_id, role_id).
