package com.example.invoice.ai.normalize;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Reads a printed number without guessing. Vietnamese documents group thousands with a dot and use a decimal comma;
 * international ones do the opposite. A single comma followed by exactly three digits is truly ambiguous for a
 * quantity or price ("3,000" can be 3 or 3000). Such a value is read the Vietnamese way but reported
 * {@link Status#AMBIGUOUS} unless the same document proves its convention elsewhere. Money is never written with
 * three decimals, so for money the grouping reading is the only one. A value that cannot be read returns {@link Status#INVALID} with a null value;
 * it never throws.
 */
public final class LocalizedNumber {
	private LocalizedNumber() {}

	public enum Kind { MONEY, QUANTITY, PRICE }
	public enum Convention { VIETNAMESE, INTERNATIONAL, UNKNOWN }
	public enum Status { VALID, NORMALIZED, AMBIGUOUS, INVALID, EMPTY }

	/** {@code alternative} is the other possible reading of an ambiguous value. */
	public record Parsed(String raw, BigDecimal value, Status status, String reason, BigDecimal alternative) {}

	private static final Pattern CURRENCY = Pattern.compile("(?iu)(vnđ|vnd|đồng|dong|usd|eur|đ|₫|\\$|€)");
	private static final Pattern DIGITS_AND_SEPARATORS = Pattern.compile("[0-9.,]+");
	private static final Pattern GROUPED_DOT = Pattern.compile("\\d{1,3}(\\.\\d{3})+");
	private static final Pattern GROUPED_COMMA = Pattern.compile("\\d{1,3}(,\\d{3})+");

	public static Parsed parse(String raw, Kind kind, Convention convention) {
		if (raw == null || raw.isBlank()) return new Parsed(raw, null, Status.EMPTY, null, null);
		String value = Normalizer.normalize(raw, Normalizer.Form.NFC).trim();
		boolean negative = false;
		if (value.startsWith("(") && value.endsWith(")")) { negative = true; value = value.substring(1, value.length() - 1); }
		value = CURRENCY.matcher(value).replaceAll("").replace(" ", "").replace(" ", "").replace("'", "");
		if (value.endsWith(",-") || value.endsWith(".-")) value = value.substring(0, value.length() - 2);
		if (value.startsWith("-") || value.startsWith("−")) { negative = !negative; value = value.substring(1); }
		if (value.startsWith("+")) value = value.substring(1);
		String reason = null;
		boolean digitConfusion = false;
		if (!DIGITS_AND_SEPARATORS.matcher(value).matches() && value.matches("[0-9.,OoIl]+") && value.matches(".*\\d.*")) {
			value = value.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1');
			digitConfusion = true;
			reason = "OCR_DIGIT_CONFUSION";
		}
		if (value.isEmpty() || !DIGITS_AND_SEPARATORS.matcher(value).matches() || !value.matches(".*\\d.*"))
			return new Parsed(raw, null, Status.INVALID, "NOT_A_NUMBER", null);
		Reading reading = read(value, kind, convention == null ? Convention.UNKNOWN : convention);
		if (reading == null) return new Parsed(raw, null, Status.INVALID, "INVALID_SEPARATORS", null);
		BigDecimal parsed = sign(reading.value, negative);
		BigDecimal alternative = reading.alternative == null ? null : sign(reading.alternative, negative);
		Status status = reading.ambiguous || digitConfusion ? Status.AMBIGUOUS
				: value.equals(raw.trim()) && !value.contains(",") && (value.indexOf('.') < 0 || reading.plain) ? Status.VALID
				: Status.NORMALIZED;
		return new Parsed(raw, parsed, status, reason != null ? reason : reading.reason, alternative);
	}

	/** Convention proven by values that can only be read one way; UNKNOWN when there is none or they disagree. */
	public static Convention detect(Collection<String> raws) {
		boolean vietnamese = false, international = false;
		for (String raw : raws) {
			if (raw == null) continue;
			String value = CURRENCY.matcher(raw).replaceAll("").replace(" ", "").replace(" ", "").replace("-", "");
			if (!DIGITS_AND_SEPARATORS.matcher(value).matches()) continue;
			int dot = value.lastIndexOf('.'), comma = value.lastIndexOf(',');
			if (dot >= 0 && comma >= 0) { if (comma > dot) vietnamese = true; else international = true; continue; }
			long dots = value.chars().filter(c -> c == '.').count(), commas = value.chars().filter(c -> c == ',').count();
			if (dots > 1 && GROUPED_DOT.matcher(value).matches()) vietnamese = true;
			else if (commas > 1 && GROUPED_COMMA.matcher(value).matches()) international = true;
			else if (commas == 1 && digitsAfter(value, comma) != 3 && digitsAfter(value, comma) > 0) vietnamese = true;
			// "12.5" proves nothing: the model writes canonical decimals that way whatever the document printed.
		}
		if (vietnamese == international) return Convention.UNKNOWN;
		return vietnamese ? Convention.VIETNAMESE : Convention.INTERNATIONAL;
	}

	private record Reading(BigDecimal value, BigDecimal alternative, boolean ambiguous, String reason, boolean plain) {}

	private static Reading read(String value, Kind kind, Convention convention) {
		int dot = value.lastIndexOf('.'), comma = value.lastIndexOf(',');
		long dots = value.chars().filter(c -> c == '.').count(), commas = value.chars().filter(c -> c == ',').count();
		if (dots == 0 && commas == 0) return new Reading(new BigDecimal(value), null, false, null, true);
		if (dots > 0 && commas > 0) {
			char decimal = comma > dot ? ',' : '.';
			char grouping = decimal == ',' ? '.' : ',';
			if (value.chars().filter(c -> c == decimal).count() != 1) return null;
			String integer = value.substring(0, value.lastIndexOf(decimal));
			String fraction = value.substring(value.lastIndexOf(decimal) + 1);
			Pattern groups = grouping == '.' ? GROUPED_DOT : GROUPED_COMMA;
			if (fraction.isEmpty() || !groups.matcher(integer).matches()) return null;
			return new Reading(new BigDecimal(integer.replace(String.valueOf(grouping), "") + "." + fraction), null, false,
					decimal == ',' ? "VIETNAMESE_FORMAT" : "INTERNATIONAL_FORMAT", false);
		}
		char separator = dots > 0 ? '.' : ',';
		long count = Math.max(dots, commas);
		if (count > 1) {
			Pattern groups = separator == '.' ? GROUPED_DOT : GROUPED_COMMA;
			if (!groups.matcher(value).matches()) return null;
			return new Reading(new BigDecimal(value.replace(String.valueOf(separator), "")), null, false, "THOUSANDS_GROUPING", false);
		}
		int position = value.indexOf(separator);
		String integer = value.substring(0, position), fraction = value.substring(position + 1);
		if (integer.isEmpty() || fraction.isEmpty()) return null;
		BigDecimal asDecimal = new BigDecimal(integer + "." + fraction);
		if (fraction.length() != 3 || integer.matches("0+")) return new Reading(asDecimal, null, false,
				separator == ',' ? "DECIMAL_COMMA" : null, separator == '.');
		BigDecimal asGrouping = new BigDecimal(integer + fraction);
		if (integer.length() > 3) return new Reading(asDecimal, null, false, "DECIMAL_SEPARATOR", separator == '.');
		if (kind == Kind.MONEY) return new Reading(asGrouping, null, false, "MONEY_THOUSANDS_GROUPING", false);
		// A dot before three digits groups thousands on Vietnamese documents ("50.000"), unless the document is proven
		// international. A comma before three digits is the real ambiguity: Vietnamese decimal ("3,000" = 3) or
		// international grouping (3000).
		if (separator == '.' && convention != Convention.INTERNATIONAL)
			return new Reading(asGrouping, null, false, "VIETNAMESE_THOUSANDS_DOT", false);
		if (convention == Convention.VIETNAMESE) return new Reading(asDecimal, null, false, "DOCUMENT_CONVENTION", false);
		if (convention == Convention.INTERNATIONAL)
			return new Reading(separator == ',' ? asGrouping : asDecimal, null, false, "DOCUMENT_CONVENTION", false);
		return new Reading(asDecimal, asGrouping, true, "SEPARATOR_AMBIGUOUS", false);
	}

	private static int digitsAfter(String value, int index) { return value.length() - index - 1; }

	private static BigDecimal sign(BigDecimal value, boolean negative) { return negative ? value.negate() : value; }
}
