package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Result of independent checks on one extracted field (or one cell of an invoice line).
 * Evidence and rule outcomes are stored as JSON so the reason for every status can be audited.
 * Never derived from the model's self-reported confidence.
 */
@Getter
@Setter
@Entity
@Table(name = "field_verifications", indexes = @Index(name = "idx_field_verifications_document", columnList = "document_id"))
public class FieldVerification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "verification_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	@Column(name = "field_key", nullable = false, length = 120)
	private String fieldKey;

	@Column(name = "item_index")
	private Integer itemIndex;

	@Column(name = "is_core", nullable = false)
	private boolean core;

	/** VERIFIED, WARNING, UNVERIFIED or MISSING. */
	@Column(nullable = false, length = 20)
	private String status;

	/** Uncalibrated heuristic 0–100; null when no check applied. */
	private Integer score;

	@Column(name = "checked_value", columnDefinition = "text")
	private String checkedValue;

	/** Value as first produced by AI, kept so later manual edits can be told apart. */
	@Column(name = "ai_value", columnDefinition = "text")
	private String aiValue;

	@Column(name = "value_origin", nullable = false, length = 10)
	private String valueOrigin;

	@Column(name = "ai_input_mode", nullable = false, length = 10)
	private String aiInputMode;

	@Column(name = "evidence_json", columnDefinition = "text")
	private String evidenceJson;

	@Column(name = "rules_json", nullable = false, columnDefinition = "text")
	private String rulesJson;

	@Column(name = "reasons_json", nullable = false, columnDefinition = "text")
	private String reasonsJson;

	@Column(name = "rule_version", nullable = false, length = 40)
	private String ruleVersion;

	@Column(name = "trigger_source", nullable = false, length = 30)
	private String triggerSource;

	@Column(name = "verified_at", nullable = false)
	private LocalDateTime verifiedAt;
}
