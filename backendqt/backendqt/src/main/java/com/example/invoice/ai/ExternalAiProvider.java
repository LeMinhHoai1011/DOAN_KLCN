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

/**
 * Adapter for OpenAI-compatible Chat Completions Vision APIs.
 *
 * AI_CLOUD_BASE_URL should include the provider's API version prefix when
 * required (for example, https://provider.example/v1). No cloud request is
 * made unless base URL, API key, and model are all configured.
 */
@Component
@RequiredArgsConstructor
public class ExternalAiProvider implements AiProvider {
	private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
	private static final String DEFAULT_PROMPT =
			"Describe the invoice image briefly. Return the visible text and key invoice fields when available.";

	private final AiProperties properties;
	private final ObjectMapper objectMapper;

	@Override
	public String providerName() {
		return "cloud";
	}

	@Override
	public AiProviderResponse analyzeImage(AiImageRequest request) {
		if (request.imageBytes() == null || request.imageBytes().length == 0) {
			throw new BadRequestException("Dữ liệu ảnh gửi đến AI không được để trống");
		}
		if (request.imageBytes().length > properties.getMaxImageSizeBytes()) {
			throw new BadRequestException("Ảnh gửi đến AI vượt quá kích thước tối đa đã cấu hình");
		}

		AiProperties.Cloud cloud = properties.getCloud();
		String baseUrl = required(cloud.getBaseUrl(), "Đã chọn AI đám mây nhưng thiếu AI_CLOUD_BASE_URL.");
		String apiKey = required(cloud.getApiKey(), "Đã chọn AI đám mây nhưng thiếu AI_CLOUD_API_KEY.");
		String model = required(cloud.getModel(), "Đã chọn AI đám mây nhưng thiếu AI_CLOUD_MODEL.");
		String path = required(cloud.getChatCompletionsPath(), "Phải cấu hình đường dẫn chat-completions của AI đám mây");
		if (cloud.getTimeout().isZero() || cloud.getTimeout().isNegative()) {
			throw new BadRequestException("AI_CLOUD_TIMEOUT phải lớn hơn 0");
		}

		String mimeType = request.contentType() == null || request.contentType().isBlank()
				? "image/png" : request.contentType().trim();
		String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(request.imageBytes());
		String prompt = request.prompt() == null || request.prompt().isBlank() ? DEFAULT_PROMPT : request.prompt();
		String payload = serialize(new ChatCompletionsRequest(
				model,
				List.of(new ChatMessage("user", List.<Object>of(
						new TextContent("text", prompt),
						new ImageContent("image_url", new ImageUrl(dataUrl))))),
				false));

		String endpoint = joinUrl(baseUrl, path);
		OkHttpClient client = new OkHttpClient.Builder()
				.callTimeout(cloud.getTimeout())
				.build();
		Request httpRequest = new Request.Builder()
				.url(endpoint)
				.header("Authorization", "Bearer " + apiKey)
				.post(RequestBody.create(payload, JSON_MEDIA_TYPE))
				.build();

		long startedAt = System.nanoTime();
		try (Response response = client.newCall(httpRequest).execute()) {
			String rawResponse = response.body() == null ? "" : response.body().string();
			long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
			if (!response.isSuccessful()) {
				throw new AiProviderException("Dịch vụ AI bên ngoài trả về mã HTTP " + response.code());
			}

			JsonNode body = objectMapper.readTree(rawResponse);
			JsonNode content = body.path("choices").path(0).path("message").path("content");
			if (content.isMissingNode() || content.isNull()) {
				throw new AiProviderException("Phản hồi của dịch vụ AI bên ngoài không chứa nội dung đã sinh");
			}
			return new AiProviderResponse(providerName(), model, content.asText(), rawResponse, durationMs);
		} catch (IOException exception) {
			throw new AiProviderException("Không thể kết nối đến dịch vụ AI bên ngoài tại " + endpoint, exception);
		}
	}

	private String serialize(ChatCompletionsRequest request) {
		try {
			return objectMapper.writeValueAsString(request);
		} catch (JsonProcessingException exception) {
			throw new AiProviderException("Không thể tạo yêu cầu gửi đến dịch vụ AI bên ngoài", exception);
		}
	}

	private String joinUrl(String baseUrl, String path) {
		return baseUrl.replaceAll("/+$", "") + "/" + path.replaceFirst("^/+", "");
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new BadRequestException(message);
		}
		return value.trim();
	}

	private record ChatCompletionsRequest(String model, List<ChatMessage> messages, boolean stream) {
	}

	private record ChatMessage(String role, List<Object> content) {
	}

	private record TextContent(String type, String text) {
	}

	private record ImageContent(String type, ImageUrl image_url) {
	}

	private record ImageUrl(String url) {
	}
}
