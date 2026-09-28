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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PdfImageConversionService {
	private final AiProperties properties;

	public List<PdfPageImage> convert(byte[] pdfBytes) {
		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			int pageCount = document.getNumberOfPages();
			if (pageCount == 0) throw new BadRequestException("PDF does not contain pages");
			if (pageCount > properties.getPdf().getMaxPages()) throw new BadRequestException("PDF has " + pageCount + " pages; configured maximum is " + properties.getPdf().getMaxPages());
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
			throw new BadRequestException("PDF could not be converted to images");
		}
	}
}
