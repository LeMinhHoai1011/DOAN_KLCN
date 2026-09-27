package com.example.invoice.ai;

import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiImageRequest;
import com.example.invoice.dto.ai.AiProviderResponse;
import com.example.invoice.exception.AiProviderException;
import com.example.invoice.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/** Adapter for Ollama's local /api/generate Vision endpoint. */
@Component
@RequiredArgsConstructor
public class OllamaAiProvider implements AiProvider {
	private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
	private static final String DEFAULT_PROMPT =
			"Describe the invoice image briefly. Return the visible text and key invoice fields when available.";

	private final AiProperties properties;
	private final ObjectMapper objectMapper;

	@Override
	public String providerName() {
		return "ollama";
	}

	@Override
	public AiProviderResponse analyzeImage(AiImageRequest request) {
		if (request.imageBytes() == null || request.imageBytes().length == 0) {
			throw new BadRequestException("AI image payload must not be empty");
		}
		if (request.imageBytes().length > properties.getMaxImageSizeBytes()) {
			throw new BadRequestException("AI image exceeds the configured maximum size");
		}

		String model = required(properties.getOllama().getModel(), "AI Ollama model must be configured");
		String endpoint = required(properties.getOllama().getBaseUrl(), "AI Ollama base URL must be configured")
				.replaceAll("/+$", "") + "/api/generate";
		int timeoutSeconds = properties.getRequestTimeoutSeconds();
		if (timeoutSeconds <= 0) {
			throw new BadRequestException("AI request timeout must be greater than zero");
		}

		String payload = serialize(new OllamaGenerateRequest(
				model,
				request.prompt() == null || request.prompt().isBlank() ? DEFAULT_PROMPT : request.prompt(),
				List.of(Base64.getEncoder().encodeToString(request.imageBytes())),
				false));
		OkHttpClient client = new OkHttpClient.Builder()
				.callTimeout(Duration.ofSeconds(timeoutSeconds))
				.build();
		Request httpRequest = new Request.Builder()
				.url(endpoint)
				.post(RequestBody.create(payload, JSON_MEDIA_TYPE))
				.build();

		long startedAt = System.nanoTime();
		try (Response response = client.newCall(httpRequest).execute()) {
			String rawResponse = response.body() == null ? "" : response.body().string();
			long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
			if (!response.isSuccessful()) {
				throw new AiProviderException("Ollama returned HTTP " + response.code());
			}

			JsonNode body = objectMapper.readTree(rawResponse);
			if (body.hasNonNull("error")) {
				throw new AiProviderException("Ollama returned an error: " + body.get("error").asText());
			}
			if (!body.hasNonNull("response")) {
				throw new AiProviderException("Ollama response did not include generated content");
			}
			return new AiProviderResponse(providerName(), model, body.get("response").asText(), rawResponse, durationMs);
		} catch (IOException exception) {
			throw new AiProviderException("Could not connect to Ollama at " + endpoint, exception);
		}
	}

	private String serialize(OllamaGenerateRequest request) {
		try {
			return objectMapper.writeValueAsString(request);
		} catch (JsonProcessingException exception) {
			throw new AiProviderException("Could not create Ollama request", exception);
		}
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new BadRequestException(message);
		}
		return value.trim();
	}

	private record OllamaGenerateRequest(String model, String prompt, List<String> images, boolean stream) {
	}
}
