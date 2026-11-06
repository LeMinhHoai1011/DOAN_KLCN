package com.example.invoice.dto.classification;

import com.example.invoice.entity.ClassificationStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ClassificationUpdateRequest(
		String category,
		String documentType,
		@PositiveOrZero BigDecimal confidence,
		String reason,
		ClassificationStatus status,
		Boolean aiGenerated,
		/** Null keeps the current account, blank clears it. */
		@Pattern(regexp = "\\s*|\\s*[1-9][0-9]{2,9}\\s*", message = "Tài khoản kế toán phải gồm 3 đến 10 chữ số") String accountingAccount) {
	public ClassificationUpdateRequest(String category, String documentType, BigDecimal confidence, String reason,
			ClassificationStatus status, Boolean aiGenerated) {
		this(category, documentType, confidence, reason, status, aiGenerated, null);
	}
}
