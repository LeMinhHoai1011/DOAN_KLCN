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

	private OcrFieldLocator locator(String layout) {
		OCRResult result = new OCRResult();
		result.setLayoutJson(layout);
		when(repository.findFirstByDocumentIdOrderByProcessedAtDesc(4L)).thenReturn(Optional.of(result));
		return new OcrFieldLocator(repository, new ObjectMapper());
	}
}
