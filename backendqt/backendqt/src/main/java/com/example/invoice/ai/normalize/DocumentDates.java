package com.example.invoice.ai.normalize;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Printed dates to ISO yyyy-MM-dd. Vietnamese documents write day before month; a two-digit year is not expanded
 * because its century would be a guess.
 */
public final class DocumentDates {
	private DocumentDates() {}

	/** {@code value} is ISO, or null when the text is not a readable date. */
	public record Parsed(String raw, String value, LocalizedNumber.Status status, String reason) {}

	private static final List<DateTimeFormatter> DAY_FIRST = List.of(
			DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
			DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
			DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT),
			DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT));
	private static final Pattern WORDS = Pattern.compile("ngay\\s*(\\d{1,2})\\s*thang\\s*(\\d{1,2})\\s*nam\\s*(\\d{4})");
	private static final Pattern TWO_DIGIT_YEAR = Pattern.compile("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2}");

	public static Parsed parse(String raw) {
		if (raw == null || raw.isBlank()) return new Parsed(raw, null, LocalizedNumber.Status.EMPTY, null);
		String value = raw.trim();
		try { return new Parsed(raw, LocalDate.parse(value).toString(), LocalizedNumber.Status.VALID, null); }
		catch (DateTimeParseException ignored) { /* Try printed forms below. */ }
		if (value.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
			try { return new Parsed(raw, LocalDate.parse(value.substring(0, 10)).toString(), LocalizedNumber.Status.NORMALIZED, "DATE_TIME_TO_DATE"); }
			catch (DateTimeParseException ignored) { /* Reported as invalid below. */ }
		}
		for (DateTimeFormatter format : DAY_FIRST) {
			try { return new Parsed(raw, LocalDate.parse(value, format).toString(), LocalizedNumber.Status.NORMALIZED, "DAY_FIRST_FORMAT"); }
			catch (DateTimeParseException ignored) { /* Next format. */ }
		}
		String folded = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
		Matcher words = WORDS.matcher(folded);
		if (words.find()) {
			try {
				return new Parsed(raw, LocalDate.of(Integer.parseInt(words.group(3)), Integer.parseInt(words.group(2)),
						Integer.parseInt(words.group(1))).toString(), LocalizedNumber.Status.NORMALIZED, "VIETNAMESE_WORDS");
			} catch (java.time.DateTimeException ignored) { /* Reported as invalid below. */ }
		}
		if (TWO_DIGIT_YEAR.matcher(value).matches()) return new Parsed(raw, null, LocalizedNumber.Status.AMBIGUOUS, "TWO_DIGIT_YEAR");
		return new Parsed(raw, null, LocalizedNumber.Status.INVALID, "UNREADABLE_DATE");
	}
}
