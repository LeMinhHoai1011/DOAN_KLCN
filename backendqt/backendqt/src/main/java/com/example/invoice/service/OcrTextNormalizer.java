package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Produces compact OCR text for AI prompts; layout coordinates never enter the prompt.
 *
 * <p>Lines carrying numbers (goods rows, totals, tax codes) are never de-duplicated: a bill can print the same
 * row twice and both rows are real. When the text exceeds the prompt budget, plain text in the middle is dropped
 * before any numeric or business line, and {@link Result#truncated()} reports that something was left out.
 */
@Component
@RequiredArgsConstructor
public class OcrTextNormalizer {
	private static final int PROMPT_OVERHEAD_TOKENS = 1200;
	private static final Pattern NUMBER = Pattern.compile("\\d");
	private static final Pattern NUMERIC_TOKEN = Pattern.compile("(?<![\\p{L}])\\d[\\d.,]*");
	private static final Pattern BUSINESS = Pattern.compile(
			".*(hóa đơn|hoa don|mã số thuế|ma so thue|mst|ký hiệu|ky hieu|số|ngày|ngay|người bán|người mua|cộng tiền|thuế|vat|tổng|tong|thanh toán|payment).*");
	private final AiProperties properties;

	/** Text sent to the model, whether lines were dropped to fit the budget, and how many. */
	public record Result(String text, boolean truncated, int droppedLines) {}

	public String normalizeAndCompact(String rawText) {
		return normalize(rawText).text();
	}

	public Result normalize(String rawText) {
		if (rawText == null || rawText.isBlank()) return new Result("", false, 0);
		List<String> lines = new ArrayList<>();
		Set<String> seenText = new HashSet<>();
		for (String rawLine : rawText.replace("\u0000", "").split("\\R")) {
			String line = rawLine.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
					.replaceAll("(.)\\1{3,}", "$1$1$1")
					.replaceAll("[ \\t]+", " ").trim();
			if (line.isBlank()) continue;
			// Repeated headers/labels are noise; repeated numeric lines can be real goods rows.
			if (!NUMBER.matcher(line).find() && !seenText.add(line)) continue;
			lines.add(line);
		}
		int tokenBudget = Math.max(512, properties.getOllama().getNumContext()
				- properties.getOcr().getReservedOutputTokens() - PROMPT_OVERHEAD_TOKENS);
		int charBudget = tokenBudget * 3;
		int maximumWords = properties.getOcr().getMaxPromptWords();
		if (maximumWords <= 0) throw new IllegalArgumentException("OCR max prompt words must be greater than 0");
		List<String> selected = fits(lines, charBudget, maximumWords) ? lines : prioritize(lines, charBudget, maximumWords);
		if (selected.isEmpty() && !lines.isEmpty()) return new Result(firstWords(String.join(" ", lines), maximumWords), true, lines.size());
		return new Result(String.join("\n", selected), selected.size() < lines.size(), lines.size() - selected.size());
	}

	private boolean fits(List<String> lines, int charBudget, int maximumWords) {
		int chars = 0, words = 0;
		for (String line : lines) { chars += line.length() + 1; words += wordCount(line); }
		return chars - 1 <= charBudget && words <= maximumWords;
	}

	/**
	 * Keeps the original order. Header and tail first, then numeric rows and business lines, then anything else,
	 * each only while the character and word budgets allow.
	 */
	private List<String> prioritize(List<String> lines, int charBudget, int maximumWords) {
		int size = lines.size();
		int headEnd = Math.max(1, size / 6), tailStart = Math.max(headEnd, size - Math.max(1, size / 6));
		boolean[] keep = new boolean[size];
		int[] used = {0, 0};
		List<Integer> order = new ArrayList<>();
		for (int index = 0; index < headEnd; index++) order.add(index);
		for (int index = size - 1; index >= tailStart; index--) order.add(index);
		for (int index = headEnd; index < tailStart; index++) if (isTableRow(lines.get(index))) order.add(index);
		for (int index = headEnd; index < tailStart; index++) if (!isTableRow(lines.get(index)) && isBusinessLine(lines.get(index))) order.add(index);
		for (int index = headEnd; index < tailStart; index++) if (!isTableRow(lines.get(index)) && !isBusinessLine(lines.get(index))) order.add(index);
		for (int index : order) {
			if (keep[index]) continue;
			String line = lines.get(index);
			int chars = line.length() + 1, words = wordCount(line);
			if (used[0] + chars > charBudget + 1 || used[1] + words > maximumWords) continue;
			keep[index] = true; used[0] += chars; used[1] += words;
		}
		List<String> result = new ArrayList<>();
		for (int index = 0; index < size; index++) if (keep[index]) result.add(lines.get(index));
		return result;
	}

	/** A goods/total row: at least two separate numbers on one line. */
	static boolean isTableRow(String line) {
		var matcher = NUMERIC_TOKEN.matcher(line);
		int count = 0;
		while (matcher.find()) if (++count >= 2) return true;
		return false;
	}

	private static boolean isBusinessLine(String value) {
		return BUSINESS.matcher(value.toLowerCase(java.util.Locale.ROOT)).matches();
	}

	private static String firstWords(String value, int maximumWords) {
		String[] words = value.trim().split("\\s+");
		return String.join(" ", java.util.Arrays.copyOf(words, Math.min(words.length, maximumWords)));
	}

	private static int wordCount(String value) { return value.isBlank() ? 0 : value.trim().split("\\s+").length; }
}
