package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiTextRequest;
import com.example.invoice.exception.AiProviderException;
import com.example.invoice.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

class OllamaAiProviderTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void acceptsDirectJsonAndBracesInsideAString() throws Exception {
		OllamaAiProvider provider = provider(new AiProperties(), request -> response(request, 200,
				"{\"response\":\"{\\\"warnings\\\":[\\\"Nội dung {không rõ}\\\"],\\\"documentType\\\":\\\"INVOICE\\\"}\",\"done\":true}"));
		String content = provider.analyzeText(new AiTextRequest("invoice", "text", "prompt")).content();
		assertEquals("INVOICE", objectMapper.readTree(content).path("documentType").asText());
		assertEquals("Nội dung {không rõ}", objectMapper.readTree(content).path("warnings").path(0).asText());
	}

	@Test
	void extractsWrappedJsonForCompatibility() {
		OllamaAiProvider provider = provider(new AiProperties(), request -> response(request, 200,
				"{\"response\":\"Here is the result: \\n{\\\"documentType\\\":\\\"INVOICE\\\"}\",\"done\":true}"));
		assertEquals("{\"documentType\":\"INVOICE\"}",
				provider.analyzeText(new AiTextRequest("invoice", "text", "prompt")).content());
	}

	@Test
	void classifiesEmptyInvalidAndTruncatedOutputSeparately() {
		assertCode("OLLAMA_EMPTY_RESPONSE", "{\"response\":\"\",\"thinking\":\"\",\"done\":true}");
		assertCode("OLLAMA_INVALID_JSON", "{\"response\":\"I cannot determine the document.\",\"done\":true}");
		assertCode("OLLAMA_TOKEN_LIMIT", "{\"response\":\"{\\\"documentType\\\":\\\"INVOICE\\\",\\\"invoice\\\":\",\"done\":false,\"done_reason\":\"length\"}");
	}

	@Test
	void acceptsOnlyACompleteJsonObjectFromThinkingForKnownOllamaCompatibility() {
		OllamaAiProvider provider = provider(new AiProperties(), request -> response(request, 200,
				"{\"response\":\"\",\"thinking\":\"{\\\"documentType\\\":\\\"INVOICE\\\"}\",\"done\":true}"));
		assertEquals("{\"documentType\":\"INVOICE\"}",
				provider.analyzeText(new AiTextRequest("invoice", "text", "prompt")).content());
		assertCode("OLLAMA_INVALID_JSON", "{\"response\":\"\",\"thinking\":\"reasoning then {\\\"documentType\\\":\\\"INVOICE\\\"}\",\"done\":true}");
	}

	@Test
	void classifiesContextConnectionAndTimeoutFailures() {
		assertEquals("AI_CONTEXT_EXCEEDED", provider(new AiProperties(), request -> response(request, 400,
				"{\"error\":\"request exceeds the available context size\"}"))
				.classifyHttpFailure(400, "request exceeds the available context size"));
		assertEquals("OLLAMA_MODEL_NOT_FOUND", provider(new AiProperties(), request -> response(request, 404, "{}"))
				.classifyHttpFailure(404, "model qwen3-vl:8b not found"));
		assertFailure("AI_CONTEXT_EXCEEDED", request -> response(request, 400,
				"{\"error\":\"request exceeds the available context size\"}"));
		assertFailure("AI_CONNECTION_ERROR", request -> { throw new ConnectException("refused"); });
		assertFailure("OLLAMA_TIMEOUT", request -> { throw new SocketTimeoutException("read timed out"); });
	}

	@Test
	void requestDisablesThinkingAndUsesConfiguredTokenBudget() throws Exception {
		AiProperties properties = new AiProperties();
		AtomicReference<JsonNode> payload = new AtomicReference<>();
		OllamaAiProvider provider = provider(properties, chain -> {
			Buffer buffer = new Buffer();
			chain.request().body().writeTo(buffer);
			payload.set(objectMapper.readTree(buffer.readUtf8()));
			return response(chain, 200, "{\"response\":\"{\\\"documentType\\\":\\\"INVOICE\\\"}\",\"done\":true}");
		});
		provider.analyzeText(new AiTextRequest("invoice", "text", "prompt"));
		assertTrue(payload.get().has("think"));
		assertFalse(payload.get().path("think").asBoolean());
		assertEquals(8192, payload.get().path("options").path("num_ctx").asInt());
		assertEquals(3072, payload.get().path("options").path("num_predict").asInt());

		properties.getOcr().setReservedOutputTokens(3073);
		assertThrows(BadRequestException.class,
				() -> provider.analyzeText(new AiTextRequest("invoice", "text", "prompt")));
	}

	@Test
	void rejectsInputThatExceedsContextBudget() {
		AiProperties properties = new AiProperties();
		String oversized = "x".repeat(16_000);
		AiProviderException error = assertThrows(AiProviderException.class,
				() -> provider(properties, request -> response(request, 200, "{}"))
						.analyzeText(new AiTextRequest("invoice", oversized, "prompt")));
		assertTrue(error.getMessage().contains("AI_CONTEXT_EXCEEDED"));
	}

	private void assertCode(String code, String body) {
		AiProviderException error = assertThrows(AiProviderException.class,
				() -> provider(new AiProperties(), request -> response(request, 200, body))
						.analyzeText(new AiTextRequest("invoice", "text", "prompt")));
		assertTrue(error.getMessage().contains(code), error.getMessage());
	}

	private void assertFailure(String code, Interceptor interceptor) {
		AiProviderException error = assertThrows(AiProviderException.class,
				() -> provider(new AiProperties(), interceptor).analyzeText(new AiTextRequest("invoice", "text", "prompt")));
		assertTrue(error.getMessage().contains(code), error.getMessage());
	}

	private OllamaAiProvider provider(AiProperties properties, Interceptor interceptor) {
		properties.getOllama().setModel("qwen3-vl:8b");
		return new OllamaAiProvider(properties, objectMapper, new OkHttpClient.Builder().addInterceptor(interceptor).build());
	}

	private Response response(Interceptor.Chain chain, int status, String body) {
		return response(chain.request(), status, body);
	}

	private Response response(Request request, int status, String body) {
		return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
				.message("test").body(ResponseBody.create(body, MediaType.get("application/json"))).build();
	}
}
