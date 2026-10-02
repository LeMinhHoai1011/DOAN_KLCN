package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class PdfImageConversionServiceTest {
	@Test
	void convertsEveryPdfPageToPng() throws Exception {
		AiProperties properties = new AiProperties();
		properties.getPdf().setDpi(72);
		PdfImageConversionService service = new PdfImageConversionService(properties);
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			pdf.addPage(new PDPage());
			pdf.addPage(new PDPage());
			pdf.save(output);
			var pages = service.convert(output.toByteArray());
			assertEquals(2, pages.size());
			assertEquals("image/png", pages.get(0).contentType());
		}
	}

	@Test
	void rejectsDocumentsOverConfiguredPageLimit() throws Exception {
		AiProperties properties = new AiProperties();
		properties.getPdf().setMaxPages(1);
		PdfImageConversionService service = new PdfImageConversionService(properties);
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			pdf.addPage(new PDPage());
			pdf.addPage(new PDPage());
			pdf.save(output);
			assertThrows(BadRequestException.class, () -> service.convert(output.toByteArray()));
		}
	}
}
