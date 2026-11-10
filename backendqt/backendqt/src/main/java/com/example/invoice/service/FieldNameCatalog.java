package com.example.invoice.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stable technical keys and Vietnamese display names for AI-extracted field names.
 *
 * <p>Keys: Vietnamese letters are folded to ASCII (đ → d, marks removed) before camel-casing, so
 * "Số tiền viết bằng chữ" becomes {@code soTienVietBangChu}. Labels that certainly mean an invoice column
 * map to that column's key so they are never stored as a duplicate dynamic field. Labels that do not say
 * whether they belong to the seller or the buyer ("Mã số thuế", "Địa chỉ", ...) are left as their own key.
 *
 * <p>{@link #legacyKey} reproduces the old algorithm that dropped every non-ASCII letter
 * ({@code sTiNViTBNgChAmountInWords}); it is only used to recognise rows stored before this fix.
 */
public final class FieldNameCatalog {
	private FieldNameCatalog() {}

	public static final Set<String> INVOICE_FIELD_KEYS = Set.of(
			"invoiceNumber", "invoiceSeries", "invoiceDate", "sellerName", "sellerTaxCode", "sellerAddress",
			"sellerPhone", "buyerName", "buyerTaxCode", "buyerAddress", "subtotal", "vatAmount", "taxAmount",
			"totalAmount", "paymentMethod", "amountInWords", "taxAuthorityCode", "signDate");

	/** Display names for invoice columns and common extended fields (keys are stable identifiers). */
	private static final Map<String, String> LABELS = new LinkedHashMap<>();
	/** Compact folded phrase (letters/digits only, lower case) → canonical key. Only unambiguous phrases. */
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();

	static {
		label("invoiceNumber", "Số hóa đơn", "sohoadon", "invoiceno", "invoicenumber", "sono", "sohoadonno");
		label("invoiceSeries", "Ký hiệu hóa đơn", "kyhieu", "kyhieuhoadon", "serial", "serialno", "invoiceseries", "kyhieuserial", "kyhieuserialno");
		label("invoiceDate", "Ngày hóa đơn", "ngayhoadon", "ngaylap", "ngaylaphoadon", "invoicedate");
		label("signDate", "Ngày ký", "ngayky", "kyngay", "signdate", "signingdate", "ngaykysigndate");
		label("sellerName", "Tên người bán", "tennguoiban", "donvibanhang", "tendonvibanhang", "sellername", "issuername");
		label("sellerTaxCode", "Mã số thuế người bán", "masothuenguoiban", "sellertaxcode", "issuertaxcode");
		label("sellerAddress", "Địa chỉ người bán", "diachinguoiban", "selleraddress", "issueraddress");
		label("sellerPhone", "Số điện thoại người bán", "dienthoainguoiban", "sellerphone", "issuerphone");
		label("buyerName", "Tên người mua", "tennguoimua", "donvimuahang", "tendonvimuahang", "buyername");
		label("buyerTaxCode", "Mã số thuế người mua", "masothuenguoimua", "buyertaxcode");
		label("buyerAddress", "Địa chỉ người mua", "diachinguoimua", "buyeraddress");
		label("subtotal", "Tổng tiền trước thuế", "congtienhang", "tongtienhang", "tongtientruocthue", "subtotal", "congtienhangsubtotal");
		label("vatAmount", "Tiền thuế GTGT", "tienthuegtgt", "vatamount", "tienthuegtgtvatamount");
		label("taxAmount", "Tiền thuế", "taxamount");
		label("totalAmount", "Tổng tiền thanh toán", "tongtienthanhtoan", "tongcongtienthanhtoan", "tongcongthanhtoan", "totalpayment", "totalamount", "tongcongtienthanhtoantotalpayment");
		label("paymentMethod", "Hình thức thanh toán", "hinhthucthanhtoan", "phuongthucthanhtoan", "paymentmethod", "methodofpayment", "hinhthucthanhtoanpaymentmethod");
		label("amountInWords", "Số tiền bằng chữ", "sotienvietbangchu", "sotienbangchu", "bangchu", "amountinwords", "inwords", "sotienvietbangchuamountinwords");
		label("taxAuthorityCode", "Mã cơ quan thuế", "macqt", "macuacqt", "macoquanthue", "macuacoquanthue", "macoquanthuecap", "taxauthoritycode");
		// Extended fields (not invoice columns): display only, never merged into an invoice column.
		LABELS.put("lookupCode", "Mã tra cứu"); ALIASES.put("matracuu", "lookupCode"); ALIASES.put("lookupcode", "lookupCode");
		LABELS.put("lookupUrl", "Trang tra cứu"); ALIASES.put("trangtracuu", "lookupUrl"); ALIASES.put("tracuutai", "lookupUrl");
		LABELS.put("vatRate", "Thuế suất GTGT"); ALIASES.put("thuesuatgtgt", "vatRate"); ALIASES.put("thuesuat", "vatRate"); ALIASES.put("vatrate", "vatRate"); ALIASES.put("thuesuatgtgtvatrate", "vatRate");
		LABELS.put("bankAccount", "Số tài khoản"); ALIASES.put("sotaikhoan", "bankAccount"); ALIASES.put("bankaccount", "bankAccount");
		LABELS.put("currency", "Tiền tệ"); ALIASES.put("tiente", "currency"); ALIASES.put("donvitiente", "currency");
		LABELS.put("website", "Website"); LABELS.put("email", "Email"); LABELS.put("hotline", "Hotline");
		LABELS.put("contractNumber", "Số hợp đồng"); LABELS.put("note", "Ghi chú"); ALIASES.put("ghichu", "note");
		LABELS.put("discountAmount", "Số tiền chiết khấu"); ALIASES.put("sotienchietkhau", "discountAmount"); ALIASES.put("tienchietkhau", "discountAmount");
		LABELS.put("paymentStatus", "Trạng thái thanh toán"); ALIASES.put("trangthai", "paymentStatus"); ALIASES.put("trangthaithanhtoan", "paymentStatus");
		LABELS.put("taxCode", "Mã số thuế"); ALIASES.put("masothue", "taxCode"); ALIASES.put("taxcode", "taxCode"); ALIASES.put("masothuetaxcode", "taxCode");
		LABELS.put("address", "Địa chỉ"); ALIASES.put("diachi", "address"); ALIASES.put("diachiaddress", "address");
		LABELS.put("unitName", "Tên đơn vị"); ALIASES.put("tendonvi", "unitName"); ALIASES.put("tendonvicompanysname", "unitName"); ALIASES.put("tendonvicompanyname", "unitName");
		LABELS.put("phone", "Điện thoại"); ALIASES.put("dienthoai", "phone"); ALIASES.put("sodienthoai", "phone");
		LABELS.put("signedBy", "Ký bởi"); ALIASES.put("kyboi", "signedBy"); ALIASES.put("signedby", "signedBy");
		LABELS.put("solutionProvider", "Đơn vị cung cấp giải pháp"); ALIASES.put("donvicungcapgiaiphap", "solutionProvider");
		ALIASES.put("congtycungcapgiaiphap", "solutionProvider"); ALIASES.put("cungcapgiaiphap", "solutionProvider");
	}

	private static void label(String key, String display, String... phrases) {
		LABELS.put(key, display);
		ALIASES.put(key.toLowerCase(Locale.ROOT), key);
		for (String phrase : phrases) ALIASES.put(phrase, key);
	}

	/** Vietnamese → ASCII: removes combining marks and maps đ/Đ. */
	public static String fold(String value) {
		if (value == null) return "";
		String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return decomposed.replace('đ', 'd').replace('Đ', 'D');
	}

	/** Letters and digits only, lower case, after folding: the text used to look up aliases. */
	public static String compact(String value) {
		return fold(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
	}

	/** Stable camelCase key; identifiers already in camelCase keep their spelling. Null when nothing usable. */
	public static String key(String value) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		if (trimmed.matches("[A-Za-z][A-Za-z0-9]*")) return Character.toLowerCase(trimmed.charAt(0)) + trimmed.substring(1);
		return camel(fold(trimmed).replaceAll("[^A-Za-z0-9]+", " "));
	}

	/** The pre-fix algorithm (drops non-ASCII letters); used only to recognise stored legacy names. */
	public static String legacyKey(String value) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		if (trimmed.matches("[A-Za-z][A-Za-z0-9]*")) return Character.toLowerCase(trimmed.charAt(0)) + trimmed.substring(1);
		return camel(trimmed.replaceAll("[^A-Za-z0-9]+", " "));
	}

	/** Key to store for an AI field name: the canonical key when the label is unambiguous, else {@link #key}. */
	public static String storageKey(String value) {
		String key = key(value);
		if (key == null) return null;
		String canonical = ALIASES.get(compact(value));
		if (canonical == null) canonical = ALIASES.get(key.toLowerCase(Locale.ROOT));
		return canonical == null ? key : canonical;
	}

	public static boolean isInvoiceField(String key) {
		return key != null && INVOICE_FIELD_KEYS.contains(key);
	}

	/** Vietnamese display name for a known key, else null. */
	public static String knownLabel(String key) {
		return key == null ? null : LABELS.get(key);
	}

	/** Every key under which a field with this original AI name may have been stored (legacy and current). */
	public static List<String> storedKeyCandidates(String originalName) {
		return java.util.stream.Stream.of(legacyKey(originalName), key(originalName), storageKey(originalName))
				.filter(java.util.Objects::nonNull).distinct().toList();
	}

	private static String camel(String spaced) {
		StringBuilder normalized = new StringBuilder();
		for (String part : spaced.trim().split("\\s+")) {
			if (part.isBlank()) continue;
			String lower = part.toLowerCase(Locale.ROOT);
			if (normalized.isEmpty()) normalized.append(lower);
			else normalized.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
		}
		return normalized.isEmpty() || !Character.isLetter(normalized.charAt(0)) ? null : normalized.toString();
	}
}
