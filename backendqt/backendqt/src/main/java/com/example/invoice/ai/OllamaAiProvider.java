package com.example.invoice.ai;

import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiImageRequest;
import com.example.invoice.dto.ai.AiConnectivityResponse;
import com.example.invoice.dto.ai.AiProviderResponse;
import com.example.invoice.dto.ai.AiTextRequest;
import com.example.invoice.exception.AiProviderException;
import com.example.invoice.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger log = LoggerFactory.getLogger(OllamaAiProvider.class);
	private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
	private static final String DEFAULT_PROMPT = "Return a JSON object containing only visible document information.";

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final OkHttpClient aiHttpClient;

	@Override
	public String providerName() {
		return "ollama";
	}

	@Override
	public AiProviderResponse analyzeImage(AiImageRequest request) {
		if (request.imageBytes() == null || request.imageBytes().length == 0) {
			throw new BadRequestException("Dữ liệu ảnh gửi đến AI không được để trống");
		}
		if (request.imageBytes().length > properties.getMaxImageSizeBytes()) {
			throw new BadRequestException("Ảnh gửi đến AI vượt quá kích thước tối đa đã cấu hình");
		}

		String prompt = request.prompt() == null || request.prompt().isBlank() ? DEFAULT_PROMPT : request.prompt();
		String base64Image = Base64.getEncoder().encodeToString(request.imageBytes());
		return generate(prompt, List.of(base64Image), request.imageBytes().length);
	}

	@Override
	public AiProviderResponse analyzeText(AiTextRequest request) {
		if (request.text() == null || request.text().isBlank()) throw new BadRequestException("Nội dung văn bản gửi đến AI không được để trống");
		String prompt = (request.prompt() == null || request.prompt().isBlank() ? DEFAULT_PROMPT : request.prompt())
				+ "\n\nSOURCE TEXT (page markers are authoritative):\n" + request.text();
		return generate(prompt, List.of(), 0);
	}

	private AiProviderResponse generate(String prompt, List<String> images, int imageBytes) {
		String model = required(properties.getOllama().getModel(), "Phải cấu hình mô hình AI Ollama");
		String endpoint = required(properties.getOllama().getBaseUrl(), "Phải cấu hình URL cơ sở của AI Ollama")
				.replaceAll("/+$", "") + "/api/generate";
		if (properties.getRequestTimeout().isZero() || properties.getRequestTimeout().isNegative()) {
			throw new BadRequestException("Thời gian chờ yêu cầu AI phải lớn hơn 0");
		}
		int estimatedInputTokens = estimateInputTokens(prompt, images.size());
		int inputBudget = Math.max(512, properties.getOllama().getNumContext()
				- properties.getOcr().getReservedOutputTokens());
		if (estimatedInputTokens > inputBudget) {
			throw new AiProviderException("AI_CONTEXT_EXCEEDED: đầu vào AI ước tính " + estimatedInputTokens
					+ " token, vượt ngân sách " + inputBudget + " token");
		}

		String payload = serialize(new OllamaGenerateRequest(
				model,
				prompt,
				images,
				false,
				"json",
				properties.getOllama().isThink() ? Boolean.TRUE : null,
				new OllamaOptions(properties.getOllama().getNumPredict(), properties.getOllama().getNumContext())));
		OkHttpClient client = configuredClient();
		log.info("Ollama request provider={} endpoint={} model={} context={} inputBudget={} estimatedInputTokens={} imageBytes={} imageCount={} payloadLength={} promptLength={} timeout={}",
				providerName(), endpoint, model, properties.getOllama().getNumContext(), inputBudget, estimatedInputTokens,
				imageBytes, images.size(), payload.length(), prompt.length(),
				properties.getRequestTimeout());
		Request httpRequest = new Request.Builder()
				.url(endpoint)
				.post(RequestBody.create(payload, JSON_MEDIA_TYPE))
				.build();

		long startedAt = System.nanoTime();
		try (Response response = client.newCall(httpRequest).execute()) {
			String rawResponse = response.body() == null ? "" : response.body().string();
			long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
			JsonNode body = parseResponse(rawResponse);
			log.info("Ollama response status={} durationMs={} done={} doneReason={} responseLength={} thinkingLength={}",
					response.code(), durationMs, body.path("done").asBoolean(false), body.path("done_reason").asText(""),
					body.path("response").asText("").length(), body.path("thinking").asText("").length());
			if (!response.isSuccessful()) {
				String providerError = providerError(body);
				if (providerError.length() > 500) providerError = providerError.substring(0, 500);
				throw new AiProviderException(classifyHttpFailure(response.code(), providerError)
						+ ": Ollama returned HTTP " + response.code()
						+ (providerError.isBlank() ? "" : ": " + providerError));
			}

			if (body.hasNonNull("error")) {
				String providerError = providerError(body);
				throw new AiProviderException(classifyHttpFailure(response.code(), providerError) + ": " + providerError);
			}
			try {
				return new AiProviderResponse(providerName(), model, selectGeneratedContent(body), rawResponse, durationMs);
			} catch (AiProviderException exception) {
				if ("length".equalsIgnoreCase(body.path("done_reason").asText()))
					throw new AiProviderException("OLLAMA_TOKEN_LIMIT: quá trình sinh nội dung đã dừng trước khi tạo xong JSON có cấu trúc", exception);
				throw exception;
			}
		} catch (SocketTimeoutException exception) {
			throw failed(classifySocketTimeout(exception), exception, startedAt);
		} catch (IOException exception) {
			throw failed(classifyIoFailure(exception), exception, startedAt);
		}
	}

	public AiConnectivityResponse checkConnectivity() {
		String model = required(properties.getOllama().getModel(), "Phải cấu hình mô hình AI Ollama");
		String endpoint = required(properties.getOllama().getBaseUrl(), "Phải cấu hình URL cơ sở của AI Ollama")
				.replaceAll("/+$", "") + "/api/tags";
		Request request = new Request.Builder().url(endpoint).get().build();
		OkHttpClient client = configuredClient();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new AiProviderException("OLLAMA_INVALID_RESPONSE: /api/tags trả về mã HTTP " + response.code());
			JsonNode models = objectMapper.readTree(response.body() == null ? "" : response.body().string())
					.path("models");
			if (!models.isArray())
				throw new AiProviderException("OLLAMA_INVALID_RESPONSE: /api/tags không chứa danh sách mô hình");
			List<String> names = new java.util.ArrayList<>();
			models.forEach(node -> {
				if (node.hasNonNull("name"))
					names.add(node.get("name").asText());
			});
			if (!names.contains(model))
				throw new AiProviderException("OLLAMA_MODEL_NOT_FOUND: không tìm thấy mô hình " + model);
			return new AiConnectivityResponse(providerName(), model, List.copyOf(names));
		} catch (IOException exception) {
			throw failed(classifyIoFailure(exception), exception, System.nanoTime());
		}
	}

	String selectGeneratedContent(JsonNode body) {
		String response = extractJsonObject(body.path("response").asText(""));
		if (response != null) return response;
		String thinking = extractJsonObject(body.path("thinking").asText(""));
		if (thinking != null) return thinking;
		if (body.path("response").asText("").isBlank() && body.path("thinking").asText("").isBlank())
			throw new AiProviderException("OLLAMA_EMPTY_RESPONSE: Ollama không trả về nội dung đã sinh");
		throw new AiProviderException("OLLAMA_EMPTY_RESPONSE: phản hồi và nội dung suy luận không chứa đối tượng JSON hợp lệ");
	}

	private String extractJsonObject(String content) {
		if (content == null || content.isBlank()) return null;
		int start = content.indexOf('{'), depth = 0;
		if (start < 0) return null;
		for (int index = start; index < content.length(); index++) {
			char value = content.charAt(index);
			if (value == '{') depth++;
			else if (value == '}' && --depth == 0) {
				String candidate = content.substring(start, index + 1);
				try { return objectMapper.readTree(candidate).isObject() ? candidate : null; }
				catch (JsonProcessingException ignored) { return null; }
			}
		}
		return null;
	}

	private JsonNode parseResponse(String rawResponse) {
		try {
			return objectMapper.readTree(rawResponse);
		} catch (JsonProcessingException exception) {
			throw new AiProviderException("AI_INVALID_RESPONSE: Ollama trả về JSON phản hồi HTTP không hợp lệ", exception);
		}
	}

	private String providerError(JsonNode body) {
		JsonNode error = body.path("error");
		String message = error.isObject() ? error.path("message").asText("") : error.asText("");
		return message.replaceAll("[\\r\\n\\t]+", " ").trim();
	}

	String classifyHttpFailure(int status, String providerError) {
		String normalized = providerError == null ? "" : providerError.toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("context size") || normalized.contains("context length")
				|| normalized.contains("exceed_context") || normalized.contains("too many tokens"))
			return "AI_CONTEXT_EXCEEDED";
		return "AI_HTTP_ERROR";
	}

	private OkHttpClient configuredClient() {
		Duration timeout = properties.getRequestTimeout();
		return aiHttpClient.newBuilder()
				.connectTimeout(timeout)
				.readTimeout(timeout)
				.writeTimeout(timeout)
				.callTimeout(timeout)
				.build();
	}

	private String classifyIoFailure(IOException exception) {
		if (exception instanceof ConnectException) return "AI_CONNECTION_ERROR";
		if (exception instanceof InterruptedIOException) return "OLLAMA_TIMEOUT";
		return "AI_CONNECTION_ERROR";
	}

	private String classifySocketTimeout(SocketTimeoutException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
		return message.contains("connect") ? "AI_CONNECTION_ERROR" : "OLLAMA_TIMEOUT";
	}

	private int estimateInputTokens(String prompt, int imageCount) {
		int textTokens = (int) Math.ceil((prompt == null ? 0 : prompt.length()) / 3.0);
		return textTokens + Math.max(0, imageCount) * 1200;
	}

	private AiProviderException failed(String code, Exception exception, long startedAt) {
		long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
		log.warn("Ollama request failed code={} exceptionClass={} message={} durationMs={}", code,
				exception.getClass().getSimpleName(), exception.getMessage(), durationMs);
		return new AiProviderException(code + ": " + exception.getMessage(), exception);
	}

	private String serialize(OllamaGenerateRequest request) {
		try {
			return objectMapper.writeValueAsString(request);
		} catch (JsonProcessingException exception) {
			throw new AiProviderException("Không thể tạo yêu cầu gửi đến Ollama", exception);
		}
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new BadRequestException(message);
		}
		return value.trim();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record OllamaGenerateRequest(
			String model,
			String prompt,
			List<String> images,
			boolean stream,
			String format,
			Boolean think,
			OllamaOptions options) {
	}
	private record OllamaOptions(int num_predict, int num_ctx) {}
}
