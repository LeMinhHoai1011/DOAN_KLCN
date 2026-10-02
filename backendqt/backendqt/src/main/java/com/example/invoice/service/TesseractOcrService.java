package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import com.sun.jna.Pointer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TesseractOcrService {
	private final AiProperties properties;

	public OcrPage recognize(byte[] imageBytes, int pageNumber) {
		long started = System.nanoTime();
		try {
			// Tesseract returns UTF-8. JNA otherwise uses the Windows ANSI code page and
			// corrupts Vietnamese characters even though the native OCR result is valid.
			System.setProperty("jna.encoding", "UTF-8");
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
			if (image == null) throw new BadRequestException("OCR_FAILED: image cannot be decoded");
			Utf8Tesseract engine = new Utf8Tesseract();
			if (!properties.getOcr().getDataPath().isBlank()) engine.setDatapath(properties.getOcr().getDataPath());
			engine.setLanguage(properties.getOcr().getLanguage());
			String text = engine.doOCRUtf8(image);
			List<Word> words = engine.getWords(image, TessPageIteratorLevel.RIL_WORD);
			double average = words.stream().filter(word -> word.getText() != null && !word.getText().isBlank())
					.mapToDouble(Word::getConfidence).average().orElse(0.0);
			BigDecimal confidence = BigDecimal.valueOf(average / 100.0).setScale(4, RoundingMode.HALF_UP);
			return new OcrPage(pageNumber, text == null ? "" : text.trim(), confidence,
					(System.nanoTime() - started) / 1_000_000);
		} catch (TesseractException exception) {
			throw new IllegalStateException("OCR_FAILED: " + exception.getMessage(), exception);
		} catch (BadRequestException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("OCR_FAILED: unable to read image", exception);
		}
	}

	public record OcrPage(int pageNumber, String text, BigDecimal confidence, long durationMs) {}

	/** Tess4J's default Pointer decoding follows the Windows code page, not Tesseract's UTF-8 contract. */
	private static final class Utf8Tesseract extends Tesseract {
		String doOCRUtf8(BufferedImage image) throws Exception {
			init();
			try {
				setImage(image);
				return getOCRText(null, 0);
			} finally {
				dispose();
			}
		}

		@Override
		protected String getOCRText(String filename, int pageNum) {
			Pointer pointer = getAPI().TessBaseAPIGetUTF8Text(getHandle());
			if (pointer == null) return "";
			try {
				int length = 0;
				while (length < 10_000_000 && pointer.getByte(length) != 0) length++;
				return new String(pointer.getByteArray(0, length), StandardCharsets.UTF_8);
			} finally {
				getAPI().TessDeleteText(pointer);
			}
		}
	}
}
