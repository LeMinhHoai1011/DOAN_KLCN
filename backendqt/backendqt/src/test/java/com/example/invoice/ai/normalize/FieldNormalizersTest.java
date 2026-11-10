package com.example.invoice.ai.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.ai.normalize.TaxRateValue.Category;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FieldNormalizersTest {
	@ParameterizedTest(name = "rate {0}")
	@CsvSource(delimiter = '|', value = {
			"8%|RATE|0.08",
			"10 %|RATE|0.1",
			"5,25%|RATE|0.0525",
			"0%|ZERO_RATE|0",
			"0,08|RATE|0.08",
			"8|RATE|0.08",
	})
	void readsPrintedTaxRates(String raw, Category category, String rate) {
		TaxRateValue value = TaxRateValue.parse(raw);
		assertEquals(category, value.category());
		assertEquals(0, new BigDecimal(rate).compareTo(value.rate()));
	}

	@ParameterizedTest(name = "no tax {0}")
	@CsvSource(delimiter = '|', value = {
			"KCT|EXEMPT_KCT", "kct|EXEMPT_KCT", "Không chịu thuế|EXEMPT_KCT",
			"KKKNT|NOT_DECLARED_KKKNT", "Không kê khai, tính nộp thuế|NOT_DECLARED_KKKNT"})
	void keepsNotSubjectToVatApartFromZeroPercent(String raw, Category category) {
		TaxRateValue value = TaxRateValue.parse(raw);
		assertEquals(category, value.category());
		assertNull(value.rate(), "KCT/KKKNT have no rate, which is not 0%");
		assertTrue(value.noTaxApplies());
	}

	@Test
	void distinguishesZeroMissingAndUnreadableRates() {
		assertEquals(Category.ZERO_RATE, TaxRateValue.ofNumber(BigDecimal.ZERO).category());
		assertEquals(Category.UNKNOWN, TaxRateValue.ofNumber(null).category());
		assertEquals(LocalizedNumber.Status.EMPTY, TaxRateValue.ofNumber(null).status());
		assertEquals(LocalizedNumber.Status.INVALID, TaxRateValue.parse("x").status());
		assertEquals(LocalizedNumber.Status.INVALID, TaxRateValue.ofNumber(new BigDecimal("150")).status());
		assertEquals(LocalizedNumber.Status.VALID, TaxRateValue.ofNumber(new BigDecimal("0.08")).status());
		assertEquals(0, new BigDecimal("0.1").compareTo(TaxRateValue.ofNumber(BigDecimal.TEN).rate()));
	}

	@ParameterizedTest(name = "date {0}")
	@CsvSource(delimiter = '|', value = {
			"2025-03-15|2025-03-15|VALID",
			"15/03/2025|2025-03-15|NORMALIZED",
			"5/3/2025|2025-03-05|NORMALIZED",
			"15-03-2025|2025-03-15|NORMALIZED",
			"15.03.2025|2025-03-15|NORMALIZED",
			"2025/03/15|2025-03-15|NORMALIZED",
			"Ngày 15 tháng 03 năm 2025|2025-03-15|NORMALIZED",
			"2025-03-15T08:30:00|2025-03-15|NORMALIZED",
	})
	void readsPrintedDatesDayFirst(String raw, String iso, LocalizedNumber.Status status) {
		DocumentDates.Parsed parsed = DocumentDates.parse(raw);
		assertEquals(iso, parsed.value());
		assertEquals(status, parsed.status());
	}

	@Test
	void doesNotGuessACenturyOrAnImpossibleDate() {
		assertNull(DocumentDates.parse("15/03/25").value());
		assertEquals(LocalizedNumber.Status.AMBIGUOUS, DocumentDates.parse("15/03/25").status());
		assertNull(DocumentDates.parse("31/02/2025").value());
		assertEquals(LocalizedNumber.Status.INVALID, DocumentDates.parse("31/02/2025").status());
		assertEquals(LocalizedNumber.Status.INVALID, DocumentDates.parse("không rõ").status());
	}

	@Test
	void taxCodesKeepDigitsAndOnlyFixFormatting() {
		TaxCodes.Parsed enterprise = TaxCodes.parse("0100109106");
		assertTrue(enterprise.valid());
		assertTrue(enterprise.checksumOk());
		assertFalse(enterprise.changed());
		assertEquals("0300588569", TaxCodes.parse("0300 588 569").value());
		assertTrue(TaxCodes.parse("0300 588 569").changed());
		assertEquals("0100109106-001", TaxCodes.parse("0100109106001").value());
		assertEquals("0100109106-001", TaxCodes.parse("0100109106 - 001").value());
		TaxCodes.Parsed personal = TaxCodes.parse("001203012345");
		assertTrue(personal.valid());
		assertEquals("PERSONAL_ID_TAX_CODE", personal.reason());
	}

	@Test
	void taxCodeWithAWrongCheckDigitIsFlaggedNotChanged() {
		TaxCodes.Parsed misread = TaxCodes.parse("0100109107");
		assertTrue(misread.valid());
		assertFalse(misread.checksumOk());
		assertEquals("0100109107", misread.value());
		assertFalse(TaxCodes.parse("12345").valid());
		assertEquals("12345", TaxCodes.parse("12345").value());
	}
}
