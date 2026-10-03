package com.example.invoice.dto.ai;

/**
 * Provider-neutral image payload. The controller introduced in a later phase
 * will create this object after multipart validation.
 */
public record AiImageRequest(
		String fileName,
		String contentType,
		byte[] imageBytes,
		String prompt) {
}
