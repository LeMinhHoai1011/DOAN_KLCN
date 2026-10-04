package com.example.invoice.service;

import com.example.invoice.dto.invoice.ExtractedFieldResponse.FieldLocation;
import com.example.invoice.repository.OCRResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves extracted values against the canonical OCR layout without mutating OCR text. */
@Component
@RequiredArgsConstructor
public class OcrFieldLocator {
	private static final int MAX_WORDS_PER_VALUE = 12;
	private static final double MIN_FUZZY_SCORE = 0.90d;
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
			List<Candidate> candidates = new ArrayList<>();
			for (JsonNode page : pages) collectCandidates(page, fieldValue, candidates);
			if (candidates.isEmpty()) return List.of();
			double bestScore = candidates.stream().mapToDouble(Candidate::score).max().orElse(0d);
			List<Candidate> best = candidates.stream().filter(candidate -> Math.abs(candidate.score() - bestScore) < 0.0001d)
					.sorted(Comparator.comparingInt(Candidate::page).thenComparingInt(Candidate::start)).toList();
			// Returning no location is safer than attaching a field to one of several identical values.
			if (best.size() != 1) return List.of();
			Candidate match = best.getFirst();
			return List.of(union(match.pageNode(), match.words(), match.score()));
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private void collectCandidates(JsonNode page, String targetValue, List<Candidate> candidates) {
		List<JsonNode> words = new ArrayList<>();
		page.path("words").forEach(words::add);
		for (int start = 0; start < words.size(); start++) {
			StringBuilder spaced = new StringBuilder();
			for (int end = start; end < Math.min(words.size(), start + MAX_WORDS_PER_VALUE); end++) {
				if (!spaced.isEmpty()) spaced.append(' ');
				spaced.append(words.get(end).path("text").asText());
				double score = matchScore(targetValue, spaced.toString());
				if (score > 0d) candidates.add(new Candidate(page.path("page").asInt(1), start,
						page, List.copyOf(words.subList(start, end + 1)), score));
			}
		}
	}

	private double matchScore(String expected, String actual) {
		String left = expected.trim(), right = actual.trim();
		if (left.equals(right)) return 1.00d;
		if (left.equalsIgnoreCase(right)) return 0.99d;
		String normalizedLeft = normalize(left), normalizedRight = normalize(right);
		if (!normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight)) return 0.98d;
		String numericLeft = digitsOnly(left), numericRight = digitsOnly(right);
		if (looksNumeric(left) && looksNumeric(right) && !numericLeft.isEmpty() && numericLeft.equals(numericRight)) return 0.97d;
		if (normalizedLeft.length() < 6 || normalizedLeft.length() != normalizedRight.length()) return 0d;
		double similarity = similarity(confusionFold(normalizedLeft), confusionFold(normalizedRight));
		return similarity >= MIN_FUZZY_SCORE ? Math.min(0.94d, similarity) : 0d;
	}

	private FieldLocation union(JsonNode page, List<JsonNode> words, double score) {
		int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = 0, bottom = 0;
		for (JsonNode word : words) {
			int x = word.path("x").asInt(), y = word.path("y").asInt();
			left = Math.min(left, x); top = Math.min(top, y);
			right = Math.max(right, x + word.path("width").asInt());
			bottom = Math.max(bottom, y + word.path("height").asInt());
		}
		return new FieldLocation(page.path("page").asInt(1), left, top, right - left, bottom - top,
				page.path("width").asInt(), page.path("height").asInt(), BigDecimal.valueOf(score));
	}

	private String normalize(String value) {
		return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
	}

	private String digitsOnly(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
	private boolean looksNumeric(String value) { return value != null && value.matches("[\\s+()\\-.,/]*\\d[\\d\\s+()\\-.,/]*"); }
	private String confusionFold(String value) { return value.replace('o', '0').replace('i', '1').replace('l', '1'); }

	private double similarity(String left, String right) {
		int differences = 0;
		for (int index = 0; index < left.length(); index++) if (left.charAt(index) != right.charAt(index)) differences++;
		return 1d - ((double) differences / left.length());
	}

	private record Candidate(int page, int start, JsonNode pageNode, List<JsonNode> words, double score) {}
}
