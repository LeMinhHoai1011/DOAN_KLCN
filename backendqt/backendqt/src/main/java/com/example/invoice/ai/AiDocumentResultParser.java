package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses a complete provider response without attempting to recover partial JSON. */
public final class AiDocumentResultParser {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(AiDocumentResultParser.class);

	private AiDocumentResultParser() {
	}

	public static AiDocumentResult parse(String provider, String model, String content, String rawResponse, long durationMs) {
		try {
			JsonNode root = OBJECT_MAPPER.readTree(stripFence(content));
			logItemShape(provider, model, root);
			AiDocumentResult result = OBJECT_MAPPER.treeToValue(root, AiDocumentResult.class)
					.withMetadata(provider, model, rawResponse, durationMs);
			var items = result.invoice() == null ? null : result.invoice().items();
			long invalidItemCount = items == null ? 0 : items.stream()
					.filter(item -> item == null || item.productName() == null || item.productName().isBlank()).count();
			log.info("AI_PARSE_COMPLETED provider={} model={} invoicePresent={} itemsPresent={} itemCount={} invalidItemCount={}",
					provider, model, result.invoice() != null, items != null, items == null ? 0 : items.size(), invalidItemCount);
			return result;
		} catch (JsonProcessingException exception) {
			log.warn("AI_JSON_PARSE_FAILED provider={} model={} errorType={} location={}", provider, model,
					exception.getClass().getSimpleName(), exception.getLocation());
			throw new AiProviderException("AI_JSON_PARSE_ERROR: nhà cung cấp không trả về JSON có cấu trúc hợp lệ", exception);
		}
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
