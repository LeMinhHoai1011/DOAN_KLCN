package com.example.invoice.dto.ai;

/**
 * Normalized provider result. rawResponse is preserved only for the caller's
 * test display and must not be persisted in Phase 1.
 */
public record AiProviderResponse(
		String provider,
		String model,
		String content,
		String rawResponse,
		long durationMs) {
}
