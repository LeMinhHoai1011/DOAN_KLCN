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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "document_reviews")
public class DocumentReview {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "review_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewer_id")
	private User reviewer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReviewType reviewType = ReviewType.FULL_REVIEW;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReviewStatus reviewStatus = ReviewStatus.PENDING;

	private String reviewNote;
	private LocalDateTime reviewedAt;

	@OneToMany(mappedBy = "review")
	private List<FieldCorrection> fieldCorrections = new ArrayList<>();

	@OneToMany(mappedBy = "review")
	private List<ClassificationCorrection> classificationCorrections = new ArrayList<>();
}
