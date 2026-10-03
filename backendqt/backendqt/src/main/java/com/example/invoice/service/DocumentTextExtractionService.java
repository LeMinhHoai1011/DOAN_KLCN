package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentTextExtractionService {
	private final AiProperties properties;
	private final ImagePreprocessingService preprocessingService;
	private final PdfImageConversionService pdfImageConversionService;
	private final TesseractOcrService tesseractOcrService;

	public DocumentTextExtractionResult extract(byte[] bytes, String contentType) {
		if (bytes == null || bytes.length == 0) throw new BadRequestException("OCR_FAILED: nội dung chứng từ bị trống");
		if ("application/pdf".equalsIgnoreCase(contentType)) return extractPdf(bytes);
		if (contentType != null && contentType.toLowerCase().startsWith("image/")) return extractImage(bytes, contentType);
		throw new BadRequestException("OCR_FAILED: không hỗ trợ loại nội dung của chứng từ");
	}

	private DocumentTextExtractionResult extractImage(byte[] bytes, String contentType) {
		ImagePreprocessingResult processed = preprocessingService.preprocess(bytes, contentType);
		TesseractOcrService.OcrPage page = tesseractOcrService.recognize(processed.bytes(), 1);
		List<String> warnings = qualityWarnings(page.text(), page.confidence());
		return result(List.of(ocrPage(1, page.text(), page.confidence())),
				"tesseract", "tess4j-5.13.0", "IMAGE_OCR", page.durationMs(), warnings);
	}

	private DocumentTextExtractionResult extractPdf(byte[] bytes) {
		long started = System.nanoTime();
		List<DocumentTextExtractionResult.PageText> digitalPages = extractPdfTextPages(bytes);
		boolean allUsable = digitalPages.stream().allMatch(page -> isUsable(page.text()));
		if (allUsable) {
			return result(digitalPages, "pdfbox", "3.0.4", "DIGITAL_PDF_TEXT",
					(System.nanoTime() - started) / 1_000_000, List.of());
		}

		List<DocumentTextExtractionResult.PageText> pages = new ArrayList<>();
		long ocrDuration = 0;
		for (PdfPageImage rendered : pdfImageConversionService.convert(bytes)) {
			DocumentTextExtractionResult.PageText digital = digitalPages.get(rendered.pageNumber() - 1);
			if (isUsable(digital.text())) {
				pages.add(digital);
				continue;
			}
			ImagePreprocessingResult processed = preprocessingService.preprocess(rendered.bytes(), rendered.contentType());
			TesseractOcrService.OcrPage ocr = tesseractOcrService.recognize(processed.bytes(), rendered.pageNumber());
			ocrDuration += ocr.durationMs();
			pages.add(ocrPage(rendered.pageNumber(), ocr.text(), ocr.confidence()));
		}
		List<String> warnings = pages.stream().flatMap(page -> qualityWarnings(page.text(), page.confidence()).stream()).distinct().toList();
		return result(pages, "tesseract", "tess4j-5.13.0", "SCANNED_PDF_OCR", ocrDuration, warnings);
	}

	private List<DocumentTextExtractionResult.PageText> extractPdfTextPages(byte[] bytes) {
		try (PDDocument document = Loader.loadPDF(bytes)) {
			if (document.getNumberOfPages() == 0) throw new BadRequestException("PDF_EXTRACTION_FAILED: tệp PDF không có trang nào");
			if (document.getNumberOfPages() > properties.getPdf().getMaxPages())
				throw new BadRequestException("PDF_EXTRACTION_FAILED: tệp PDF vượt quá giới hạn số trang đã cấu hình");
			PDFTextStripper stripper = new PDFTextStripper();
			List<DocumentTextExtractionResult.PageText> pages = new ArrayList<>();
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				stripper.setStartPage(page); stripper.setEndPage(page);
				String text = normalize(stripper.getText(document));
				pages.add(new DocumentTextExtractionResult.PageText(page, "TEXT_LAYER", text, null,
						isUsable(text) ? BigDecimal.ONE : BigDecimal.ZERO, !isUsable(text)));
			}
			return List.copyOf(pages);
		} catch (BadRequestException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("PDF_EXTRACTION_FAILED: không thể trích xuất văn bản từ tệp PDF", exception);
		}
	}

	private boolean isUsable(String text) {
		if (text == null) return false;
		String compact = text.replaceAll("\\s+", "");
		if (compact.length() < properties.getOcr().getMinimumTextLength()) return false;
		long readable = compact.codePoints().filter(cp -> Character.isLetterOrDigit(cp) || Character.isWhitespace(cp)).count();
		long replacement = compact.chars().filter(cp -> cp == '\uFFFD').count();
		return replacement == 0 && readable >= Math.ceil(compact.length() * 0.65);
	}

	private List<String> qualityWarnings(String text, BigDecimal confidence) {
		List<String> warnings = new ArrayList<>();
		if (!isUsable(text)) warnings.add("OCR text is too short or unreadable");
		if (confidence == null || confidence.compareTo(properties.getOcr().getGoodConfidence()) < 0)
			warnings.add("OCR confidence is below the configured quality threshold");
		return List.copyOf(warnings);
	}

	private DocumentTextExtractionResult.PageText ocrPage(int pageNumber, String text, BigDecimal confidence) {
		boolean usable = isUsable(text);
		boolean goodConfidence = confidence != null && confidence.compareTo(properties.getOcr().getGoodConfidence()) >= 0;
		BigDecimal quality = usable && confidence != null ? confidence : BigDecimal.ZERO;
		return new DocumentTextExtractionResult.PageText(pageNumber, "TESSERACT_OCR", text, confidence,
				quality, !usable || !goodConfidence);
	}

	private DocumentTextExtractionResult result(List<DocumentTextExtractionResult.PageText> pages, String engine,
			String version, String sourceType, long duration, List<String> warnings) {
		String text = pages.stream().map(page -> "[PAGE " + page.pageNumber() + "]\n" + page.text())
				.reduce((left, right) -> left + "\n\n" + right).orElse("");
		BigDecimal confidence = pages.stream().map(DocumentTextExtractionResult.PageText::confidence)
				.filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(Math.max(1, pages.size())), 4, RoundingMode.HALF_UP);
		boolean fallback = pages.stream().anyMatch(DocumentTextExtractionResult.PageText::requiresVisionFallback);
		return new DocumentTextExtractionResult(text, List.copyOf(pages), engine, version,
				properties.getOcr().getLanguage(), sourceType, confidence, duration, fallback, warnings);
	}

	private String normalize(String value) { return value == null ? "" : value.replace("\u0000", "").trim(); }
}
