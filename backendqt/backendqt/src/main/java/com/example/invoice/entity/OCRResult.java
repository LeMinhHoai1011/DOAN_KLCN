package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ocr_results")
public class OCRResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ocr_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	private String ocrEngine;
	private String modelVersion;

	@Lob
	@Column(nullable = false)
	private String rawText;

	@Column(precision = 5, scale = 2)
	private BigDecimal confidence;

	private Long processingTime;
	private String status;
	private LocalDateTime processedAt;

	@PrePersist
	void prePersist() {
		if (processedAt == null) {
			processedAt = LocalDateTime.now();
		}
	}
}
