package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import org.junit.jupiter.api.Test;

class AiDocumentResultParserTest {
	@Test
	void parsesJsonWrappedInMarkdownFence() {
		AiDocumentResult result = AiDocumentResultParser.parse("ollama", "vision", """
			```json
			{"documentType":"INVOICE","classificationConfidence":0.91,"rawText":"text","invoice":null,"fields":[],"warnings":[]}
			```
			""", "raw", 12L);

		assertEquals("INVOICE", result.documentType());
		assertEquals("ollama", result.provider());
	}

	@Test
	void rejectsMalformedJson() {
		assertThrows(AiProviderException.class,
				() -> AiDocumentResultParser.parse("ollama", "vision", "not-json", "raw", 1L));
	}
}
