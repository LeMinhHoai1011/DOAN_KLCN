package com.example.invoice.dto.document;
import jakarta.validation.constraints.NotBlank;
/** activeReviewMs: optional client-measured visible review time, only kept for reviewer decisions. */
public record ReviewActionRequest(@NotBlank String action, String note, Long activeReviewMs) {
	public ReviewActionRequest(String action, String note) { this(action, note, null); }
}
