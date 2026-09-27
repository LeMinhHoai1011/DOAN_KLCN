package com.example.invoice.controller;

import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.ai.OllamaAiProvider;
import com.example.invoice.dto.ai.AiConnectivityResponse;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiImageRequest;
import com.example.invoice.dto.ai.AiProviderResponse;
import com.example.invoice.exception.BadRequestException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiTestController {
	private final AiProcessingService aiProcessingService;
	private final AiProperties properties;
	private final OllamaAiProvider ollamaAiProvider;

	@GetMapping("/ollama/connectivity")
	@PreAuthorize("hasRole('ADMIN')")
	public AiConnectivityResponse checkOllamaConnectivity() {
		return ollamaAiProvider.checkConnectivity();
	}

	@PostMapping(value = "/test", consumes = "multipart/form-data")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER')")
	@ResponseStatus(HttpStatus.OK)
	public AiProviderResponse testImage(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "prompt", required = false) String prompt) {
		validate(file);
		try {
			return aiProcessingService.analyzeImage(new AiImageRequest(
					file.getOriginalFilename(), file.getContentType(), file.getBytes(), prompt));
		} catch (IOException exception) {
			throw new BadRequestException("Could not read the uploaded image");
		}
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("An image file is required");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
			throw new BadRequestException("Only image files are supported for AI testing");
		}
		if (file.getSize() > properties.getMaxImageSizeBytes()) {
			throw new BadRequestException("Image exceeds the configured maximum size");
		}
	}
}
