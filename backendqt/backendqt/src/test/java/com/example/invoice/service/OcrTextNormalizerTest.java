package com.example.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.invoice.config.AiProperties;
import org.junit.jupiter.api.Test;

class OcrTextNormalizerTest {
	@Test
	void removesDuplicateLinesAndOcrNoise() {
		OcrTextNormalizer normalizer = new OcrTextNormalizer(new AiProperties());
		String result = normalizer.normalizeAndCompact("  HÓA   ĐƠN  \n\nHÓA   ĐƠN\nTổng::::::: 1.100.000\u0000");
		assertThat(result).contains("HÓA ĐƠN", "Tổng::: 1.100.000");
		assertThat(result.indexOf("HÓA ĐƠN")).isEqualTo(result.lastIndexOf("HÓA ĐƠN"));
	}

	@Test
	void compactsLongTextWithinContextBudgetAndKeepsHeaderAndTotals() {
		AiProperties properties = new AiProperties();
		properties.getOllama().setNumContext(2048);
		properties.getOcr().setReservedOutputTokens(512);
		OcrTextNormalizer normalizer = new OcrTextNormalizer(properties);
		String middle = java.util.stream.IntStream.range(0, 1000)
				.mapToObj(index -> "Dòng sản phẩm rất dài " + index).collect(java.util.stream.Collectors.joining("\n"));
		String result = normalizer.normalizeAndCompact("HÓA ĐƠN GIÁ TRỊ GIA TĂNG\n" + middle + "\nTỔNG TIỀN 1.100.000");
		assertThat(result).contains("HÓA ĐƠN GIÁ TRỊ GIA TĂNG", "TỔNG TIỀN 1.100.000");
		assertThat(result.length()).isLessThanOrEqualTo(1536 * 3);
	}
}
