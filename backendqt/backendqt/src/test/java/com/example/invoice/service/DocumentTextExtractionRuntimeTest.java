package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.invoice.config.AiProperties;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TESSDATA_PATH", matches = ".+")
class DocumentTextExtractionRuntimeTest {
	private AiProperties properties;
	private DocumentTextExtractionService service;

	@BeforeEach
	void setup() {
		properties = new AiProperties();
		properties.getOcr().setDataPath(System.getenv("TESSDATA_PATH"));
		properties.getOcr().setLanguage(System.getenv().getOrDefault("TESSERACT_LANGUAGE", "eng"));
		properties.getPdf().setMaxPages(10);
		ImagePreprocessingService preprocessing = new ImagePreprocessingService(properties);
		service = new DocumentTextExtractionService(properties, preprocessing,
				new PdfImageConversionService(properties), new TesseractOcrService(properties));
	}

	@Test
	void imageRunsRealTess4jAndReturnsNormalizedConfidence() throws Exception {
		byte[] image = image("INVOICE NO INV-2026-001", "TAX CODE 0312345678", "DATE 2026-10-02", "TOTAL 15800000 VND");
		DocumentTextExtractionResult result = service.extract(image, "image/png");
		System.out.println("RUNTIME_IMAGE_OCR=" + result.text().replace('\n', '|'));
		System.out.println("RUNTIME_IMAGE_CONFIDENCE=" + result.confidence());
		assertEquals("IMAGE_OCR", result.sourceType());
		assertTrue(result.text().contains("0312345678"));
		assertTrue(result.text().contains("15800000"));
		assertTrue(result.confidence().compareTo(BigDecimal.ZERO) > 0 && result.confidence().compareTo(BigDecimal.ONE) <= 0);
	}

	@Test
	void digitalPdfUsesPdfboxAndKeepsTwoPageBoundaries() throws Exception {
		byte[] pdf = digitalPdf();
		java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
		java.nio.file.Files.write(java.nio.file.Path.of("target", "runtime-digital.pdf"), pdf);
		DocumentTextExtractionResult result = service.extract(pdf, "application/pdf");
		System.out.println("RUNTIME_DIGITAL_PDF=" + result.text().replace('\n', '|'));
		assertEquals("DIGITAL_PDF_TEXT", result.sourceType());
		assertEquals("pdfbox", result.engine());
		assertTrue(result.text().contains("[PAGE 1]") && result.text().contains("[PAGE 2]"));
		assertTrue(result.text().contains("VAT 100000") && result.text().contains("TOTAL 1100000"));
	}

	@Test
	void scannedPdfRunsOcrForEveryPage() throws Exception {
		byte[] pdf = imagePdf(false);
		java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
		java.nio.file.Files.write(java.nio.file.Path.of("target", "runtime-scanned.pdf"), pdf);
		DocumentTextExtractionResult result = service.extract(pdf, "application/pdf");
		System.out.println("RUNTIME_SCANNED_PDF=" + result.text().replace('\n', '|'));
		assertEquals("SCANNED_PDF_OCR", result.sourceType());
		assertEquals(2, result.pages().size());
		assertTrue(result.text().contains("[PAGE 1]") && result.text().contains("[PAGE 2]"));
		assertTrue(result.text().contains("0312345678") && result.text().contains("1100000"));
	}

	@Test
	void mixedPdfUsesTextLayerThenOcrProblematicPage() throws Exception {
		DocumentTextExtractionResult result = service.extract(imagePdf(true), "application/pdf");
		System.out.println("RUNTIME_MIXED_PDF=" + result.text().replace('\n', '|'));
		assertEquals("SCANNED_PDF_OCR", result.sourceType());
		assertNull(result.pages().get(0).ocrConfidence());
		assertEquals("TEXT_LAYER", result.pages().get(0).extractionMethod());
		assertTrue(result.pages().get(1).confidence().compareTo(BigDecimal.ONE) < 0);
		assertTrue(result.text().contains("INV-2026-001") && result.text().contains("TOTAL 1100000"));
	}

	@Test
	void vietnameseImageRunsRealViePlusEngModel() throws Exception {
		assertEquals("vie+eng", properties.getOcr().getLanguage());
		byte[] image = vietnameseImage(
				"HÓA ĐƠN GIÁ TRỊ GIA TĂNG / VAT INVOICE",
				"CÔNG TY CỔ PHẦN ÁNH DƯƠNG",
				"Địa chỉ: 123 Nguyễn Huệ, Quận 1, Thành phố Hồ Chí Minh",
				"Mã số thuế: 0312345678    Ngày hóa đơn: 02/10/2026",
				"Tiền thuế GTGT: 1.580.000 VND",
				"Tổng tiền thanh toán: 17.380.000 VND");
		java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
		java.nio.file.Files.write(java.nio.file.Path.of("target", "vietnamese-ocr-fixture.png"), image);
		java.nio.file.Files.write(java.nio.file.Path.of("target", "vietnamese-ocr-preprocessed.png"),
				new ImagePreprocessingService(properties).preprocess(image, "image/png").bytes());
		DocumentTextExtractionResult result = service.extract(image, "image/png");
		String text = result.text();
		System.out.println("RUNTIME_VIETNAMESE_OCR=" + text.replace('\n', '|'));
		assertTrue(text.contains("HÓA ĐƠN GIÁ TRỊ GIA TĂNG"));
		assertTrue(text.contains("CÔNG TY") && text.contains("DƯƠNG"));
		assertTrue(text.contains("Nguyễn Huệ"));
		assertTrue(text.contains("0312345678"));
		assertTrue(text.contains("02/10/2026"));
		assertTrue(text.contains("thuế GTGT"));
		assertTrue(text.contains("17.380.000 VND"));
	}

	private byte[] image(String... lines) throws Exception {
		BufferedImage image = new BufferedImage(1500, 650, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1500, 650);
		graphics.setColor(Color.BLACK); graphics.setFont(new Font("Arial", Font.PLAIN, 52));
		int y = 100; for (String line : lines) { graphics.drawString(line, 60, y); y += 115; } graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
	}

	private byte[] vietnameseImage(String... lines) throws Exception {
		BufferedImage image = new BufferedImage(2400, 1050, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics(); graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 2400, 1050);
		graphics.setColor(Color.BLACK); graphics.setFont(new Font("Arial", Font.PLAIN, 48));
		int y = 110; for (String line : lines) { graphics.drawString(line, 60, y); y += 150; } graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
	}

	private byte[] digitalPdf() throws Exception {
		try (PDDocument pdf = new PDDocument()) {
			addTextPage(pdf, "INVOICE INV-2026-001 SELLER ABC TAX CODE 0312345678 DATE 2026-10-02 ITEM A 1000000");
			addTextPage(pdf, "ITEM B 0 VAT 100000 TOTAL 1100000 VND FINAL INVOICE PAYMENT INFORMATION");
			ByteArrayOutputStream output = new ByteArrayOutputStream(); pdf.save(output); return output.toByteArray();
		}
	}

	private byte[] imagePdf(boolean mixed) throws Exception {
		try (PDDocument pdf = new PDDocument()) {
			if (mixed) addTextPage(pdf, "INVOICE INV-2026-001 SELLER ABC TAX CODE 0312345678 DATE 2026-10-02 ITEM A 1000000");
			else addImagePage(pdf, image("INVOICE INV-2026-001", "TAX CODE 0312345678", "ITEM A 1000000"));
			addImagePage(pdf, image("ITEM B 0", "VAT 100000", "TOTAL 1100000 VND"));
			ByteArrayOutputStream output = new ByteArrayOutputStream(); pdf.save(output); return output.toByteArray();
		}
	}

	private void addTextPage(PDDocument pdf, String text) throws Exception {
		PDPage page = new PDPage(); pdf.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
			stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 13);
			stream.newLineAtOffset(40, 700); stream.showText(text); stream.endText();
		}
	}

	private void addImagePage(PDDocument pdf, byte[] bytes) throws Exception {
		BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes)); PDPage page = new PDPage(); pdf.addPage(page);
		try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
			stream.drawImage(LosslessFactory.createFromImage(pdf, image), 20, 250, 570, 250);
		}
	}
}
