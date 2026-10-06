package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.invoice.entity.OCRResult;
import com.example.invoice.repository.OCRResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcrFieldLocatorTest {
	@Mock OCRResultRepository repository;

	@Test
	void exactMatchReturnsPageDimensionsCoordinatesAndConfidence() {
		OcrFieldLocator locator = locator("""
				{"version":1,"pages":[{"page":2,"width":2480,"height":3508,"words":[
				{"id":"1","text":"41NVNO036","confidence":92.4,"x":1200,"y":320,"width":180,"height":35}]}]}
				""");
		var boxes = locator.locate(4L, "41NVNO036");
		assertEquals(1, boxes.size());
		assertEquals(2, boxes.getFirst().page());
		assertEquals(2480, boxes.getFirst().pageWidth());
		assertEquals(3508, boxes.getFirst().pageHeight());
		assertEquals(0, boxes.getFirst().matchConfidence().compareTo(BigDecimal.ONE));
	}

	@Test
	void normalizedMultiWordMatchWorksButAmbiguousMatchReturnsNothing() {
		OcrFieldLocator unique = locator("""
				{"pages":[{"page":1,"width":1000,"height":1000,"words":[
				{"text":"0312","x":1,"y":2,"width":3,"height":4},{"text":"345","x":5,"y":2,"width":3,"height":4},
				{"text":"678","x":9,"y":2,"width":3,"height":4}]}]}
				""");
		assertEquals(1, unique.locate(4L, "0312-345-678").size());

		OcrFieldLocator ambiguous = locator("""
				{"pages":[{"page":1,"width":1000,"height":1000,"words":[
				{"text":"15000000","x":1,"y":2,"width":3,"height":4},
				{"text":"15000000","x":9,"y":2,"width":3,"height":4}]}]}
				""");
		assertTrue(ambiguous.locate(4L, "15.000.000").isEmpty());
	}

	@Test
	void reconstructedLineContextDisambiguatesSellerAndBuyerTaxCodes() {
		OcrFieldLocator locator = locator("""
				{"version":2,"pages":[{"page":1,"width":1000,"height":1000,"words":[
				{"id":"w1","text":"Người bán MST","x":10,"y":10,"width":100,"height":20},
				{"id":"w2","text":"0312345678","x":120,"y":10,"width":100,"height":20},
				{"id":"w3","text":"Người mua MST","x":10,"y":100,"width":100,"height":20},
				{"id":"w4","text":"0312345678","x":120,"y":100,"width":100,"height":20}],
				"lines":[{"text":"Người bán MST 0312345678","wordIds":["w1","w2"]},
				{"text":"Người mua MST 0312345678","wordIds":["w3","w4"]}]}]}
				""");

		var seller = locator.locate(4L, "sellerTaxCode", "0312345678");
		var buyer = locator.locate(4L, "buyerTaxCode", "0312345678");
		assertEquals(120, seller.getFirst().x());
		assertEquals(10, seller.getFirst().y());
		assertEquals(100, buyer.getFirst().y());
	}

	@Test
	void controlledFuzzyAcceptsOneOcrConfusionButRejectsWeakMatch() {
		OcrFieldLocator locator = locator("""
				{"pages":[{"page":1,"width":1000,"height":1000,"words":[
				{"text":"41NVN0036","x":1,"y":2,"width":80,"height":10}]}]}
				""");
		assertEquals(1, locator.locate(4L, "41NVNO036").size());
		assertTrue(locator.locate(4L, "UNRELATED-VALUE").isEmpty());
	}

	private OcrFieldLocator locator(String layout) {
		OCRResult result = new OCRResult();
		result.setLayoutJson(layout);
		when(repository.findFirstByDocumentIdOrderByProcessedAtDesc(4L)).thenReturn(Optional.of(result));
		return new OcrFieldLocator(repository, new ObjectMapper());
	}
}
