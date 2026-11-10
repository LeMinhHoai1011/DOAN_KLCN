package com.example.invoice.ai.normalize;

/**
 * Vietnamese tax codes: 10 digits (enterprise, with a check digit), 10 digits plus a 3-digit branch suffix, or the
 * 12-digit personal identification number used as the tax code of individuals and household businesses.
 * Formatting only: spaces and dots are removed and a 13-digit run gets its branch hyphen; digits are never changed.
 */
public final class TaxCodes {
	private TaxCodes() {}

	private static final int[] WEIGHTS = {31, 29, 23, 19, 17, 13, 7, 5, 3};

	/** {@code value} is the canonical form when {@code valid}, otherwise the text as printed. */
	public record Parsed(String raw, String value, boolean valid, boolean checksumOk, boolean changed, String reason) {}

	public static Parsed parse(String raw) {
		if (raw == null || raw.isBlank()) return new Parsed(raw, raw, false, false, false, "EMPTY");
		String trimmed = raw.trim();
		String compact = trimmed.replaceAll("[\\s. _]", "").replace('–', '-').replace('—', '-');
		String value = null;
		if (compact.matches("\\d{10}")) value = compact;
		else if (compact.matches("\\d{13}")) value = compact.substring(0, 10) + "-" + compact.substring(10);
		else if (compact.matches("\\d{10}-\\d{3}")) value = compact;
		else if (compact.matches("\\d{12}")) return new Parsed(raw, compact, true, true, !compact.equals(trimmed), "PERSONAL_ID_TAX_CODE");
		if (value == null) return new Parsed(raw, trimmed, false, false, false, "INVALID_FORMAT");
		boolean checksum = checksumOk(value.substring(0, 10));
		return new Parsed(raw, value, true, checksum, !value.equals(trimmed), checksum ? null : "CHECKSUM_MISMATCH");
	}

	/** Tenth digit = 10 − (Σ weight × digit mod 11) over the first nine digits. */
	static boolean checksumOk(String tenDigits) {
		int sum = 0;
		for (int index = 0; index < 9; index++) sum += WEIGHTS[index] * (tenDigits.charAt(index) - '0');
		int check = 10 - sum % 11;
		return check == tenDigits.charAt(9) - '0';
	}
}
