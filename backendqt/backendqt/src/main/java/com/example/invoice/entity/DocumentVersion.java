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
@Table(name = "document_versions")
public class DocumentVersion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "version_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
	
	@Column(name = "version_number", nullable = false)
	private Integer versionNumber;

	@Column(name = "original_filename")
	private String originalFileName;

	@Column(name = "content_type")
	private String contentType;
	
	@Column(name = "file_size")
	private Long fileSize;

	private String fileName;
	private String objectKey;
	
	@Column(updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}
}
