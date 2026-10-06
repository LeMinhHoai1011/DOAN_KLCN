package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OcrLayoutReconstructorTest {
	private final OcrLayoutReconstructor reconstructor = new OcrLayoutReconstructor();

	@Test
	void groupsSlightlyMisalignedWordsAndPreservesVietnameseAndWordBoxes() {
		OcrWord first = word("p1-w1", "HÓA", .10, .10, .08, .025, 96);
		OcrWord second = word("p1-w2", "ĐƠN", .19, .105, .08, .024, 90);
		OcrPageResult result = reconstructor.reconstruct(page(1, first, second));

		assertEquals(1, result.lines().size());
		assertEquals("HÓA ĐƠN", result.lines().getFirst().text());
		assertEquals(List.of("p1-w1", "p1-w2"), result.lines().getFirst().wordIds());
		assertEquals(.10, result.lines().getFirst().x(), .0001);
		assertEquals(.17, result.lines().getFirst().width(), .0001);
		assertSame(first, result.words().getFirst());
		assertEquals(93f, result.lines().getFirst().confidence(), .01f);
	}

	@Test
	void splitsLargeHorizontalGapAndOrdersMultipleRowsTopToBottom() {
		OcrPageResult result = reconstructor.reconstruct(page(1,
				word("p1-w3", "dưới", .10, .30, .08, .02, 90),
				word("p1-w1", "trái", .05, .10, .05, .02, 90),
				word("p1-w2", "phải", .72, .10, .06, .02, 90)));

		assertEquals(List.of("trái", "phải", "dưới"), result.lines().stream().map(OcrLine::text).toList());
		assertEquals(List.of("p1-l1", "p1-l2", "p1-l3"), result.lines().stream().map(OcrLine::id).toList());
	}

	@Test
	void separatesDistantBlocksAndKeepsPageTraceability() {
		OcrPageResult firstPage = reconstructor.reconstruct(page(1,
				word("p1-w1", "Người bán", .10, .08, .20, .02, 90),
				word("p1-w2", "MST 0312345678", .10, .115, .25, .02, 90),
				word("p1-w3", "Tổng cộng", .10, .75, .20, .02, 90)));
		OcrPageResult secondPage = reconstructor.reconstruct(page(2, word("p2-w1", "Trang hai", .1, .1, .2, .02, 90)));

		assertEquals(2, firstPage.blocks().size());
		assertEquals(List.of("p1-l1", "p1-l2"), firstPage.blocks().getFirst().lineIds());
		assertEquals("p2-b1", secondPage.blocks().getFirst().id());
		assertEquals(2, secondPage.blocks().getFirst().pageNumber());
	}

	@Test
	void clampsParentBoundsSkipsInvalidGeometryAndProducesCompactContext() {
		OcrWord original = word("p3-w1", "Tổng tiền", -.02, .96, .30, .08, 88);
		OcrWord invalid = word("p3-w2", "bad", .2, .2, -1, .02, 10);
		OcrPageResult result = reconstructor.reconstruct(page(3, original, invalid));

		assertEquals(2, result.words().size());
		assertEquals(1, result.lines().size());
		assertTrue(result.lines().getFirst().x() >= 0 && result.lines().getFirst().y() >= 0);
		assertTrue(result.lines().getFirst().x() + result.lines().getFirst().width() <= 1);
		assertTrue(result.lines().getFirst().y() + result.lines().getFirst().height() <= 1);
		String context = reconstructor.structuredContext(result);
		assertTrue(context.contains("[PAGE 3]"));
		assertTrue(context.contains("[BLOCK p3-b1]"));
		assertTrue(context.contains("Tổng tiền"));
		assertFalse(context.matches("(?s).*0\\.9[0-9].*"));
	}

	private OcrPageResult page(int page, OcrWord... words) {
		return new OcrPageResult(page, 1000, 1400, "raw", 90, List.of(words), 5, null, "vie+eng");
	}
	private OcrWord word(String id, String text, double x, double y, double width, double height, float confidence) {
		return new OcrWord(id, text, confidence, x, y, width, height);
	}
}
