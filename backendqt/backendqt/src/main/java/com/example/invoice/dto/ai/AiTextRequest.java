package com.example.invoice.dto.ai;

/** Provider-neutral text payload. Page markers must be preserved in text. */
public record AiTextRequest(String fileName, String text, String prompt) {
}
