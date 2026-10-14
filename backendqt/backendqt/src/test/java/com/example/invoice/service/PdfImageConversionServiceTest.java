package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

	@Test
	void extractsUsableDigitalTextLayerWithoutRenderingOrOcr() throws Exception {
		AiProperties properties = new AiProperties();
		PdfImageConversionService service = new PdfImageConversionService(properties);
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(); pdf.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(pdf,page)) {
				content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
				content.newLineAtOffset(40,700);
				content.showText("VAT INVOICE tax code 0319174875 total amount 15984000 payment by bank transfer");
				content.endText();
			}
			pdf.save(output);
			var pages=service.extractTextPages(output.toByteArray());
			assertEquals(1,pages.size());
			assertEquals(1,pages.getFirst().pageNumber());
			assertEquals(true,pages.getFirst().usable());
		}
	}
}
