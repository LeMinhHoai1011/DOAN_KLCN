package com.example.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {
	private String provider = "ollama";
	private int requestTimeoutSeconds = 60;
	private long maxImageSizeBytes = 10 * 1024 * 1024;
	private final Ollama ollama = new Ollama();
	private final Cloud cloud = new Cloud();

	public String getProvider() { return provider; }
	public void setProvider(String provider) { this.provider = provider; }
	public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
	public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
	public long getMaxImageSizeBytes() { return maxImageSizeBytes; }
	public void setMaxImageSizeBytes(long maxImageSizeBytes) { this.maxImageSizeBytes = maxImageSizeBytes; }
	public Ollama getOllama() { return ollama; }
	public Cloud getCloud() { return cloud; }

	public static class Ollama {
		private String baseUrl = "http://127.0.0.1:11434";
		private String model = "qwen3-vl:8b";

		public String getBaseUrl() { return baseUrl; }
		public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
		public String getModel() { return model; }
		public void setModel(String model) { this.model = model; }
	}

	public static class Cloud {
		private String baseUrl = "";
		private String apiKey = "";
		private String model = "";
		private String chatCompletionsPath = "/chat/completions";

		public String getBaseUrl() { return baseUrl; }
		public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
		public String getApiKey() { return apiKey; }
		public void setApiKey(String apiKey) { this.apiKey = apiKey; }
		public String getModel() { return model; }
		public void setModel(String model) { this.model = model; }
		public String getChatCompletionsPath() { return chatCompletionsPath; }
		public void setChatCompletionsPath(String chatCompletionsPath) { this.chatCompletionsPath = chatCompletionsPath; }
	}
}
