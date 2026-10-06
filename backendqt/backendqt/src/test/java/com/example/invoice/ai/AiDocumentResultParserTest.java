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

	@Test
	void parsesVatInvoiceCoreFieldsWithVietnameseUnicode() {
		AiDocumentResult result = AiDocumentResultParser.parse("ollama", "qwen3-vl", """
			{"documentType":"VAT_INVOICE","classificationConfidence":0.96,"invoice":{"sellerPhone":"028 1234 5678","paymentMethod":"Tiền mặt/Chuyển khoản","amountInWords":"Mười lăm triệu chín trăm tám mươi bốn nghìn đồng.","taxAuthorityCode":"CQT-AB12-0099","signDate":"2026-09-25","items":[]},"fields":[],"warnings":[]}
			""", "raw", 4L);

		assertEquals("028 1234 5678", result.invoice().sellerPhone());
		assertEquals("Tiền mặt/Chuyển khoản", result.invoice().paymentMethod());
		assertEquals("Mười lăm triệu chín trăm tám mươi bốn nghìn đồng.", result.invoice().amountInWords());
		assertEquals("CQT-AB12-0099", result.invoice().taxAuthorityCode());
		assertEquals("2026-09-25", result.invoice().signDate());
	}
}
