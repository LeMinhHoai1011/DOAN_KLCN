# Danh sách Entity và cột dữ liệu

Tài liệu này tổng hợp các entity JPA trong thư mục `entity`, gồm tên file Java, tên bảng, field/cột, kiểu dữ liệu, quan hệ và mô tả tiếng Việt.

> Ghi chú: Các field quan hệ được ghi để mô tả liên kết giữa entity. Các field dạng `List<>` là collection, không tạo cột trực tiếp trong bảng hiện tại.

## 1. AccountingCategory

- **File:** `AccountingCategory.java`
- **Bảng:** `accounting_categories`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `category_id` | `Long` | `@Id`, `IDENTITY` | Mã danh mục kế toán |
| `company` / `company_id` | `Company` | `@ManyToOne`, `@JoinColumn` | Công ty sở hữu danh mục |
| `categoryCode` | `String` | `nullable=false`, `length=50` | Mã danh mục |
| `categoryName` | `String` | `nullable=false`, `length=200` | Tên danh mục |
| `description` | `String` | - | Mô tả danh mục |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | - | Thời điểm cập nhật |

## 2. AccountingEntry

- **File:** `AccountingEntry.java`
- **Bảng:** `accounting_entries`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `entry_id` | `Long` | `@Id`, `IDENTITY` | Mã bút toán |
| `document` / `document_id` | `Document` | `@ManyToOne`, `nullable=false` | Chứng từ liên quan |
| `category` / `category_id` | `AccountingCategory` | `@ManyToOne`, `nullable=false` | Danh mục kế toán |
| `createdBy` / `created_by` | `User` | `@ManyToOne` | Người tạo bút toán |
| `amount` | `BigDecimal` | `precision=19`, `scale=2` | Số tiền |
| `description` | `String` | - | Diễn giải bút toán |
| `entryDate` | `LocalDate` | - | Ngày ghi nhận |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | - | Thời điểm cập nhật |

## 3. Classification

- **File:** `Classification.java`
- **Bảng:** `ai_classifications`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `classification_id` | `Long` | `@Id`, `IDENTITY` | Mã kết quả phân loại |
| `document` / `document_id` | `Document` | `@ManyToOne`, `nullable=false` | Chứng từ được phân loại |
| `accountingCategory` / `category_id` | `AccountingCategory` | `@ManyToOne` | Danh mục kế toán được dự đoán/chọn |
| `modelName` | `String` | - | Tên mô hình AI |
| `modelVersion` | `String` | - | Phiên bản mô hình |
| `predictedLabel` | `String` | - | Nhãn dự đoán |
| `category` | `String` | - | Tên hoặc mã nhóm dự đoán |
| `confidence` | `BigDecimal` | `precision=5`, `scale=2` | Độ tin cậy dự đoán |
| `reason` | `String` | `@Lob` | Lý do hoặc giải thích của AI |
| `status` | `ClassificationStatus` | `@Enumerated(STRING)`, `length=30` | Trạng thái phân loại |
| `aiGenerated` | `boolean` | `nullable=false` | Có phải kết quả do AI tạo không |
| `createdAt` | `LocalDateTime` | `nullable=false`, `updatable=false` | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | `nullable=false` | Thời điểm cập nhật |

## 4. ClassificationCorrection

- **File:** `ClassificationCorrection.java`
- **Bảng:** `classification_correction`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `correction_id` | `Long` | `@Id`, `IDENTITY` | Mã lần sửa phân loại |
| `classification` / `classification_id` | `Classification` | `@ManyToOne`, `nullable=false` | Kết quả phân loại bị sửa |
| `review` / `review_id` | `DocumentReview` | `@ManyToOne` | Lần review liên quan |
| `correctedBy` / `corrected_by` | `User` | `@ManyToOne` | Người sửa |
| `oldCategory` / `old_category_id` | `AccountingCategory` | `@ManyToOne` | Danh mục cũ |
| `newCategory` / `new_category_id` | `AccountingCategory` | `@ManyToOne` | Danh mục mới |
| `reason` | `String` | - | Lý do sửa |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo bản ghi sửa |

## 5. Company

- **File:** `Company.java`
- **Bảng:** `companies`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `company_id` | `Long` | `@Id`, `IDENTITY` | Mã công ty |
| `companyName` | `String` | `nullable=false`, `length=200` | Tên công ty |
| `taxCode` | `String` | `unique=true`, `length=50` | Mã số thuế |
| `address` | `String` | - | Địa chỉ |
| `phone` | `String` | - | Số điện thoại |
| `email` | `String` | - | Email |
| `users` | `List<User>` | `@OneToMany(mappedBy="company")` | Danh sách người dùng; không tạo cột trực tiếp |
| `accountingCategories` | `List<AccountingCategory>` | `@OneToMany(mappedBy="company")` | Danh sách danh mục kế toán; không tạo cột trực tiếp |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | - | Thời điểm cập nhật |

## 6. Document

- **File:** `Document.java`
- **Bảng:** `documents`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `document_id` | `Long` | `@Id`, `IDENTITY` | Mã chứng từ |
| `company` / `company_id` | `Company` | `@ManyToOne` | Công ty sở hữu |
| `documentType` | `String` | `length=50` | Loại chứng từ |
| `originalFileName` | `String` | `nullable=false` | Tên file gốc |
| `fileName` | `String` | `length=255` | Tên file lưu trữ |
| `fileType` / `mime_type` | `String` | `nullable=false`, `length=100` | MIME type |
| `fileSize` | `Long` | `nullable=false` | Kích thước file |
| `filePath` / `object_key` | `String` | - | Đường dẫn hoặc object key trong MinIO |
| `status` / `processing_status` | `DocumentStatus` | `@Enumerated(STRING)`, `nullable=false`, `length=30` | Trạng thái xử lý |
| `reviewStatus` / `review_status` | `ReviewStatus` | `@Enumerated(STRING)`, `nullable=false`, `length=30` | Trạng thái kiểm tra |
| `uploadedBy` / `uploaded_by` | `User` | `@ManyToOne` | Người upload |
| `invoice` | `Invoice` | `@OneToOne(mappedBy="document")` | Dữ liệu hóa đơn tương ứng; không tạo cột trực tiếp |
| `versions` | `List<DocumentVersion>` | `@OneToMany(mappedBy="document")` | Lịch sử phiên bản; không tạo cột trực tiếp |
| `ocrResults` | `List<OCRResult>` | `@OneToMany(mappedBy="document")` | Các kết quả OCR; không tạo cột trực tiếp |
| `classifications` | `List<Classification>` | `@OneToMany(mappedBy="document")` | Các kết quả phân loại; không tạo cột trực tiếp |
| `reviews` | `List<DocumentReview>` | `@OneToMany(mappedBy="document")` | Lịch sử review; không tạo cột trực tiếp |
| `processingLogs` | `List<ProcessingLog>` | `@OneToMany(mappedBy="document")` | Log xử lý; không tạo cột trực tiếp |
| `accountingEntries` | `List<AccountingEntry>` | `@OneToMany(mappedBy="document")` | Các bút toán liên quan; không tạo cột trực tiếp |
| `createdAt` | `LocalDateTime` | `nullable=false`, `updatable=false` | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | `nullable=false` | Thời điểm cập nhật |

## 7. DocumentReview

- **File:** `DocumentReview.java`
- **Bảng:** `document_reviews`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `review_id` | `Long` | `@Id`, `IDENTITY` | Mã lần review |
| `document` / `document_id` | `Document` | `@ManyToOne`, `nullable=false` | Chứng từ được review |
| `reviewer` / `reviewer_id` | `User` | `@ManyToOne` | Người review |
| `reviewType` | `ReviewType` | `@Enumerated(STRING)`, `nullable=false`, `length=30` | Loại review |
| `reviewStatus` | `ReviewStatus` | `@Enumerated(STRING)`, `nullable=false`, `length=30` | Trạng thái review |
| `reviewNote` | `String` | - | Ghi chú review |
| `reviewedAt` | `LocalDateTime` | - | Thời điểm review |
| `fieldCorrections` | `List<FieldCorrection>` | `@OneToMany(mappedBy="review")` | Các sửa đổi field; không tạo cột trực tiếp |
| `classificationCorrections` | `List<ClassificationCorrection>` | `@OneToMany(mappedBy="review")` | Các sửa đổi phân loại; không tạo cột trực tiếp |

## 8. DocumentVersion

- **File:** `DocumentVersion.java`
- **Bảng:** `document_versions`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `version_id` | `Long` | `@Id`, `IDENTITY` | Mã phiên bản |
| `document` / `document_id` | `Document` | `@ManyToOne`, `nullable=false` | Chứng từ |
| `createdBy` / `created_by` | `User` | `@ManyToOne` | Người tạo phiên bản |
| `fileName` | `String` | - | Tên file phiên bản |
| `objectKey` | `String` | - | Object key lưu trữ |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo |

## 9. ExtractedField

- **File:** `ExtractedField.java`
- **Bảng:** `extracted_fields`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `field_id` | `Long` | `@Id`, `IDENTITY` | Mã field trích xuất |
| `invoice` / `invoice_id` | `Invoice` | `@ManyToOne`, `nullable=false` | Hóa đơn chứa field |
| `fieldName` | `String` | `nullable=false`, `length=100` | Tên field |
| `fieldValue` | `String` | `columnDefinition=text` | Giá trị field |
| `source` | `String` | - | Nguồn dữ liệu |
| `confidence` | `BigDecimal` | `precision=5`, `scale=2` | Độ tin cậy trích xuất |
| `corrections` | `List<FieldCorrection>` | `@OneToMany(mappedBy="field")` | Lịch sử chỉnh sửa field; không tạo cột trực tiếp |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | - | Thời điểm cập nhật |

## 10. FieldCorrection

- **File:** `FieldCorrection.java`
- **Bảng:** `field_corrections`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `correction_id` | `Long` | `@Id`, `IDENTITY` | Mã lần sửa field |
| `field` / `field_id` | `ExtractedField` | `@ManyToOne`, `nullable=false` | Field bị sửa |
| `review` / `review_id` | `DocumentReview` | `@ManyToOne` | Lần review liên quan |
| `correctedBy` / `corrected_by` | `User` | `@ManyToOne` | Người sửa |
| `oldValue` | `String` | `columnDefinition=text` | Giá trị cũ |
| `newValue` | `String` | `columnDefinition=text` | Giá trị mới |
| `reason` | `String` | - | Lý do sửa |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo bản ghi sửa |

## 11. Invoice

- **File:** `Invoice.java`
- **Bảng:** `invoice_data`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `invoice_id` | `Long` | `@Id`, `IDENTITY` | Mã hóa đơn |
| `document` / `document_id` | `Document` | `@OneToOne`, `nullable=false`, `unique=true` | Chứng từ nguồn |
| `invoiceNumber` | `String` | - | Số hóa đơn |
| `invoiceSeries` | `String` | - | Ký hiệu hóa đơn |
| `invoiceDate` | `LocalDate` | - | Ngày hóa đơn |
| `sellerName` | `String` | - | Tên bên bán |
| `sellerTaxCode` | `String` | - | Mã số thuế bên bán |
| `sellerAddress` | `String` | - | Địa chỉ bên bán |
| `buyerName` | `String` | - | Tên bên mua |
| `buyerTaxCode` | `String` | - | Mã số thuế bên mua |
| `buyerAddress` | `String` | - | Địa chỉ bên mua |
| `subtotal` / `total_before_tax` | `BigDecimal` | `precision=19`, `scale=2` | Tổng tiền trước thuế |
| `vatAmount` / `tax_amount` | `BigDecimal` | `precision=19`, `scale=2` | Tiền thuế VAT |
| `totalAmount` | `BigDecimal` | `precision=19`, `scale=2` | Tổng tiền thanh toán |
| `items` | `List<InvoiceItem>` | `@OneToMany(mappedBy="invoice")` | Các dòng hàng hóa; không tạo cột trực tiếp |
| `extractedFields` | `List<ExtractedField>` | `@OneToMany(mappedBy="invoice")` | Các field được trích xuất; không tạo cột trực tiếp |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | - | Thời điểm cập nhật |

## 12. InvoiceItem

- **File:** `InvoiceItem.java`
- **Bảng:** `invoice_items`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `item_id` | `Long` | `@Id`, `IDENTITY` | Mã dòng hóa đơn |
| `invoice` / `invoice_id` | `Invoice` | `@ManyToOne`, `nullable=false` | Hóa đơn chứa dòng này |
| `productName` / `item_name` | `String` | `nullable=false` | Tên hàng hóa/dịch vụ |
| `quantity` | `BigDecimal` | `precision=19`, `scale=2` | Số lượng |
| `unit` | `String` | - | Đơn vị tính |
| `unitPrice` | `BigDecimal` | `precision=19`, `scale=2` | Đơn giá |
| `taxRate` | `BigDecimal` | `precision=5`, `scale=2` | Thuế suất |
| `taxAmount` | `BigDecimal` | `precision=19`, `scale=2` | Tiền thuế |
| `amount` / `total_amount` | `BigDecimal` | `precision=19`, `scale=2` | Thành tiền |

## 13. OCRResult

- **File:** `OCRResult.java`
- **Bảng:** `ocr_results`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `ocr_id` | `Long` | `@Id`, `IDENTITY` | Mã kết quả OCR |
| `document` / `document_id` | `Document` | `@ManyToOne`, `nullable=false` | Chứng từ được OCR |
| `ocrEngine` | `String` | - | Tên engine OCR |
| `modelVersion` | `String` | - | Phiên bản mô hình OCR |
| `rawText` | `String` | `@Lob`, `nullable=false` | Văn bản OCR thô |
| `confidence` | `BigDecimal` | `precision=5`, `scale=2` | Độ tin cậy OCR |
| `processingTime` | `Long` | - | Thời gian xử lý |
| `status` | `String` | - | Trạng thái OCR |
| `processedAt` | `LocalDateTime` | - | Thời điểm xử lý |

## 14. ProcessingLog

- **File:** `ProcessingLog.java`
- **Bảng:** `processing_logs`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `log_id` | `Long` | `@Id`, `IDENTITY` | Mã log |
| `document` / `document_id` | `Document` | `@ManyToOne`, `nullable=false` | Chứng từ được xử lý |
| `processType` | `String` | - | Loại quy trình |
| `agentStep` | `String` | - | Bước xử lý hoặc agent |
| `modelName` | `String` | - | Tên mô hình |
| `modelVersion` | `String` | - | Phiên bản mô hình |
| `status` | `String` | - | Trạng thái xử lý |
| `confidence` | `BigDecimal` | `precision=5`, `scale=2` | Độ tin cậy |
| `executionTime` | `Long` | - | Thời gian thực thi |
| `message` | `String` | - | Thông báo hoặc chi tiết log |
| `createdAt` | `LocalDateTime` | - | Thời điểm tạo log |

## 15. Role

- **File:** `Role.java`
- **Bảng:** `roles`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `role_id` | `Long` | `@Id`, `IDENTITY` | Mã vai trò |
| `roleName` | `String` | `nullable=false`, `unique=true`, `length=100` | Tên vai trò |
| `users` | `List<User>` | `@OneToMany(mappedBy="roleRef")` | Người dùng có vai trò này; không tạo cột trực tiếp |

## 16. User

- **File:** `User.java`
- **Bảng:** `users`

| Field / cột | Kiểu Java | Cấu hình / quan hệ | Mô tả tiếng Việt |
|---|---|---|---|
| `id` / `user_id` | `Long` | `@Id`, `IDENTITY` | Mã người dùng |
| `company` / `company_id` | `Company` | `@ManyToOne` | Công ty của người dùng |
| `roleRef` / `role_id` | `Role` | `@ManyToOne` | Đối tượng vai trò liên kết |
| `username` | `String` | `nullable=false`, `unique=true`, `length=100` | Tên đăng nhập |
| `password` / `password_hash` | `String` | `nullable=false` | Mật khẩu đã băm |
| `fullName` | `String` | `nullable=false`, `length=150` | Họ tên |
| `email` | `String` | `nullable=false`, `unique=true`, `length=150` | Email |
| `phone` | `String` | - | Số điện thoại |
| `avatar` | `String` | - | Đường dẫn hoặc định danh ảnh đại diện |
| `role` | `UserRole` | `@Enumerated(STRING)`, `nullable=false`, `length=20` | Vai trò nghiệp vụ |
| `status` | `UserStatus` | `@Enumerated(STRING)`, `nullable=false`, `length=20` | Trạng thái tài khoản |
| `createdAt` | `LocalDateTime` | `nullable=false`, `updatable=false` | Thời điểm tạo |
| `updatedAt` | `LocalDateTime` | `nullable=false` | Thời điểm cập nhật |

## Enum hỗ trợ

| Enum | File Java | Giá trị | Ý nghĩa tiếng Việt |
|---|---|---|---|
| `ClassificationStatus` | `ClassificationStatus.java` | `PENDING`, `ACCEPTED`, `NEED_REVIEW`, `CORRECTED`, `CLASSIFIED`, `REVIEW_REQUIRED`, `VERIFIED` | Trạng thái phân loại |
| `DocumentStatus` | `DocumentStatus.java` | `UPLOADED`, `PROCESSING`, `PROCESSED`, `NEED_REVIEW`, `COMPLETED`, `FAILED` | Trạng thái xử lý chứng từ |
| `ReviewStatus` | `ReviewStatus.java` | `PENDING`, `APPROVED`, `REJECTED`, `CORRECTED` | Trạng thái review |
| `ReviewType` | `ReviewType.java` | `OCR`, `EXTRACTION`, `CLASSIFICATION`, `FULL_REVIEW` | Loại review |
| `UserRole` | `UserRole.java` | `ADMIN`, `USER` | Vai trò người dùng |
| `UserStatus` | `UserStatus.java` | `ACTIVE`, `INACTIVE` | Trạng thái người dùng |

## Tổng hợp

- **16 JPA entity**
- **6 enum hỗ trợ**
- Không có quan hệ `@ManyToMany`
- `package-info.java` không khai báo entity, field hoặc enum
