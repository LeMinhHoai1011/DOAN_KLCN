package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses a complete provider response without attempting to recover partial JSON. */
public final class AiDocumentResultParser {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(AiDocumentResultParser.class);

	private AiDocumentResultParser() {
	}

	public static AiDocumentResult parse(String provider, String model, String content, String rawResponse, long durationMs) {
		try {
			return OBJECT_MAPPER.readValue(stripFence(content), AiDocumentResult.class)
					.withMetadata(provider, model, rawResponse, durationMs);
		} catch (JsonProcessingException exception) {
			log.warn("AI_JSON_PARSE_FAILED provider={} model={} errorType={} location={}", provider, model,
					exception.getClass().getSimpleName(), exception.getLocation());
			throw new AiProviderException("AI_JSON_PARSE_ERROR: nhà cung cấp không trả về JSON có cấu trúc hợp lệ", exception);
		}
	}

	private static String stripFence(String content) {
		String value = content == null ? "" : content.trim();
		if (!value.startsWith("```")) {
			return value;
		}
		int firstLineEnd = value.indexOf('\n');
		if (firstLineEnd < 0 || !value.endsWith("```")) {
			return value;
		}
		return value.substring(firstLineEnd + 1, value.length() - 3).trim();
	}
}
