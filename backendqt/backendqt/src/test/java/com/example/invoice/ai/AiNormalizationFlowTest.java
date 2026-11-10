package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.dto.ai.AiDocumentResult;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Model JSON → parser → validator, as the pipeline runs it, for the formats seen on real documents. */
class AiNormalizationFlowTest {
	private final AiDocumentResultValidator validator = new AiDocumentResultValidator();
	private static final Set<String> TYPES = Set.of("VAT_INVOICE", "RETAIL_INVOICE", "RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "OTHER");

	private static AiDocumentResult parse(String json) {
		return AiDocumentResultParser.parse("ollama", "qwen3-vl", json, "raw", 1L);
	}

	private static AiDocumentResult.AiFieldNote note(AiDocumentResult result, String field, Integer row) {
		return result.notes().stream().filter(n -> field.equals(n.field()) && java.util.Objects.equals(row, n.row()))
				.findFirst().orElseThrow(() -> new AssertionError("no note for " + field + " row " + row + ": " + result.notes()));
	}

	@Test
	void vatInvoiceWithKctRowsHasNoMissingVatAndKeepsKctApartFromZero() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.9,"invoice":{"invoiceNumber":"0000123","invoiceSeries":"1C25TAA",
			"invoiceDate":"15/03/2025","sellerName":"Công ty A","sellerTaxCode":"0100109106","subtotal":"200.000","vatAmount":null,
			"totalAmount":"200.000","items":[
			{"productName":"Sách giáo khoa","quantity":2,"unit":"Cuốn","unitPrice":"50.000","taxRate":"KCT","amount":"100.000"},
			{"productName":"Vở","quantity":"10","unit":"Quyển","unitPrice":"10.000","taxRate":"KCT","amount":"100.000"}]},
			"warnings":["DOCUMENT_TYPE_REASON: có ký hiệu và MST"]}
			""");
		assertNull(result.invoice().items().getFirst().taxRate());
		assertEquals("EXEMPT_KCT", note(result, "taxRate", 1).normalizedValue());
		assertEquals("KCT", note(result, "taxRate", 1).rawValue());
		assertEquals("2025-03-15", result.invoice().invoiceDate());
		assertEquals("15/03/2025", note(result, "invoiceDate", null).rawValue());
		var validated = validator.validate(result, TYPES);
		assertFalse(validated.warnings().stream().anyMatch(w -> w.startsWith("MISSING_VAT_AMOUNT")), validated.warnings().toString());
		assertFalse(validated.requiresReview(), validated.warnings().toString());
	}

	@Test
	void zeroPercentRowsStillNeedAPrintedVatAmount() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.9,"invoice":{"invoiceNumber":"1","invoiceDate":"2025-03-15",
			"sellerName":"A","sellerTaxCode":"0100109106","subtotal":100000,"totalAmount":100000,"items":[
			{"productName":"Hàng xuất khẩu","quantity":1,"unitPrice":100000,"taxRate":"0%","amount":100000}]},
			"warnings":["DOCUMENT_TYPE_REASON: x"]}
			""");
		assertEquals(0, BigDecimal.ZERO.compareTo(result.invoice().items().getFirst().taxRate()));
		assertTrue(validator.validate(result, TYPES).warnings().stream().anyMatch(w -> w.startsWith("MISSING_VAT_AMOUNT")));
	}

	@Test
	void ambiguousQuantityIsKeptWithBothReadingsAndBlocksAutoApproval() {
		var result = parse("""
			{"documentType":"RETAIL_INVOICE","classificationConfidence":0.9,"invoice":{"sellerName":"Cửa hàng","invoiceDate":"2025-03-15",
			"totalAmount":45000,"items":[{"productName":"Gạo","quantity":"1,500","unit":"kg","unitPrice":30000,"amount":45000}]},
			"warnings":["DOCUMENT_TYPE_REASON: bill bán lẻ"]}
			""");
		assertEquals(0, new BigDecimal("1.5").compareTo(result.invoice().items().getFirst().quantity()));
		var quantity = note(result, "quantity", 1);
		assertEquals("AMBIGUOUS", quantity.status());
		assertEquals("1,500", quantity.rawValue());
		var validated = validator.validate(result, TYPES);
		assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith("AMBIGUOUS_NUMBER") && w.contains("1500")));
		assertTrue(validated.requiresReview());
	}

	@Test
	void unreadableAmountLeavesOnlyThatFieldEmpty() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.9,"invoice":{"invoiceNumber":"1","sellerName":"A",
			"totalAmount":"1.0.0","items":[{"productName":"X","quantity":"1","unitPrice":"abc","amount":"10.000"}]},
			"warnings":[]}
			""");
		assertNull(result.invoice().totalAmount());
		assertNull(result.invoice().items().getFirst().unitPrice());
		assertEquals(0, new BigDecimal("10000").compareTo(result.invoice().items().getFirst().amount()));
		assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("EXTRACTION_FAILED_UNIT_PRICE") && w.contains("row=1")));
		assertEquals("CRITICAL", AssessmentWarnings.normalize(result.warnings().getFirst()).severity());
	}

	@Test
	void percentRateAndTaxCodeFormattingAreNormalizedWithNotes() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","invoice":{"sellerTaxCode":"0100 109 106","buyerTaxCode":"0100109106001","items":[
			{"productName":"A","quantity":1,"unitPrice":100,"taxRate":8,"amount":100}]}}
			""");
		assertEquals("0100109106", result.invoice().sellerTaxCode());
		assertEquals("0100109106-001", result.invoice().buyerTaxCode());
		assertEquals(0, new BigDecimal("0.08").compareTo(result.invoice().items().getFirst().taxRate()));
		assertEquals("8", note(result, "taxRate", 1).rawValue());
	}

	@Test
	void twelveDigitPersonalTaxCodeIsValidAndAWrongCheckDigitIsOnlyAWarning() {
		var personal = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.9,"invoice":{"invoiceNumber":"1","invoiceDate":"2025-03-15",
			"sellerName":"Hộ kinh doanh B","sellerTaxCode":"001203012345","buyerTaxCode":"0100109107","subtotal":100,"vatAmount":8,
			"totalAmount":108,"items":[{"productName":"A","quantity":1,"unitPrice":100,"taxRate":0.08,"taxAmount":8,"amount":100}]},
			"warnings":["DOCUMENT_TYPE_REASON: x"]}
			""");
		var validated = validator.validate(personal, TYPES);
		assertFalse(validated.warnings().stream().anyMatch(w -> w.startsWith("INVALID_SELLER_TAX_CODE")), validated.warnings().toString());
		assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith("TAX_CODE_CHECKSUM_MISMATCH") && w.contains("buyerTaxCode")));
		assertFalse(validated.requiresReview(), validated.warnings().toString());
	}

	@Test
	void quantityWithThreeDecimalsWarnsThatTheColumnWillRoundIt() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","invoice":{"items":[{"productName":"Kính","quantity":12.635,"unitPrice":238182,"amount":3009429}]}}
			""");
		var warnings = validator.validate(result, TYPES).warnings();
		var precision = warnings.stream().filter(w -> w.startsWith("PRECISION_LOSS")).findFirst().orElseThrow();
		assertTrue(precision.contains("field=quantity"));
		assertEquals("WARNING", AssessmentWarnings.normalize(precision).severity());
	}

	@Test
	void paymentVoucherReadsVietnameseAmountAndPrintedCurrency() {
		var result = parse("""
			{"documentType":"PAYMENT_VOUCHER","classificationConfidence":0.9,"invoice":null,"extraFields":[
			{"name":"voucherDate","label":"Ngày","value":"05/03/2025","confidence":0.9},
			{"name":"payerName","label":"Người chi","value":"Công ty A","confidence":0.9},
			{"name":"payeeName","label":"Người nhận","value":"Nguyễn Văn B","confidence":0.9},
			{"name":"paymentReason","label":"Lý do","value":"Tạm ứng","confidence":0.9},
			{"name":"totalAmount","label":"Số tiền","value":"1.500.000 đ","confidence":0.9}],
			"warnings":["DOCUMENT_TYPE_REASON: phiếu chi"]}
			""");
		var validated = validator.validate(result, TYPES);
		var fields = validated.result().extraFields();
		assertEquals("1500000", value(fields, "totalAmount"));
		assertEquals("VND", value(fields, "currency"));
		assertEquals("2025-03-05", value(fields, "voucherDate"));
		assertEquals("1.500.000 đ", note(validated.result(), "totalAmount", null).rawValue());
		assertEquals("05/03/2025", note(validated.result(), "voucherDate", null).rawValue());
		assertFalse(validated.warnings().stream().anyMatch(w -> w.startsWith("INVALID_AMOUNT") || w.contains("field=currency")),
				validated.warnings().toString());
	}

	@Test
	void receiptVoucherWithoutAnyCurrencyEvidenceStillAsksForIt() {
		var result = parse("""
			{"documentType":"RECEIPT_VOUCHER","classificationConfidence":0.9,"extraFields":[
			{"name":"voucherDate","value":"2025-03-05"},{"name":"payerName","value":"A"},{"name":"recipientName","value":"B"},
			{"name":"receiptReason","value":"Thu tiền hàng"},{"name":"totalAmount","value":"2.000.000"}],
			"warnings":["DOCUMENT_TYPE_REASON: phiếu thu"]}
			""");
		var validated = validator.validate(result, TYPES);
		assertEquals("2000000", value(validated.result().extraFields(), "totalAmount"));
		assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith("MISSING_REQUIRED_FIELD") && w.contains("field=currency")));
	}

	@Test
	void receiptWithAnInvalidDateKeepsTheRawTextInTheNotes() {
		var result = parse("""
			{"documentType":"RECEIPT","classificationConfidence":0.9,"extraFields":[
			{"name":"receiptKind","value":"NON_FINANCIAL"},{"name":"receiptDate","value":"15/03/25"},
			{"name":"issuerName","value":"A"},{"name":"recipientName","value":"B"},{"name":"subject","value":"Hồ sơ"}],
			"warnings":["DOCUMENT_TYPE_REASON: biên nhận"]}
			""");
		var validated = validator.validate(result, TYPES);
		assertTrue(validated.warnings().stream().anyMatch(w -> w.startsWith("INVALID_DATE") && w.contains("receiptDate")));
		assertEquals("TWO_DIGIT_YEAR", note(validated.result(), "receiptDate", null).reason());
	}

	@Test
	void repeatedIdenticalRetailRowsSurviveParsingAndValidation() {
		var result = parse("""
			{"documentType":"RETAIL_INVOICE","classificationConfidence":0.9,"invoice":{"sellerName":"Cửa hàng","invoiceDate":"2025-03-15",
			"totalAmount":20000,"items":[
			{"productName":"Nước suối","quantity":2,"unit":"Chai","unitPrice":5000,"amount":10000},
			{"productName":"Nước suối","quantity":2,"unit":"Chai","unitPrice":5000,"amount":10000}]},
			"warnings":["DOCUMENT_TYPE_REASON: bill"]}
			""");
		assertEquals(2, validator.validate(result, TYPES).result().invoice().items().size());
	}

	@Test
	void discountColumnInvoiceEndsWithoutRowTaxMismatchOrAmountMismatch() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.95,"invoice":{"invoiceNumber":"00001583","invoiceSeries":"1C25TYY",
			"invoiceDate":"2025-06-24","sellerName":"Công ty C","sellerTaxCode":"0100109106","subtotal":2182500,"vatAmount":174600,
			"totalAmount":2357100,"items":[
			{"productName":"Ván LMR 17 mm x 1220 x 2440 phủ Melamine 2 mặt 01T Tấm","quantity":3000,"unitPrice":444444.44,"taxRate":0.08,"taxAmount":40000,"amount":1293333},
			{"productName":"Ván LMR 8 mm x 1220 x 2440 phủ Melamine 2 mặt trắng 01T Tấm","quantity":3000,"unitPrice":305555.56,"taxRate":0.08,"taxAmount":27500,"amount":889167}]},
			"warnings":["DOCUMENT_TYPE_REASON: hóa đơn GTGT"]}
			""");
		var validated = validator.validate(result, TYPES);
		assertFalse(validated.warnings().stream().anyMatch(w -> w.startsWith("ITEM_AMOUNT_MISMATCH") || w.startsWith("ITEM_TAX_MISMATCH")
				|| w.startsWith("ITEM_ROW_TAX")), validated.warnings().toString());
		assertEquals(0, new BigDecimal("3").compareTo(validated.result().invoice().items().getFirst().quantity()));
		assertFalse(validated.requiresReview(), validated.warnings().toString());
		var report = FieldValidationReport.build(validated.result(), validated.warnings(), true);
		var discount = report.items().stream().filter(s -> "discountAmount".equals(s.field()) && s.row() == 1).findFirst().orElseThrow();
		assertEquals("40000", discount.normalizedValue());
		assertEquals("REPAIRED_WITH_EVIDENCE", discount.status());
	}

	@Test
	void invoiceWithVatOnlyInTheTotalsPassesWithoutRowTaxWarnings() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.95,"invoice":{"invoiceNumber":"1843","invoiceSeries":"1C25THP",
			"invoiceDate":"2025-07-01","sellerName":"Công ty Nguyễn Hùng Phát","sellerTaxCode":"0314752096","subtotal":451151,
			"vatAmount":36092,"totalAmount":487243,"items":[
			{"productName":"Công tắc 1 chiều S30/1/2M","quantity":5,"unit":"Cái","unitPrice":6937,"taxRate":0.08,"taxAmount":34685,"amount":34685},
			{"productName":"Mặt nạ 2 S182/x","quantity":1,"unit":"Cái","unitPrice":7875,"taxRate":0.08,"taxAmount":7875,"amount":7875},
			{"productName":"Mặt nạ 3 S183/X","quantity":1,"unit":"Cái","unitPrice":7875,"taxRate":0.08,"taxAmount":7875,"amount":7875},
			{"productName":"Đế nổi chữ nhật CK157RH","quantity":12,"unit":"Cái","unitPrice":4623,"taxRate":0.08,"taxAmount":55476,"amount":55476},
			{"productName":"Ổ cắm 3-2 chấu - S18U3","quantity":10,"unit":"Cái","unitPrice":34524,"taxRate":0.08,"taxAmount":345240,"amount":345240}]},
			"warnings":["DOCUMENT_TYPE_REASON: hóa đơn GTGT"]}
			""");
		var validated = validator.validate(result, TYPES);
		assertTrue(validated.result().invoice().items().stream().allMatch(item -> item.taxAmount() == null));
		assertFalse(validated.warnings().stream().anyMatch(w -> w.startsWith("ITEM_TAX_MISMATCH") || w.startsWith("ITEM_ROW_TAX_TOTAL")),
				validated.warnings().toString());
		assertFalse(validated.requiresReview(), validated.warnings().toString());
	}

	@Test
	void discountReturnedInItsOwnFieldIsKeptAndChecked() {
		var result = parse("""
			{"documentType":"VAT_INVOICE","classificationConfidence":0.95,"invoice":{"invoiceNumber":"1","invoiceDate":"2025-06-24",
			"sellerName":"C","sellerTaxCode":"0100109106","subtotal":1293333,"vatAmount":103467,"totalAmount":1396800,"items":[
			{"productName":"Ván","quantity":"3,000","unit":"Tấm","unitPrice":"444.444,44","taxRate":0.08,"discountAmount":"40.000","amount":1293333}]},
			"warnings":["DOCUMENT_TYPE_REASON: x"]}
			""");
		assertEquals(0, new BigDecimal("3").compareTo(result.invoice().items().getFirst().quantity()), "both separators prove the Vietnamese convention");
		var validated = validator.validate(result, TYPES);
		assertFalse(validated.warnings().stream().anyMatch(w -> w.startsWith("ITEM_AMOUNT_MISMATCH")), validated.warnings().toString());
		assertEquals(0, new BigDecimal("40000").compareTo(InvoiceItemRepairs.discounts(validated.result().notes()).get(1)));
	}

	private static String value(java.util.List<AiDocumentResult.AiExtraField> fields, String name) {
		return fields.stream().filter(f -> name.equals(f.name())).map(AiDocumentResult.AiExtraField::value).findFirst().orElse(null);
	}
}
