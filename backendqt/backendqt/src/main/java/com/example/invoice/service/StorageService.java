package com.example.invoice.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StorageService {
	public String generateObjectKey(String originalFileName) {
		LocalDate now = LocalDate.now();
		String extension = StringUtils.getFilenameExtension(originalFileName);
		String suffix = extension == null || extension.isBlank() ? "" : "." + extension.toLowerCase(Locale.ROOT);
		return "documents/%d/%02d/%s%s".formatted(now.getYear(), now.getMonthValue(), UUID.randomUUID(), suffix);
	}

	public String sanitizeFileName(String originalFileName) {
		String fileName = StringUtils.getFilename(originalFileName);
		return fileName == null || fileName.isBlank() ? "uploaded-document" : fileName;
	}
}
