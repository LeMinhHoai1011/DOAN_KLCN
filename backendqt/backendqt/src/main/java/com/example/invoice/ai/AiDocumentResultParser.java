package com.example.invoice.ai;

import com.example.invoice.ai.normalize.DocumentDates;
import com.example.invoice.ai.normalize.LocalizedNumber;
import com.example.invoice.ai.normalize.TaxCodes;
import com.example.invoice.ai.normalize.TaxRateValue;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses a complete provider response without attempting to recover partial JSON.
 *
 * <p>Invoice values the model returned as text are normalized here, once: numbers, tax rates, dates and tax codes.
 * Nothing is guessed. A value that cannot be read becomes null with an {@code EXTRACTION_FAILED_*} warning instead
 * of failing the whole document; an ambiguous one is kept with an {@code AMBIGUOUS_NUMBER} warning for review.
 * Every change is recorded as a field note (raw text, kept value, status, reason).
 */
public final class AiDocumentResultParser {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(AiDocumentResultParser.class);

	private AiDocumentResultParser() {
	}

	public static AiDocumentResult parse(String provider, String model, String content, String rawResponse, long durationMs) {
		try {
			JsonNode root = OBJECT_MAPPER.readTree(stripFence(content));
			if (root instanceof ObjectNode object) normalize(object);
			logItemShape(provider, model, root);
			AiDocumentResult result = OBJECT_MAPPER.treeToValue(root, AiDocumentResult.class)
					.withMetadata(provider, model, rawResponse, durationMs);
			var items = result.invoice() == null ? null : result.invoice().items();
			long invalidItemCount = items == null ? 0 : items.stream()
					.filter(item -> item == null || item.productName() == null || item.productName().isBlank()).count();
			log.info("AI_PARSE_COMPLETED provider={} model={} invoicePresent={} itemsPresent={} itemCount={} invalidItemCount={} fieldNotes={}",
					provider, model, result.invoice() != null, items != null, items == null ? 0 : items.size(), invalidItemCount,
					result.notes().size());
			return result;
		} catch (JsonProcessingException exception) {
			log.warn("AI_JSON_PARSE_FAILED provider={} model={} errorType={} location={}", provider, model,
					exception.getClass().getSimpleName(), exception.getLocation());
			throw new AiProviderException("AI_JSON_PARSE_ERROR: nhà cung cấp không trả về JSON có cấu trúc hợp lệ", exception);
		}
	}

	/** Notes and warnings collected while normalizing one response. */
	private static final class Collector {
		final List<AiDocumentResult.AiFieldNote> notes = new ArrayList<>();
		final List<String> warnings = new ArrayList<>();

		void note(String field, Integer row, String raw, String value, String status, String reason) {
			notes.add(new AiDocumentResult.AiFieldNote(field, row, raw, value, status, reason));
		}
	}

	private static void normalize(ObjectNode root) {
		Collector collector = new Collector();
		JsonNode invoiceNode = root.path("invoice");
		if (invoiceNode instanceof ObjectNode invoice) {
			JsonNode items = invoice.has("items") ? invoice.path("items") : invoice.path("invoiceItems");
			LocalizedNumber.Convention convention = LocalizedNumber.detect(textualNumbers(invoice, items));
			normalizeNumber(invoice, "subtotal", null, LocalizedNumber.Kind.MONEY, convention, collector);
			normalizeNumber(invoice, "vatAmount", null, LocalizedNumber.Kind.MONEY, convention, collector);
			normalizeNumber(invoice, "totalAmount", null, LocalizedNumber.Kind.MONEY, convention, collector);
			normalizeDate(invoice, "invoiceDate", collector);
			normalizeDate(invoice, "signDate", collector);
			normalizeTaxCode(invoice, "sellerTaxCode", collector);
			normalizeTaxCode(invoice, "buyerTaxCode", collector);
			if (items.isArray()) {
				int row = 0;
				for (JsonNode itemNode : items) {
					row++;
					if (!(itemNode instanceof ObjectNode item)) continue;
					normalizeNumber(item, "quantity", row, LocalizedNumber.Kind.QUANTITY, convention, collector);
					normalizeNumber(item, "unitPrice", row, LocalizedNumber.Kind.PRICE, convention, collector);
					normalizeNumber(item, "taxAmount", row, LocalizedNumber.Kind.MONEY, convention, collector);
					normalizeNumber(item, "amount", row, LocalizedNumber.Kind.MONEY, convention, collector);
					normalizeTaxRate(item, row, collector);
					readDiscount(item, row, convention, collector);
					normalizeText(item);
				}
			}
		}
		ArrayNode notes = OBJECT_MAPPER.valueToTree(collector.notes);
		root.set("fieldNotes", notes);
		if (!collector.warnings.isEmpty()) {
			ArrayNode warnings = root.path("warnings").isArray() ? (ArrayNode) root.path("warnings") : root.putArray("warnings");
			collector.warnings.forEach(warnings::add);
		}
	}

	private static List<String> textualNumbers(ObjectNode invoice, JsonNode items) {
		List<String> values = new ArrayList<>();
		for (String name : List.of("subtotal", "vatAmount", "totalAmount"))
			if (invoice.path(name).isTextual()) values.add(invoice.path(name).textValue());
		if (items.isArray()) for (JsonNode item : items)
			for (String name : List.of("quantity", "unitPrice", "taxAmount", "amount", InvoiceItemRepairs.DISCOUNT_FIELD))
				if (item.path(name).isTextual()) values.add(item.path(name).textValue());
		return values;
	}

	private static void normalizeNumber(ObjectNode node, String name, Integer row, LocalizedNumber.Kind kind,
			LocalizedNumber.Convention convention, Collector collector) {
		JsonNode value = node.path(name);
		if (!value.isTextual()) return;
		LocalizedNumber.Parsed parsed = LocalizedNumber.parse(value.textValue(), kind, convention);
		String location = "field=" + name + (row == null ? "" : "; row=" + row);
		switch (parsed.status()) {
			case EMPTY -> node.putNull(name);
			case INVALID -> {
				node.putNull(name);
				collector.note(name, row, parsed.raw(), null, "INVALID", parsed.reason());
				collector.warnings.add("EXTRACTION_FAILED_" + snake(name) + ": " + location
						+ "; Không đọc được số \"" + parsed.raw() + "\"; để trống, cần nhập từ chứng từ");
			}
			case AMBIGUOUS -> {
				node.put(name, parsed.value());
				collector.note(name, row, parsed.raw(), parsed.value().toPlainString(), "AMBIGUOUS", parsed.reason());
				collector.warnings.add("AMBIGUOUS_NUMBER: " + location + "; \"" + parsed.raw() + "\" được đọc là "
						+ parsed.value().toPlainString() + (parsed.alternative() == null ? "" : " nhưng cũng có thể là "
						+ parsed.alternative().toPlainString()) + "; cần kiểm tra trên chứng từ");
			}
			default -> {
				node.put(name, parsed.value());
				collector.note(name, row, parsed.raw(), parsed.value().toPlainString(), parsed.status().name(), parsed.reason());
			}
		}
	}

	/**
	 * The invoice has no discount column, so a printed row discount is kept as a field note: the validator and the
	 * repairs use it in quantity × price = amount + discount, and the field report shows it.
	 */
	private static void readDiscount(ObjectNode item, int row, LocalizedNumber.Convention convention, Collector collector) {
		JsonNode value = item.path(InvoiceItemRepairs.DISCOUNT_FIELD);
		java.math.BigDecimal amount;
		String raw;
		String status = "VALID";
		if (value.isNumber()) { amount = value.decimalValue(); raw = amount.toPlainString(); }
		else if (value.isTextual()) {
			LocalizedNumber.Parsed parsed = LocalizedNumber.parse(value.textValue(), LocalizedNumber.Kind.MONEY, convention);
			raw = value.textValue();
			if (parsed.value() == null) {
				if (parsed.status() == LocalizedNumber.Status.INVALID)
					collector.note(InvoiceItemRepairs.DISCOUNT_FIELD, row, raw, null, "INVALID", parsed.reason());
				return;
			}
			amount = parsed.value();
			status = parsed.status().name();
		} else return;
		if (amount.signum() <= 0) return;
		collector.note(InvoiceItemRepairs.DISCOUNT_FIELD, row, raw, amount.stripTrailingZeros().toPlainString(), status, "ROW_DISCOUNT");
	}

	/** A rate returned as 8 or "8%" becomes 0.08; KCT/KKKNT keep a null rate and are recorded as such. */
	private static void normalizeTaxRate(ObjectNode item, int row, Collector collector) {
		JsonNode value = item.path("taxRate");
		TaxRateValue rate;
		if (value.isTextual()) rate = TaxRateValue.parse(value.textValue());
		else if (value.isNumber()) rate = TaxRateValue.ofNumber(value.decimalValue());
		else return;
		if (rate.status() == LocalizedNumber.Status.VALID) return;
		if (rate.rate() == null) item.putNull("taxRate"); else item.put("taxRate", rate.rate());
		if (rate.status() == LocalizedNumber.Status.EMPTY) return;
		String kept = rate.noTaxApplies() ? rate.category().name() : rate.rate() == null ? null : rate.rate().toPlainString();
		collector.note("taxRate", row, rate.raw(), kept, rate.status() == LocalizedNumber.Status.INVALID ? "INVALID" : "NORMALIZED",
				rate.noTaxApplies() ? rate.category().name() : rate.reason());
		if (rate.status() == LocalizedNumber.Status.INVALID)
			collector.warnings.add("EXTRACTION_FAILED_TAX_RATE: field=taxRate; row=" + row + "; Không đọc được thuế suất \""
					+ rate.raw() + "\"");
	}

	private static void normalizeDate(ObjectNode invoice, String name, Collector collector) {
		JsonNode value = invoice.path(name);
		if (!value.isTextual()) return;
		DocumentDates.Parsed parsed = DocumentDates.parse(value.textValue());
		switch (parsed.status()) {
			case VALID, EMPTY -> { }
			case NORMALIZED -> {
				invoice.put(name, parsed.value());
				collector.note(name, null, parsed.raw(), parsed.value(), "NORMALIZED", parsed.reason());
			}
			// The printed text stays in the result so the validator reports it and the accountant sees it.
			default -> collector.note(name, null, parsed.raw(), null, parsed.status().name(), parsed.reason());
		}
	}

	private static void normalizeTaxCode(ObjectNode invoice, String name, Collector collector) {
		JsonNode value = invoice.path(name);
		if (!value.isTextual() || value.textValue().isBlank()) return;
		TaxCodes.Parsed parsed = TaxCodes.parse(value.textValue());
		if (parsed.changed()) {
			invoice.put(name, parsed.value());
			collector.note(name, null, parsed.raw(), parsed.value(), "NORMALIZED", "TAX_CODE_FORMAT");
		}
		if (parsed.valid() && !parsed.checksumOk())
			collector.note(name, null, parsed.raw(), parsed.value(), "SUSPECT", "CHECKSUM_MISMATCH");
	}

	/** Unicode NFC and single spaces only; letters and digits are never changed. */
	private static void normalizeText(ObjectNode item) {
		for (String name : List.of("productName", "description", "itemName", "name", "productDescription", "unit")) {
			JsonNode value = item.path(name);
			if (!value.isTextual()) continue;
			String cleaned = Normalizer.normalize(value.textValue(), Normalizer.Form.NFC).replaceAll("\\s+", " ").trim();
			// Cosmetic only, so no note: the printed characters are unchanged.
			if (!cleaned.equals(value.textValue())) item.put(name, cleaned);
		}
	}

	private static String snake(String name) {
		return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
	}

	private static void logItemShape(String provider, String model, JsonNode root) {
		JsonNode invoice = root.path("invoice");
		boolean itemsKeyPresent = invoice.has("items");
		boolean invoiceItemsKeyPresent = invoice.has("invoiceItems");
		JsonNode items = itemsKeyPresent ? invoice.path("items") : invoice.path("invoiceItems");
		int objectCount = 0, productNameKeyCount = 0, descriptionKeyCount = 0, itemNameKeyCount = 0;
		if (items.isArray()) {
			for (JsonNode item : items) {
				if (!item.isObject()) continue;
				objectCount++;
				if (item.has("productName")) productNameKeyCount++;
				if (item.has("description")) descriptionKeyCount++;
				if (item.has("itemName")) itemNameKeyCount++;
			}
		}
		log.info("AI_ITEM_JSON_SHAPE provider={} model={} itemsKeyPresent={} invoiceItemsKeyPresent={} itemObjectCount={} productNameKeyCount={} descriptionKeyCount={} itemNameKeyCount={}",
				provider, model, itemsKeyPresent, invoiceItemsKeyPresent, objectCount, productNameKeyCount,
				descriptionKeyCount, itemNameKeyCount);
	}

	private static String stripFence(String content) {
		String value = content == null ? "" : content.trim();
		if (!value.startsWith("```")) {
			return value;
		}
		int firstLineEnd = value.indexOf('\n');
		if (firstLineEnd < 0 || !value.endsWith("```")) {
			return value;
		}
		return value.substring(firstLineEnd + 1, value.length() - 3).trim();
	}
}
