package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileValidationServiceTest {
	private final FileValidationService service = new FileValidationService(new AiProperties());

	@Test void acceptsValidPngWithVietnameseFileName() throws Exception {
		assertDoesNotThrow(() -> service.validateUpload(file("hóa-đơn.png", "image/png", image("png"))));
	}

	@Test void acceptsValidJpeg() throws Exception {
		assertDoesNotThrow(() -> service.validateUpload(file("invoice.wrong-extension", "image/jpeg", image("jpg"))));
	}

	@Test void acceptsValidPdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(output);
			assertDoesNotThrow(() -> service.validateUpload(file("document.bin", "application/pdf", output.toByteArray())));
		}
	}

	@Test void rejectsSpoofedMimeAndWrongMagicBytes() {
		assertThrows(BadRequestException.class,
				() -> service.validateUpload(file("fake.jpg", "image/jpeg", "not-an-image".getBytes())));
	}

	@Test void rejectsMimeThatDoesNotMatchValidSignature() throws Exception {
		assertThrows(BadRequestException.class,
				() -> service.validateUpload(file("image.png", "application/pdf", image("png"))));
	}

	@Test void rejectsCorruptImageWithPngSignature() {
		byte[] bytes = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
		assertThrows(BadRequestException.class, () -> service.validateUpload(file("bad.png", "image/png", bytes)));
	}

	@Test void rejectsCorruptPdfAndEmptyFile() {
		assertThrows(BadRequestException.class,
				() -> service.validateUpload(file("bad.pdf", "application/pdf", "%PDF-bad".getBytes())));
		assertThrows(BadRequestException.class,
				() -> service.validateUpload(file("empty.pdf", "application/pdf", new byte[0])));
	}

	@Test void rejectsFileAboveLimit() {
		assertThrows(BadRequestException.class, () -> service.validateUpload(
				file("large.png", "image/png", new byte[10 * 1024 * 1024 + 1])));
	}

	private MockMultipartFile file(String name, String type, byte[] bytes) {
		return new MockMultipartFile("file", name, type, bytes);
	}

	private byte[] image(String format) throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, format, output);
			return output.toByteArray();
		}
	}
}
