package com.example.invoice.ai.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.invoice.ai.normalize.LocalizedNumber.Convention;
import com.example.invoice.ai.normalize.LocalizedNumber.Kind;
import com.example.invoice.ai.normalize.LocalizedNumber.Status;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocalizedNumberTest {
	private static void assertReads(String raw, Kind kind, Convention convention, String expected, Status status) {
		LocalizedNumber.Parsed parsed = LocalizedNumber.parse(raw, kind, convention);
		assertEquals(status, parsed.status(), raw);
		if (expected == null) assertNull(parsed.value(), raw);
		else assertEquals(0, new BigDecimal(expected).compareTo(parsed.value()), raw + " -> " + parsed.value());
		assertEquals(raw, parsed.raw(), "raw text is kept as returned");
	}

	@ParameterizedTest(name = "money {0} = {1}")
	@CsvSource(delimiter = '|', value = {
			"842400|842400|VALID",
			"842.400|842400|NORMALIZED",
			"842,400|842400|NORMALIZED",
			"1.234.567|1234567|NORMALIZED",
			"1,234,567|1234567|NORMALIZED",
			"1.234.567,89|1234567.89|NORMALIZED",
			"1,234,567.89|1234567.89|NORMALIZED",
			"80.000,000|80000|NORMALIZED",
			"1.500.000 đ|1500000|NORMALIZED",
			"1.500.000đ|1500000|NORMALIZED",
			"1.500.000 VNĐ|1500000|NORMALIZED",
			"1.500.000 VND|1500000|NORMALIZED",
			"₫ 250.000|250000|NORMALIZED",
			"10.000,-|10000|NORMALIZED",
			"(1.000)|-1000|NORMALIZED",
			"-25.000|-25000|NORMALIZED",
			"1 234 567|1234567|NORMALIZED",
			"12,5|12.5|NORMALIZED",
			"0,500|0.5|NORMALIZED",
			"1234.567|1234.567|VALID",
	})
	void readsMoneyInVietnameseAndInternationalFormats(String raw, String expected, Status status) {
		assertReads(raw, Kind.MONEY, Convention.UNKNOWN, expected, status);
	}

	@ParameterizedTest(name = "quantity {0} = {1} ({2})")
	@CsvSource(delimiter = '|', value = {
			"3|3|VALID",
			"3,00|3|NORMALIZED",
			"12,63|12.63|NORMALIZED",
			"12.5|12.5|VALID",
			"0,125|0.125|NORMALIZED",
			"3,000|3|AMBIGUOUS",
			"1,500|1.5|AMBIGUOUS",
			"1.500|1500|NORMALIZED",
			"50.000|50000|NORMALIZED",
	})
	void commaFollowedByThreeDigitsIsAmbiguousForQuantitiesWithoutEvidence(String raw, String expected, Status status) {
		assertReads(raw, Kind.QUANTITY, Convention.UNKNOWN, expected, status);
	}

	@Test
	void ambiguousValueReportsTheOtherReading() {
		var parsed = LocalizedNumber.parse("3,000", Kind.QUANTITY, Convention.UNKNOWN);
		assertEquals(0, new BigDecimal("3000").compareTo(parsed.alternative()));
		assertEquals("SEPARATOR_AMBIGUOUS", parsed.reason());
	}

	@ParameterizedTest(name = "{0} with {1} convention = {2}")
	@CsvSource(delimiter = '|', value = {
			"3,000|VIETNAMESE|3",
			"3,000|INTERNATIONAL|3000",
			"1.500|VIETNAMESE|1500",
			"1.500|INTERNATIONAL|1.5",
	})
	void documentConventionResolvesTheAmbiguity(String raw, Convention convention, String expected) {
		assertReads(raw, Kind.QUANTITY, convention, expected, Status.NORMALIZED);
	}

	@ParameterizedTest(name = "invalid {0}")
	@CsvSource(delimiter = '|', value = {"8.00.000", "1,2,3", "abc", "10%", "1.234,56,7", "--", ".", "12a5"})
	void unreadableValuesAreInvalidAndNeverThrow(String raw) {
		assertReads(raw, Kind.MONEY, Convention.UNKNOWN, null, Status.INVALID);
	}

	@Test
	void emptyIsNotAnError() {
		assertEquals(Status.EMPTY, LocalizedNumber.parse("  ", Kind.MONEY, Convention.UNKNOWN).status());
		assertEquals(Status.EMPTY, LocalizedNumber.parse(null, Kind.MONEY, Convention.UNKNOWN).status());
	}

	@ParameterizedTest(name = "OCR {0} = {1}")
	@CsvSource(delimiter = '|', value = {"1O.000|10000", "5O0.000|500000", "l2.500|12500"})
	void ocrLetterDigitConfusionIsReadButFlagged(String raw, String expected) {
		var parsed = LocalizedNumber.parse(raw, Kind.MONEY, Convention.UNKNOWN);
		assertEquals(Status.AMBIGUOUS, parsed.status());
		assertEquals("OCR_DIGIT_CONFUSION", parsed.reason());
		assertEquals(0, new BigDecimal(expected).compareTo(parsed.value()));
	}

	@Test
	void detectsTheConventionOnlyFromValuesThatCanBeReadOneWay() {
		assertEquals(Convention.VIETNAMESE, LocalizedNumber.detect(List.of("1.234.567", "3,000")));
		assertEquals(Convention.VIETNAMESE, LocalizedNumber.detect(List.of("12,5")));
		assertEquals(Convention.VIETNAMESE, LocalizedNumber.detect(List.of("80.000,000")));
		assertEquals(Convention.INTERNATIONAL, LocalizedNumber.detect(List.of("1,234,567.50")));
		assertEquals(Convention.UNKNOWN, LocalizedNumber.detect(List.of("3,000", "1.500")));
		assertEquals(Convention.UNKNOWN, LocalizedNumber.detect(List.of("1.234.567", "1,234,567")));
	}
}
