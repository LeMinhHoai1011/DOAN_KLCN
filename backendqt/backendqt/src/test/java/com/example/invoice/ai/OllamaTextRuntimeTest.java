package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiTextRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OLLAMA_RUNTIME_MODEL", matches = ".+")
class OllamaTextRuntimeTest {
	@Test
	void realOllamaAcceptsTextWithoutImageAndReturnsStructuredJson() throws Exception {
		AiProperties properties = new AiProperties();
		properties.getOllama().setBaseUrl("http://localhost:11434");
		properties.getOllama().setModel(System.getenv("OLLAMA_RUNTIME_MODEL"));
		properties.getOllama().setThink(false);
		properties.setRequestTimeout(java.time.Duration.ofMinutes(2));
		OllamaAiProvider provider = new OllamaAiProvider(properties, new ObjectMapper(), new OkHttpClient());
		var response = provider.analyzeText(new AiTextRequest("runtime.txt",
				"[PAGE 1]\nINVOICE INV-2026-001 TOTAL 1100000 VND",
				"Return JSON only with this exact shape: {\"documentType\":\"INVOICE\",\"invoiceNumber\":\"INV-2026-001\",\"totalAmount\":1100000}"));
		System.out.println("RUNTIME_OLLAMA_TEXT=" + response.content());
		var json = new ObjectMapper().readTree(response.content());
		assertEquals("INVOICE", json.path("documentType").asText());
		assertEquals("INV-2026-001", json.path("invoiceNumber").asText());
		assertEquals(1100000, json.path("totalAmount").asInt());
	}
}
