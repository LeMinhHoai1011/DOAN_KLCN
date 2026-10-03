package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Produces compact OCR text for AI prompts; layout coordinates never enter the prompt. */
@Component
@RequiredArgsConstructor
public class OcrTextNormalizer {
	private static final int PROMPT_OVERHEAD_TOKENS = 1200;
	private final AiProperties properties;

	public String normalizeAndCompact(String rawText) {
		if (rawText == null || rawText.isBlank()) return "";
		Set<String> unique = new LinkedHashSet<>();
		for (String rawLine : rawText.replace("\u0000", "").split("\\R")) {
			String line = rawLine.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
					.replaceAll("(.)\\1{3,}", "$1$1$1")
					.replaceAll("[ \\t]+", " ").trim();
			if (!line.isBlank()) unique.add(line);
		}
		List<String> lines = new ArrayList<>(unique);
		int tokenBudget = Math.max(512, properties.getOllama().getNumContext()
				- properties.getOcr().getReservedOutputTokens() - PROMPT_OVERHEAD_TOKENS);
		int charBudget = tokenBudget * 3;
		String normalized = String.join("\n", lines);
		if (normalized.length() <= charBudget) return normalized;
		return compactSections(lines, charBudget);
	}

	private String compactSections(List<String> lines, int budget) {
		if (lines.isEmpty()) return "";
		int headEnd = Math.max(1, lines.size() / 3);
		int tailStart = Math.max(headEnd, lines.size() * 2 / 3);
		List<String> priority = new ArrayList<>();
		priority.addAll(lines.subList(0, headEnd));
		for (int index = headEnd; index < tailStart; index++) {
			String line = lines.get(index);
			if (isBusinessLine(line)) priority.add(line);
		}
		priority.addAll(lines.subList(tailStart, lines.size()));
		StringBuilder result = new StringBuilder();
		for (String line : new LinkedHashSet<>(priority)) {
			if (result.length() + line.length() + 1 > budget) continue;
			if (!result.isEmpty()) result.append('\n');
			result.append(line);
		}
		return result.toString();
	}

	private boolean isBusinessLine(String value) {
		String line = value.toLowerCase(java.util.Locale.ROOT);
		return line.matches(".*(hóa đơn|hoa don|mã số thuế|ma so thue|mst|ký hiệu|ky hieu|số|ngày|ngay|người bán|người mua|cộng tiền|thuế|vat|tổng|tong|thanh toán|payment).*?");
	}
}
