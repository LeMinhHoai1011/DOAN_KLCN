package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "field_corrections")
public class FieldCorrection {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "correction_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "field_id", nullable = false)
	private ExtractedField field;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_id")
	private DocumentReview review;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "corrected_by")
	private User correctedBy;

	@Column(columnDefinition = "text")
	private String oldValue;

	@Column(columnDefinition = "text")
	private String newValue;

	private String reason;
	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}
}
