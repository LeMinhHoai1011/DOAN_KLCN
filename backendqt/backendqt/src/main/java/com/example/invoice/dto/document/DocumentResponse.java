package com.example.invoice.dto.document;

import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ReviewStatus;
import java.time.LocalDateTime;

public record DocumentResponse(
		Long id,
		String originalFileName,
		String fileType,
		Long fileSize,
		String filePath,
		DocumentStatus status,
		ReviewStatus reviewStatus,
		Long companyId,
		Long typeId,
		String documentType,
		CompanyRoleResponse companyRole,
		String documentDirection,
		TransactionAssessmentResponse transactionAssessment,
		Long uploadedById,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
