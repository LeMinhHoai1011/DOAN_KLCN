package com.example.invoice.service;

import com.example.invoice.dto.invoice.ExtractedFieldResponse;
import com.example.invoice.entity.AuditLog;
import com.example.invoice.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Vietnamese display names for stored extracted-field keys, without changing stored data.
 * Order: known key (catalog) → the exact original name in this document's AI_EXTRACT snapshot →
 * a known printed label whose old (accent-dropping) key is unique → unresolved (shown as the raw key).
 * A name is only used when exactly one candidate matches; nothing is guessed.
 */
@Component
@RequiredArgsConstructor
public class ExtractedFieldNameResolver {
	public static final String NAME_LEGACY_LABEL = "LEGACY_LABEL";
	private static final Pattern SUFFIXED = Pattern.compile("(.*[A-Za-z])([2-9])");
	/** Labels printed on Vietnamese e-invoices; used only to recognise rows stored with the old key algorithm. */
	static final List<String> KNOWN_PRINTED_LABELS = List.of(
			"Số tiền viết bằng chữ", "Số tiền viết bằng chữ (Amount in words)", "Số tiền bằng chữ",
			"Mã tra cứu", "Trang tra cứu", "Tra cứu tại", "Hình thức thanh toán", "Hình thức thanh toán (Payment method)",
			"Phương thức thanh toán", "Mã số thuế", "Mã số thuế (Tax code)", "Ký hiệu", "Ký hiệu (Serial)",
			"Số tài khoản", "Tên đơn vị", "Tên đơn vị (Company's name)", "Địa chỉ", "Địa chỉ (Address)",
			"Mã CQT", "Mã của CQT", "Mã cơ quan thuế cấp", "Mã của cơ quan thuế", "Ngày (Date)", "Ngày ký", "Ký ngày",
			"Ký bởi", "Đơn vị cung cấp giải pháp", "Cung cấp giải pháp", "Thuế suất GTGT", "Thuế suất GTGT (VAT rate)",
			"Tiền thuế GTGT", "Tiền thuế GTGT (VAT amount)", "Tổng cộng tiền thanh toán",
			"Tổng cộng tiền thanh toán (Total payment)", "Tổng tiền thanh toán", "Cộng tiền hàng",
			"Cộng tiền hàng (Sub total)", "Đơn vị tiền tệ", "Trạng thái", "Họ tên người mua hàng",
			"Hóa đơn giá trị gia tăng", "Phí vận chuyển", "Số tiền chiết khấu", "Ghi chú", "Thanh toán tiền mặt",
			"Số hộ chiếu", "Căn cước công dân", "Người bán hàng", "Người mua hàng");
	private static final Map<String, String> LEGACY_LABELS = legacyLabels();

	private final AuditLogRepository auditLogs;
	private final ObjectMapper json = new ObjectMapper();

	public record Resolved(String displayName, String source) {}

	public Map<String, Resolved> resolve(Long documentId, Collection<String> storedKeys) {
		Map<String, Resolved> result = new LinkedHashMap<>();
		Map<String, Set<String>> originals = null;
		for (String key : new LinkedHashSet<>(storedKeys)) {
			Resolved resolved = resolveWithoutSnapshot(key);
			if (resolved == null) {
				if (originals == null) originals = originalsFromSnapshots(documentId);
				resolved = fromOriginals(key, originals);
			}
			if (resolved == null) resolved = fromLegacyLabels(key);
			result.put(key, resolved == null ? new Resolved(null, ExtractedFieldResponse.NAME_UNRESOLVED) : resolved);
		}
		return result;
	}

	/** Catalog label, including suffixed duplicates such as taxCode2 → "Mã số thuế (2)". */
	static Resolved resolveWithoutSnapshot(String key) {
		String label = FieldNameCatalog.knownLabel(key);
		if (label != null) return new Resolved(label, ExtractedFieldResponse.NAME_CATALOG);
		Matcher suffixed = key == null ? null : SUFFIXED.matcher(key);
		if (suffixed != null && suffixed.matches() && FieldNameCatalog.knownLabel(suffixed.group(1)) != null)
			return new Resolved(FieldNameCatalog.knownLabel(suffixed.group(1)) + " (" + suffixed.group(2) + ")", ExtractedFieldResponse.NAME_CATALOG);
		return null;
	}

	static Resolved fromOriginals(String key, Map<String, Set<String>> originals) {
		Set<String> names = originals.get(key);
		return names != null && names.size() == 1
				? new Resolved(names.iterator().next(), ExtractedFieldResponse.NAME_AI_ORIGINAL) : null;
	}

	static Resolved fromLegacyLabels(String key) {
		String label = key == null ? null : LEGACY_LABELS.get(key);
		return label == null ? null : new Resolved(label, NAME_LEGACY_LABEL);
	}

	/**
	 * stored key → distinct original texts that produce that key (an AI field name, or a label), from this
	 * document's AI_EXTRACT snapshots. The text that generated the key is shown as is; a label the AI attached
	 * to another name never replaces it. Texts differing only by letter case count as one.
	 */
	Map<String, Set<String>> originalsFromSnapshots(Long documentId) {
		Map<String, Map<String, String>> byKey = new HashMap<>();
		if (documentId == null) return Map.of();
		for (AuditLog event : auditLogs.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
				DocumentAuditService.TYPE, documentId.toString())) {
			if (!"AI_EXTRACT".equals(event.getAction()) || event.getNewValue() == null) continue;
			JsonNode proposal;
			try { proposal = json.readTree(event.getNewValue()).path("aiProposal"); }
			catch (java.io.IOException unreadable) { continue; }
			for (JsonNode field : proposal.path("fields")) add(byKey, field.path("fieldName").asText(null), null);
			for (JsonNode field : proposal.path("extraFields")) {
				String name = field.path("name").asText(null), label = field.path("label").asText(null);
				// A technical name (signer, paymentCurrency) is shown through its Vietnamese label.
				boolean technical = name != null && name.trim().matches("[A-Za-z][A-Za-z0-9]*");
				add(byKey, name, technical && label != null && !label.isBlank() ? label : null);
				add(byKey, label, null);
			}
		}
		Map<String, Set<String>> originals = new HashMap<>();
		byKey.forEach((key, texts) -> originals.put(key, new LinkedHashSet<>(texts.values())));
		return originals;
	}

	private static void add(Map<String, Map<String, String>> byKey, String source, String shownAs) {
		String keySource = clean(source), display = shownAs == null ? keySource : clean(shownAs);
		if (keySource == null || display == null) return;
		for (String key : FieldNameCatalog.storedKeyCandidates(keySource))
			byKey.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
					.putIfAbsent(display.toLowerCase(java.util.Locale.ROOT), display);
	}

	private static String clean(String value) {
		if (value == null || value.isBlank()) return null;
		return Normalizer.normalize(value, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
	}

	/** Old key → printed label, only for old keys that keep at least three letters and are unique in the list. */
	private static Map<String, String> legacyLabels() {
		Map<String, Set<String>> candidates = new HashMap<>();
		for (String label : KNOWN_PRINTED_LABELS) {
			String legacy = FieldNameCatalog.legacyKey(label);
			if (legacy == null || legacy.replaceAll("[^A-Za-z]", "").length() < 3) continue;
			candidates.computeIfAbsent(legacy, ignored -> new LinkedHashSet<>()).add(label);
		}
		Map<String, String> unique = new HashMap<>();
		candidates.forEach((legacy, labels) -> { if (labels.size() == 1) unique.put(legacy, labels.iterator().next()); });
		return Map.copyOf(unique);
	}
}
