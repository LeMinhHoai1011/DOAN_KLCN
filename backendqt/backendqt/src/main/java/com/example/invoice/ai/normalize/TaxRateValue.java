package com.example.invoice.ai.normalize;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/**
 * A row tax rate keeps apart what a bare number cannot: 0% (taxed at zero), KCT (not subject to VAT), KKKNT (not
 * declared) and no rate printed at all. The rate is a fraction (8% is 0.08); KCT and KKKNT have no rate.
 */
public record TaxRateValue(Category category, BigDecimal rate, LocalizedNumber.Status status, String reason, String raw) {
	public enum Category { RATE, ZERO_RATE, EXEMPT_KCT, NOT_DECLARED_KKKNT, UNKNOWN }

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	public boolean noTaxApplies() { return category == Category.EXEMPT_KCT || category == Category.NOT_DECLARED_KKKNT; }

	public static TaxRateValue ofNumber(BigDecimal value) {
		if (value == null) return new TaxRateValue(Category.UNKNOWN, null, LocalizedNumber.Status.EMPTY, null, null);
		String raw = value.toPlainString();
		if (value.signum() < 0 || value.compareTo(HUNDRED) > 0)
			return new TaxRateValue(Category.UNKNOWN, null, LocalizedNumber.Status.INVALID, "RATE_OUT_OF_RANGE", raw);
		if (value.signum() == 0) return new TaxRateValue(Category.ZERO_RATE, BigDecimal.ZERO, LocalizedNumber.Status.VALID, null, raw);
		if (value.compareTo(BigDecimal.ONE) > 0)
			return new TaxRateValue(Category.RATE, value.divide(HUNDRED).stripTrailingZeros(), LocalizedNumber.Status.NORMALIZED,
					"PERCENT_TO_FRACTION", raw);
		return new TaxRateValue(Category.RATE, value, LocalizedNumber.Status.VALID, null, raw);
	}

	public static TaxRateValue parse(String raw) {
		if (raw == null || raw.isBlank()) return new TaxRateValue(Category.UNKNOWN, null, LocalizedNumber.Status.EMPTY, null, raw);
		String folded = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT).trim();
		if (folded.contains("KKKNT") || folded.contains("KHONG KE KHAI"))
			return new TaxRateValue(Category.NOT_DECLARED_KKKNT, null, LocalizedNumber.Status.NORMALIZED, "TAX_NOT_DECLARED_KKKNT", raw);
		if (folded.contains("KCT") || folded.contains("KHONG CHIU THUE"))
			return new TaxRateValue(Category.EXEMPT_KCT, null, LocalizedNumber.Status.NORMALIZED, "TAX_EXEMPT_KCT", raw);
		boolean percent = folded.endsWith("%");
		String number = percent ? folded.substring(0, folded.length() - 1).trim() : folded;
		LocalizedNumber.Parsed parsed = LocalizedNumber.parse(number, LocalizedNumber.Kind.QUANTITY, LocalizedNumber.Convention.VIETNAMESE);
		if (parsed.value() == null)
			return new TaxRateValue(Category.UNKNOWN, null, LocalizedNumber.Status.INVALID, "UNREADABLE_TAX_RATE", raw);
		BigDecimal value = percent ? parsed.value().divide(HUNDRED) : parsed.value();
		TaxRateValue numeric = ofNumber(value);
		return new TaxRateValue(numeric.category(), numeric.rate(),
				numeric.status() == LocalizedNumber.Status.INVALID ? LocalizedNumber.Status.INVALID : LocalizedNumber.Status.NORMALIZED,
				numeric.reason() != null ? numeric.reason() : percent ? "PERCENT_TO_FRACTION" : "TEXT_TO_NUMBER", raw);
	}
}
