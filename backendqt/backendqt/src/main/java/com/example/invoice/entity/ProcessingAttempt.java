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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "processing_attempts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"processing_job_id", "attempt_number"})
)
public class ProcessingAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "processing_attempt_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processing_job_id", nullable = false)
    private ProcessingJob processingJob;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false, length = 30)
    private String status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Column(columnDefinition = "text")
    private String errorMessage;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
