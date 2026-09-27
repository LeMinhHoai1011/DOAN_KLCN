package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

class OllamaAiProviderTest {
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final OllamaAiProvider provider = new OllamaAiProvider(new AiProperties(), objectMapper, new OkHttpClient());

	@Test
	void prefersTheOfficialResponseField() throws Exception {
		assertEquals("{\"documentType\":\"INVOICE\"}", provider.selectGeneratedContent(
				objectMapper.readTree("{\"response\":\"{\\\"documentType\\\":\\\"INVOICE\\\"}\",\"thinking\":\"{\\\"ignored\\\":true}\"}")));
	}

	@Test
	void safelyFallsBackToThinkingJsonWhenResponseIsEmpty() throws Exception {
		assertEquals("{\"documentType\":\"INVOICE\"}", provider.selectGeneratedContent(
				objectMapper.readTree("{\"response\":\"\",\"thinking\":\"{\\\"documentType\\\":\\\"INVOICE\\\"}\"}")));
	}

	@Test
	void rejectsNonJsonThinkingWhenResponseIsEmpty() throws Exception {
		assertThrows(AiProviderException.class, () -> provider.selectGeneratedContent(
				objectMapper.readTree("{\"response\":\"\",\"thinking\":\"reasoning text\"}")));
	}
}
