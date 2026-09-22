package com.example.invoice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    // =========================
    // THÔNG TIN CÔNG TY
    // =========================

    /**
     * Công ty sở hữu chứng từ.
     * Một Company có thể có nhiều Document.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;


    // =========================
    // THÔNG TIN FILE
    // =========================

    /**
     * Loại chứng từ:
     * - INVOICE
     * - RECEIPT
     * - PAYMENT_VOUCHER
     * - RECEIPT_VOUCHER
     * - OTHER
     */
    @Column(length = 50)
    private String documentType;

    /**
     * Tên file gốc do người dùng upload.
     *
     * Ví dụ:
     * HoaDon_Thang09.pdf
     */
    @Column(nullable = false)
    private String originalFileName;

    /**
     * Tên file được hệ thống lưu trữ.
     *
     * Có thể là UUID để tránh trùng tên.
     *
     * Ví dụ:
     * 8f72a1c4-xxxx-xxxx.pdf
     */
    @Column(length = 255)
    private String fileName;

    /**
     * MIME type của file.
     *
     * Ví dụ:
     * application/pdf
     * image/jpeg
     * image/png
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String fileType;

    /**
     * Kích thước file tính bằng byte.
     */
    @Column(nullable = false)
    private Long fileSize;

    /**
     * Object key / đường dẫn file trong MinIO.
     *
     * Ví dụ:
     * documents/2026/09/8f72a1c4.pdf
     */
    @Column(name = "object_key")
    private String filePath;


    // =========================
    // TRẠNG THÁI XỬ LÝ
    // =========================

    /**
     * Trạng thái xử lý kỹ thuật của hệ thống.
     *
     * UPLOADED
     * PROCESSING
     * COMPLETED
     * FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "processing_status",
        nullable = false,
        length = 30
    )
    private DocumentStatus status = DocumentStatus.UPLOADED;


    // =========================
    // TRẠNG THÁI KIỂM TRA
    // =========================

    /**
     * Trạng thái người dùng kiểm tra kết quả OCR / AI.
     *
     * PENDING
     * REVIEWING
     * APPROVED
     * REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "review_status",
        nullable = false,
        length = 30
    )
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;


    // =========================
    // NGƯỜI UPLOAD
    // =========================

    /**
     * Người dùng upload chứng từ.
     * Một User có thể upload nhiều Document.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;


    // =========================
    // HÓA ĐƠN
    // =========================

    /**
     * Một Document có thể tương ứng với một Invoice.
     *
     * Ví dụ:
     * Document = file PDF hóa đơn
     * Invoice = dữ liệu hóa đơn được AI/OCR trích xuất.
     */
    @OneToOne(
        mappedBy = "document",
        fetch = FetchType.LAZY
    )
    private Invoice invoice;


    // =========================
    // LỊCH SỬ PHIÊN BẢN
    // =========================

    /**
     * Lưu các phiên bản của chứng từ.
     *
     * Ví dụ:
     * Version 1 → OCR ban đầu
     * Version 2 → User chỉnh sửa
     * Version 3 → User xác nhận
     */
    @OneToMany(mappedBy = "document")
    private List<DocumentVersion> versions = new ArrayList<>();


    // =========================
    // KẾT QUẢ OCR
    // =========================

    /**
     * Lưu kết quả OCR của Document.
     *
     * Có thể có nhiều kết quả nếu:
     * - OCR lại
     * - thay đổi engine OCR
     * - xử lý nhiều lần.
     */
    @OneToMany(mappedBy = "document")
    private List<OCRResult> ocrResults = new ArrayList<>();


    // =========================
    // KẾT QUẢ PHÂN LOẠI
    // =========================

    /**
     * Lưu kết quả phân loại chứng từ/chi phí.
     *
     * Ví dụ:
     * - Văn phòng phẩm
     * - Ăn uống
     * - Vận chuyển
     * - Nguyên vật liệu
     */
    @OneToMany(mappedBy = "document")
    private List<Classification> classifications = new ArrayList<>();


    // =========================
    // LỊCH SỬ REVIEW
    // =========================

    /**
     * Lịch sử người dùng kiểm tra/chỉnh sửa chứng từ.
     */
    @OneToMany(mappedBy = "document")
    private List<DocumentReview> reviews = new ArrayList<>();


    // =========================
    // LOG XỬ LÝ
    // =========================

    /**
     * Lịch sử các bước hệ thống xử lý Document.
     *
     * Ví dụ:
     * UPLOAD
     * OCR
     * AI_EXTRACTION
     * CLASSIFICATION
     * VALIDATION
     */
    @OneToMany(mappedBy = "document")
    private List<ProcessingLog> processingLogs = new ArrayList<>();


    // =========================
    // BÚT TOÁN KẾ TOÁN
    // =========================

    /**
     * Các bút toán kế toán liên quan đến Document.
     */
    @OneToMany(mappedBy = "document")
    private List<AccountingEntry> accountingEntries = new ArrayList<>();


    // =========================
    // THỜI GIAN
    // =========================

    /**
     * Thời gian tạo Document.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Thời gian cập nhật Document gần nhất.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    // =========================
    // JPA LIFECYCLE
    // =========================

    /**
     * Tự động chạy trước khi INSERT.
     */
    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = DocumentStatus.UPLOADED;
        }

        if (reviewStatus == null) {
            reviewStatus = ReviewStatus.PENDING;
        }
    }

    /**
     * Tự động chạy trước khi UPDATE.
     */
    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}