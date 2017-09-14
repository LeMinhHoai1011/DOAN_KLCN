package com.example.invoice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "classification_correction")
public class ClassificationCorrection {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "correction_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "classification_id", nullable = false)
	private Classification classification;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_id")
	private DocumentReview review;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "corrected_by")
	private User correctedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "old_category_id")
	private AccountingCategory oldCategory;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "new_category_id")
	private AccountingCategory newCategory;

	private String reason;
	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}
}
