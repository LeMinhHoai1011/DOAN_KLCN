package com.example.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.invoice.config.AiProperties;
import org.junit.jupiter.api.Test;

class OcrTextNormalizerTest {
	@Test
	void enforcesConfiguredMaximumWordCount() {
		AiProperties properties = new AiProperties();
		properties.getOcr().setMaxPromptWords(3);
		String result = new OcrTextNormalizer(properties).normalizeAndCompact("one two three four five");
		assertThat(result.split("\\s+")).hasSize(3);
	}
	@Test
	void removesDuplicateLinesAndOcrNoise() {
		OcrTextNormalizer normalizer = new OcrTextNormalizer(new AiProperties());
		String result = normalizer.normalizeAndCompact("  HÓA   ĐƠN  \n\nHÓA   ĐƠN\nTổng::::::: 1.100.000\u0000");
		assertThat(result).contains("HÓA ĐƠN", "Tổng::: 1.100.000");
		assertThat(result.indexOf("HÓA ĐƠN")).isEqualTo(result.lastIndexOf("HÓA ĐƠN"));
	}

	@Test
	void preservesVietnameseUnicodeAndDiacritics() {
		String source = "Đường Nguyễn Văn Linh, Phường Tân Phong";
		String result = new OcrTextNormalizer(new AiProperties()).normalizeAndCompact(source);
		assertThat(result).isEqualTo(source);
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

	@Test
	void keepsRepeatedGoodsRowsBecauseBothAreCharged() {
		String result = new OcrTextNormalizer(new AiProperties())
				.normalizeAndCompact("Nước suối Chai 2 5.000 10.000\nNước suối Chai 2 5.000 10.000\nTổng cộng 20.000");
		assertThat(result.split("\\R")).containsExactly("Nước suối Chai 2 5.000 10.000",
				"Nước suối Chai 2 5.000 10.000", "Tổng cộng 20.000");
	}

	@Test
	void reportsTruncationAndKeepsTableRowsBeforePlainText() {
		AiProperties properties = new AiProperties();
		properties.getOcr().setMaxPromptWords(60);
		StringBuilder source = new StringBuilder("HÓA ĐƠN GIÁ TRỊ GIA TĂNG\n");
		for (int index = 0; index < 6; index++) source.append("Lời cảm ơn quý khách lần thứ ").append((char) ('A' + index)).append(" đã mua hàng\n");
		for (int index = 1; index <= 5; index++) source.append(index).append(" Ống nhựa Mét 10 5.000 50.000\n");
		source.append("Tổng cộng tiền thanh toán 250.000");
		OcrTextNormalizer.Result result = new OcrTextNormalizer(properties).normalize(source.toString());
		assertThat(result.truncated()).isTrue();
		assertThat(result.droppedLines()).isPositive();
		for (int index = 1; index <= 5; index++) assertThat(result.text()).contains(index + " Ống nhựa Mét 10 5.000 50.000");
		assertThat(result.text()).contains("HÓA ĐƠN GIÁ TRỊ GIA TĂNG", "Tổng cộng tiền thanh toán 250.000");
	}

	@Test
	void shortTextIsNotTruncated() {
		OcrTextNormalizer.Result result = new OcrTextNormalizer(new AiProperties()).normalize("HÓA ĐƠN\nTổng 1.000");
		assertThat(result.truncated()).isFalse();
		assertThat(result.droppedLines()).isZero();
	}
}
