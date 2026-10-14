package com.example.invoice.service;

import com.example.invoice.config.AiProperties;
import com.example.invoice.exception.BadRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PdfImageConversionService {
	private final AiProperties properties;

	public List<PdfPageImage> convert(byte[] pdfBytes) {
		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			int pageCount = document.getNumberOfPages();
			if (pageCount == 0) throw new BadRequestException("Tệp PDF không có trang nào");
			if (pageCount > properties.getPdf().getMaxPages()) throw new BadRequestException("Tệp PDF có " + pageCount + " trang; số trang tối đa đã cấu hình là " + properties.getPdf().getMaxPages());
			PDFRenderer renderer = new PDFRenderer(document);
			List<PdfPageImage> pages = new ArrayList<>();
			for (int index = 0; index < pageCount; index++) {
				BufferedImage image = renderer.renderImageWithDPI(index, properties.getPdf().getDpi());
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write(image, "png", output);
				pages.add(new PdfPageImage(index + 1, output.toByteArray(), "image/png"));
			}
			return List.copyOf(pages);
		} catch (BadRequestException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BadRequestException("Không thể chuyển tệp PDF thành ảnh");
		}
	}

	/** Reads the native text layer without rendering or OCR. One result is returned per page. */
	public List<PdfTextPage> extractTextPages(byte[] pdfBytes) {
		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			validatePageCount(document);
			PDFTextStripper stripper = new PDFTextStripper();
			List<PdfTextPage> pages = new ArrayList<>();
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				stripper.setStartPage(page); stripper.setEndPage(page);
				String text = stripper.getText(document).replace("\u0000", "").trim();
				pages.add(new PdfTextPage(page, text, isUsableTextLayer(text)));
			}
			return List.copyOf(pages);
		} catch (BadRequestException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BadRequestException("Không thể đọc text layer của tệp PDF");
		}
	}

	private void validatePageCount(PDDocument document) {
		int pageCount = document.getNumberOfPages();
		if (pageCount == 0) throw new BadRequestException("Tệp PDF không có trang nào");
		if (pageCount > properties.getPdf().getMaxPages())
			throw new BadRequestException("Tệp PDF có " + pageCount + " trang; số trang tối đa đã cấu hình là " + properties.getPdf().getMaxPages());
	}

	private boolean isUsableTextLayer(String text) {
		if (text == null) return false;
		String compact = text.replaceAll("\\s+", "");
		if (compact.length() < properties.getOcr().getMinimumTextLength()) return false;
		long readable = compact.codePoints().filter(cp -> Character.isLetterOrDigit(cp)
				|| Character.getType(cp) == Character.CURRENCY_SYMBOL || ",.-/:()%".indexOf(cp) >= 0).count();
		return compact.indexOf('\uFFFD') < 0 && readable >= Math.ceil(compact.codePointCount(0, compact.length()) * 0.75);
	}

	public record PdfTextPage(int pageNumber, String text, boolean usable) {}
}
