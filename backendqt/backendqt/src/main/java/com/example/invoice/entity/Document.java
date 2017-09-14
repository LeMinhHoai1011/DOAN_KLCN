package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "document_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id")
	private Company company;

	@Column(length = 50)
	private String documentType;

	@Column(nullable = false)
	private String originalFileName;

	@Column(length = 255)
	private String fileName;

	@Column(name = "mime_type", nullable = false, length = 100)
	private String fileType;

	@Column(nullable = false)
	private Long fileSize;

	@Column(name = "object_key")
	private String filePath;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 30)
	private DocumentStatus status = DocumentStatus.UPLOADED;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false, length = 30)
	private ReviewStatus reviewStatus = ReviewStatus.PENDING;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploaded_by")
	private User uploadedBy;

	@OneToOne(mappedBy = "document", fetch = FetchType.LAZY)
	private Invoice invoice;

	@OneToMany(mappedBy = "document")
	private List<DocumentVersion> versions = new ArrayList<>();

	@OneToMany(mappedBy = "document")
	private List<OCRResult> ocrResults = new ArrayList<>();

	@OneToMany(mappedBy = "document")
	private List<Classification> classifications = new ArrayList<>();

	@OneToMany(mappedBy = "document")
	private List<DocumentReview> reviews = new ArrayList<>();

	@OneToMany(mappedBy = "document")
	private List<ProcessingLog> processingLogs = new ArrayList<>();

	@OneToMany(mappedBy = "document")
	private List<AccountingEntry> accountingEntries = new ArrayList<>();

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
