package com.example.invoice.service;

import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.invoice.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import com.example.invoice.config.AiProperties;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileValidationService {
    private final AiProperties aiProperties;

    public void validateUpload(MultipartFile file) {
        if (file.isEmpty()) throw new BadRequestException("Tệp tải lên không được để trống");
        
        long maxSize = 10 * 1024 * 1024; // 10MB default
        if (file.getSize() > maxSize) {
            throw new BadRequestException("Kích thước tệp vượt quá giới hạn 10MB");
        }

        byte[] header = new byte[8];
        try (InputStream is = file.getInputStream()) {
            is.read(header);
        } catch (Exception e) {
            throw new BadRequestException("Không thể đọc tệp");
        }

        String mimeType = file.getContentType();
        boolean isPdf = "application/pdf".equalsIgnoreCase(mimeType) && isPdfSignature(header);
        boolean isJpeg = ("image/jpeg".equalsIgnoreCase(mimeType) || "image/jpg".equalsIgnoreCase(mimeType))
                && isJpegSignature(header);
        boolean isPng = "image/png".equalsIgnoreCase(mimeType) && isPngSignature(header);

        if (!isPdf && !isJpeg && !isPng) {
            throw new BadRequestException("Chỉ hỗ trợ tệp PDF, JPEG và PNG");
        }

        if (isPdf) {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                int pageCount = document.getNumberOfPages();
                if (pageCount == 0) throw new BadRequestException("Tệp PDF không có trang nào");
                if (pageCount > aiProperties.getPdf().getMaxPages()) {
                    throw new BadRequestException("Tệp PDF có " + pageCount + " trang; số trang tối đa đã cấu hình là " + aiProperties.getPdf().getMaxPages());
                }
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                throw new BadRequestException("Tệp PDF hỏng hoặc không thể đọc");
            }
        } else {
            try (InputStream input = file.getInputStream()) {
                BufferedImage image = ImageIO.read(input);
                if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                    throw new BadRequestException("Tệp ảnh hỏng hoặc không thể giải mã");
                }
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                throw new BadRequestException("Tệp ảnh hỏng hoặc không thể giải mã");
            }
        }
    }

    private boolean isPdfSignature(byte[] header) {
        return header.length >= 5 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46 && header[4] == 0x2D; // %PDF-
    }

    private boolean isJpegSignature(byte[] header) {
        return header.length >= 3 && header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF;
    }

    private boolean isPngSignature(byte[] header) {
        return header.length >= 8 && header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
    }
}
