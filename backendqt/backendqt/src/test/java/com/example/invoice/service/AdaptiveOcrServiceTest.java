package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.invoice.config.AiProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveOcrServiceTest {
	private final AiProperties properties=new AiProperties();
	private final ImageQualityAnalyzer analyzer=mock(ImageQualityAnalyzer.class);
	private final OpenCvImageProcessor processor=mock(OpenCvImageProcessor.class);
	private final Tess4jOcrService ocr=mock(Tess4jOcrService.class);
	private AdaptiveOcrService service;

	@BeforeEach void setUp() {
		service=new AdaptiveOcrService(properties,analyzer,processor,ocr);
		when(analyzer.analyze(any())).thenReturn(new ImageQualityResult(170,50,500,0,0,100,100,.8));
		for(var profile:OcrProcessingProfile.values()) when(processor.process(any(),eq(profile),any()))
				.thenReturn(new OpenCvImageProcessor.ProcessedImage(new byte[]{(byte)(profile.ordinal()+2)},"image/png",profile,100,100,100,100,0,List.of()));
	}

	@Test void goodNormalExitsAfterOneAttempt() {
		when(ocr.recognize(any(),eq(1))).thenReturn(page(90,12,180));
		var result=service.recognize(new byte[]{1},"image/png",1);
		assertEquals(OcrProcessingProfile.NORMAL,result.selectedProfile()); assertEquals(1,result.attempts());
		verify(ocr,times(1)).recognize(any(),eq(1));
	}

	@Test void poorNormalAndGoodEnhancedSelectsEnhanced() {
		when(ocr.recognize(any(),eq(1))).thenReturn(page(20,1,5),page(92,12,180));
		var result=service.recognize(new byte[]{1},"image/png",1);
		assertEquals(OcrProcessingProfile.ENHANCED,result.selectedProfile()); assertEquals(2,result.attempts());
	}

	@Test void aggressiveIsSelectedWhenFirstTwoArePoor() {
		when(ocr.recognize(any(),eq(1))).thenReturn(page(20,1,5),page(35,2,12),page(94,14,200));
		assertEquals(OcrProcessingProfile.AGGRESSIVE,service.recognize(new byte[]{1},"image/png",1).selectedProfile());
	}

	@Test void enhancedRemainsSelectedWhenAggressiveIsWorse() {
		properties.getOcr().getAdaptive().setGoodQualityScore(.99);
		when(ocr.recognize(any(),eq(1))).thenReturn(page(20,1,5),page(88,10,160),page(40,3,30));
		assertEquals(OcrProcessingProfile.ENHANCED,service.recognize(new byte[]{1},"image/png",1).selectedProfile());
	}

	@Test void enhancedFailureRetainsUsableNormalResult() {
		properties.getOcr().getAdaptive().setGoodQualityScore(.99); properties.getOcr().getAdaptive().setMaxAttempts(2);
		when(processor.process(any(),eq(OcrProcessingProfile.ENHANCED),any())).thenThrow(new IllegalStateException("boom"));
		when(ocr.recognize(any(),eq(1))).thenReturn(page(70,6,80));
		var result=service.recognize(new byte[]{1},"image/png",1);
		assertEquals(OcrProcessingProfile.NORMAL,result.selectedProfile()); assertTrue(result.usable(properties));
	}

	@Test void upscaleKeepsNormalizedBoundingBoxInOriginalCoordinateSystem() {
		when(processor.process(any(),eq(OcrProcessingProfile.NORMAL),any())).thenReturn(new OpenCvImageProcessor.ProcessedImage(
				new byte[]{2},"image/png",OcrProcessingProfile.NORMAL,100,50,200,100,0,List.of("upscale")));
		OcrWord word=new OcrWord("p1-w1","TOTAL",95,.2,.3,.4,.2);
		when(ocr.recognize(any(),eq(1))).thenReturn(new OcrPageResult(1,200,100,"TOTAL invoice value text long enough",95,List.of(word),1,null,"eng"));
		OcrWord mapped=service.recognize(new byte[]{1},"image/png",1).page().words().getFirst();
		assertEquals(.2,mapped.x(),1e-9); assertEquals(.4,mapped.width(),1e-9);
	}

	@Test void deskewMapsBoxBackIntoOriginalCoordinateSystem() {
		when(processor.process(any(),eq(OcrProcessingProfile.NORMAL),any())).thenReturn(new OpenCvImageProcessor.ProcessedImage(
				new byte[]{2},"image/png",OcrProcessingProfile.NORMAL,100,100,100,100,10,List.of("deskew")));
		OcrWord word=new OcrWord("p1-w1","TOTAL",95,.4,.4,.2,.2);
		when(ocr.recognize(any(),eq(1))).thenReturn(new OcrPageResult(1,100,100,"TOTAL invoice value text long enough",95,List.of(word),1,null,"eng"));
		OcrWord mapped=service.recognize(new byte[]{1},"image/png",1).page().words().getFirst();
		assertEquals(.5,mapped.x()+mapped.width()/2,1e-9); assertEquals(.5,mapped.y()+mapped.height()/2,1e-9);
		assertTrue(mapped.x()>=0 && mapped.y()>=0 && mapped.x()+mapped.width()<=1 && mapped.y()+mapped.height()<=1);
	}

	private OcrPageResult page(float confidence,int words,int textLength) {
		String text="A".repeat(textLength); java.util.ArrayList<OcrWord> list=new java.util.ArrayList<>();
		for(int i=0;i<words;i++) list.add(new OcrWord("w"+i,"Word"+i,confidence,.01*i,.1,.05,.05));
		return new OcrPageResult(1,100,100,text,confidence,List.copyOf(list),1,null,"eng");
	}
}
