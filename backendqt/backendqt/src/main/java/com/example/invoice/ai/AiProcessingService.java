package com.example.invoice.ai;

import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiImageRequest;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiProviderResponse;
import com.example.invoice.dto.ai.AiTextRequest;
import com.example.invoice.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AiProcessingService {
	private final AiProperties properties;
	private final List<AiProvider> providers;

	public AiProcessingService(AiProperties properties, List<AiProvider> providers) {
		this.properties = properties;
		this.providers = providers;
	}

	public AiProviderResponse analyzeImage(AiImageRequest request) {
		return selectedProvider().analyzeImage(request);
	}

	public AiDocumentResult analyzeDocument(AiDocumentRequest request) {
		return selectedProvider().analyze(request);
	}

	public AiDocumentResult analyzeTextDocument(AiTextRequest request) {
		return selectedProvider().analyzeTextDocument(request);
	}

	public String selectedProviderName() {
		return normalizeProviderName(properties.getProvider());
	}

	private AiProvider selectedProvider() {
		String selected = selectedProviderName();
		return providers.stream()
				.filter(provider -> selected.equals(normalizeProviderName(provider.providerName())))
				.findFirst()
				.orElseThrow(() -> new BadRequestException(
						"AI provider '" + selected + "' is not available. Configure a supported provider."));
	}

	private String normalizeProviderName(String providerName) {
		if (providerName == null || providerName.isBlank()) {
			throw new BadRequestException("AI provider must be configured");
		}
		return providerName.trim().toLowerCase(Locale.ROOT);
	}
}
