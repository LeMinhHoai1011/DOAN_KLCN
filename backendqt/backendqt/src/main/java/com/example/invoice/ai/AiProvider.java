package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiImageRequest;
import com.example.invoice.dto.ai.AiProviderResponse;

/** A provider adapter; invoice business logic must not call a vendor directly. */
public interface AiProvider {
	String providerName();

	AiProviderResponse analyzeImage(AiImageRequest request);
}
