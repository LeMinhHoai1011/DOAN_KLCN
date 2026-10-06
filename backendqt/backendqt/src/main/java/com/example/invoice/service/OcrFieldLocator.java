package com.example.invoice.service;

import com.example.invoice.dto.invoice.ExtractedFieldResponse.FieldLocation;
import com.example.invoice.repository.OCRResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves values against OCR words; reconstructed lines provide ranking context only. */
@Component
@RequiredArgsConstructor
public class OcrFieldLocator {
	private static final int MAX_WORDS_PER_VALUE = 12;
	private final OCRResultRepository repository;
	private final ObjectMapper objectMapper;

	public List<FieldLocation> locate(Long documentId, String fieldValue) {
		return locate(documentId, null, fieldValue);
	}

	public List<FieldLocation> locate(Long documentId, String fieldName, String fieldValue) {
		if (fieldValue == null || fieldValue.isBlank()) return List.of();
		String layout = repository.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)
				.map(com.example.invoice.entity.OCRResult::getLayoutJson).orElse(null);
		if (layout == null || layout.isBlank()) return List.of();
		try {
			JsonNode root = objectMapper.readTree(layout);
			JsonNode pages = root.isArray() ? root : root.path("pages");
			List<Candidate> candidates = new ArrayList<>();
			for (JsonNode page : pages) collectCandidates(page, fieldName, fieldValue, candidates);
			if (candidates.isEmpty()) return List.of();
			candidates.sort(Comparator.comparingDouble(Candidate::matchScore).reversed()
					.thenComparing(Comparator.comparingDouble(Candidate::contextScore).reversed())
					.thenComparingInt(Candidate::page).thenComparingInt(Candidate::start));
			Candidate match = candidates.getFirst();
			if (candidates.stream().filter(candidate -> sameRank(match, candidate)).count() != 1) return List.of();
			return List.of(union(match.pageNode(), match.words(), match.matchScore()));
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private void collectCandidates(JsonNode page, String fieldName, String targetValue, List<Candidate> candidates) {
		List<JsonNode> words = new ArrayList<>(); page.path("words").forEach(words::add);
		Map<String, String> lineTextByWordId = lineContext(page);
		for (int start = 0; start < words.size(); start++) {
			StringBuilder spaced = new StringBuilder();
			String startLine = lineTextByWordId.get(words.get(start).path("id").asText());
			for (int end = start; end < Math.min(words.size(), start + MAX_WORDS_PER_VALUE); end++) {
				String endLine = lineTextByWordId.get(words.get(end).path("id").asText());
				if (startLine != null && endLine != null && !startLine.equals(endLine)) break;
				if (!spaced.isEmpty()) spaced.append(' ');
				spaced.append(words.get(end).path("text").asText());
				double score = matchScore(targetValue, spaced.toString());
				if (score > 0d) candidates.add(new Candidate(page.path("page").asInt(1), start, page,
						List.copyOf(words.subList(start, end + 1)), score, contextScore(fieldName, startLine)));
			}
		}
	}

	private Map<String, String> lineContext(JsonNode page) {
		Map<String, String> result = new HashMap<>();
		for (JsonNode line : page.path("lines")) {
			String text = line.path("text").asText();
			for (JsonNode id : line.path("wordIds")) result.put(id.asText(), text);
		}
		return result;
	}

	private double contextScore(String fieldName, String lineText) {
		if (fieldName == null || lineText == null) return 0d;
		String field = normalize(fieldName), line = normalize(lineText);
		boolean seller = field.contains("seller") || field.contains("nguoiban");
		boolean buyer = field.contains("buyer") || field.contains("nguoimua");
		boolean sellerLabel = containsAny(line, "nguoiban", "donviban", "benban", "seller");
		boolean buyerLabel = containsAny(line, "nguoimua", "donvimua", "benmua", "buyer");
		if ((seller && sellerLabel) || (buyer && buyerLabel)) return 2d;
		if ((seller && buyerLabel) || (buyer && sellerLabel)) return -2d;
		if (field.contains("taxcode") || field.contains("masothue") || field.contains("mst"))
			return containsAny(line, "masothue", "mst", "taxcode") ? 1d : 0d;
		return 0d;
	}

	private boolean containsAny(String value, String... fragments) {
		for (String fragment : fragments) if (value.contains(fragment)) return true;
		return false;
	}

	private double matchScore(String expected, String actual) {
		String left = expected.trim(), right = actual.trim();
		if (left.equals(right)) return 1.00d;
		if (left.equalsIgnoreCase(right)) return 0.995d;
		String normalizedLeft = normalize(left), normalizedRight = normalize(right);
		if (!normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight)) return 0.99d;
		String numericLeft = digitsOnly(left), numericRight = digitsOnly(right);
		if (looksNumeric(left) && looksNumeric(right) && !numericLeft.isEmpty() && numericLeft.equals(numericRight)) return 0.98d;
		if (normalizedLeft.length() < 5 || Math.abs(normalizedLeft.length() - normalizedRight.length()) > 2) return 0d;
		double similarity = similarity(confusionFold(normalizedLeft), confusionFold(normalizedRight));
		double threshold = identifierLike(left) ? 0.92d : 0.86d;
		return similarity >= threshold ? Math.min(0.95d, similarity) : 0d;
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

	private boolean sameRank(Candidate left, Candidate right) {
		return Math.abs(left.matchScore() - right.matchScore()) < 0.0001d
				&& Math.abs(left.contextScore() - right.contextScore()) < 0.0001d;
	}
	private String normalize(String value) {
		return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
	}
	private String digitsOnly(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
	private boolean looksNumeric(String value) { return value != null && value.matches("[\\s+()\\-.,/]*\\d[\\d\\s+()\\-.,/]*"); }
	private boolean identifierLike(String value) { return value != null && value.matches(".*\\d.*"); }
	private String confusionFold(String value) { return value.replace('o', '0').replace('i', '1').replace('l', '1'); }
	private double similarity(String left, String right) {
		if (left.isEmpty() && right.isEmpty()) return 1d;
		int[] previous = new int[right.length() + 1];
		for (int column = 0; column <= right.length(); column++) previous[column] = column;
		for (int row = 1; row <= left.length(); row++) {
			int[] current = new int[right.length() + 1]; current[0] = row;
			for (int column = 1; column <= right.length(); column++) current[column] = Math.min(
					Math.min(current[column - 1] + 1, previous[column] + 1),
					previous[column - 1] + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1));
			previous = current;
		}
		return 1d - previous[right.length()] / (double) Math.max(left.length(), right.length());
	}

	private record Candidate(int page, int start, JsonNode pageNode, List<JsonNode> words,
			double matchScore, double contextScore) {}
}
