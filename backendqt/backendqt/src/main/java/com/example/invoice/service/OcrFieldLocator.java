package com.example.invoice.service;

import com.example.invoice.dto.invoice.ExtractedFieldResponse.FieldLocation;
import com.example.invoice.repository.OCRResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OcrFieldLocator {
	private final OCRResultRepository repository;
	private final ObjectMapper objectMapper;

	public List<FieldLocation> locate(Long documentId, String fieldValue) {
		if (fieldValue == null || fieldValue.isBlank()) return List.of();
		String layout = repository.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)
				.map(com.example.invoice.entity.OCRResult::getLayoutJson).orElse(null);
		if (layout == null || layout.isBlank()) return List.of();
		try {
			JsonNode root = objectMapper.readTree(layout);
			JsonNode pages = root.isArray() ? root : root.path("pages");
			for (JsonNode page : pages) {
				List<JsonNode> words = new ArrayList<>(); page.path("words").forEach(words::add);
				String target = normalize(fieldValue);
				for (int start = 0; start < words.size(); start++) {
					StringBuilder joined = new StringBuilder();
					for (int end = start; end < Math.min(words.size(), start + 12); end++) {
						joined.append(normalize(words.get(end).path("text").asText()));
						if (joined.toString().equals(target)) return List.of(union(page.path("page").asInt(1), words.subList(start, end + 1)));
						if (!target.startsWith(joined.toString())) break;
					}
				}
			}
		} catch (Exception ignored) { return List.of(); }
		return List.of();
	}

	private FieldLocation union(int page, List<JsonNode> words) {
		int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = 0, bottom = 0;
		for (JsonNode word : words) {
			int x = word.path("x").asInt(), y = word.path("y").asInt();
			left = Math.min(left, x); top = Math.min(top, y);
			right = Math.max(right, x + word.path("width").asInt()); bottom = Math.max(bottom, y + word.path("height").asInt());
		}
		return new FieldLocation(page, left, top, right - left, bottom - top);
	}

	private String normalize(String value) {
		return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
	}
}
