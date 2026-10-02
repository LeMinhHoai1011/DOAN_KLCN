package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiImageRequest;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiProviderResponse;
import com.example.invoice.dto.ai.AiTextRequest;

/** A provider adapter; invoice business logic must not call a vendor directly. */
public interface AiProvider {
	String providerName();

	AiProviderResponse analyzeImage(AiImageRequest request);

	default AiProviderResponse analyzeText(AiTextRequest request) {
		throw new UnsupportedOperationException("Provider does not support text document analysis");
	}

	default AiDocumentResult analyze(AiDocumentRequest request) {
		AiProviderResponse response = analyzeImage(new AiImageRequest(
				request.fileName(), request.contentType(), request.imageBytes(), request.prompt()));
		return AiDocumentResultParser.parse(response.provider(), response.model(), response.content(),
				response.rawResponse(), response.durationMs());
	}

	default AiDocumentResult analyzeTextDocument(AiTextRequest request) {
		AiProviderResponse response = analyzeText(request);
		return AiDocumentResultParser.parse(response.provider(), response.model(), response.content(),
				response.rawResponse(), response.durationMs());
	}
}
