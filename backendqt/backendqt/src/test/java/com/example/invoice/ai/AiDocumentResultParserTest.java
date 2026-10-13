package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

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

	@Test
	void parsesMultipleCompleteInvoiceItems() {
		AiDocumentResult result = AiDocumentResultParser.parse("ollama", "qwen3-vl", """
			{"documentType":"VAT_INVOICE","classificationConfidence":0.99,"invoice":{"items":[
			{"productName":"Ghế xoay","quantity":6,"unit":"cái","unitPrice":250000,"taxRate":0.08,"taxAmount":120000,"amount":1500000},
			{"productName":"Gương sấy treo","quantity":7,"unit":"cái","unitPrice":700000,"taxRate":0.08,"taxAmount":392000,"amount":4900000}
			]},"fields":[],"warnings":[]}
			""", "raw", 9L);

		assertEquals(2, result.invoice().items().size());
		var first = result.invoice().items().getFirst();
		assertEquals("Ghế xoay", first.productName());
		assertEquals(new BigDecimal("6"), first.quantity());
		assertEquals("cái", first.unit());
		assertEquals(new BigDecimal("250000"), first.unitPrice());
		assertEquals(new BigDecimal("0.08"), first.taxRate());
		assertEquals(new BigDecimal("120000"), first.taxAmount());
		assertEquals(new BigDecimal("1500000"), first.amount());
	}

	@Test
	void acceptsDescriptionAndItemNameAliasesWithoutLoggingTheirValues() {
		Logger logger = (Logger) LoggerFactory.getLogger(AiDocumentResultParser.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			AiDocumentResult result = AiDocumentResultParser.parse("ollama", "qwen3-vl", """
				{"documentType":"VAT_INVOICE","invoice":{"items":[
				{"description":"SENSITIVE_PRODUCT_NAME","quantity":1,"unitPrice":100,"amount":100},
				{"itemName":"ANOTHER_PRIVATE_NAME","quantity":1,"unitPrice":200,"amount":200}
				]},"fields":[],"warnings":[]}
				""", "raw", 1L);

			assertEquals(2, result.invoice().items().size());
			assertEquals("SENSITIVE_PRODUCT_NAME", result.invoice().items().get(0).productName());
			assertEquals("ANOTHER_PRIVATE_NAME", result.invoice().items().get(1).productName());
			String event = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
					.filter(message -> message.startsWith("AI_ITEM_JSON_SHAPE")).findFirst().orElseThrow();
			assertTrue(event.contains("itemsKeyPresent=true"));
			assertTrue(event.contains("productNameKeyCount=0"));
			assertTrue(event.contains("descriptionKeyCount=1"));
			assertTrue(event.contains("itemNameKeyCount=1"));
			assertFalse(event.contains("SENSITIVE_PRODUCT_NAME"));
			assertFalse(event.contains("ANOTHER_PRIVATE_NAME"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void acceptsInvoiceItemsAndCommonProductNameAliases() {
		AiDocumentResult result = AiDocumentResultParser.parse("ollama", "qwen3-vl", """
			{"documentType":"VAT_INVOICE","invoice":{"invoiceItems":[
			{"name":"Ghế xoay","quantity":6,"unit":"cái","unitPrice":250000,"amount":1500000},
			{"productDescription":"Gương sấy treo","quantity":7,"unit":"cái","unitPrice":700000,"amount":4900000}
			]},"fields":[],"warnings":[]}
			""", "raw", 2L);

		assertEquals(2, result.invoice().items().size());
		assertEquals("Ghế xoay", result.invoice().items().get(0).productName());
		assertEquals(new BigDecimal("6"), result.invoice().items().get(0).quantity());
		assertEquals("Gương sấy treo", result.invoice().items().get(1).productName());
	}
}
