package com.example.invoice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {
	private String provider = "ollama";
	private Duration requestTimeout = Duration.ofSeconds(60);
	private long maxImageSizeBytes = 10 * 1024 * 1024;
	private final Ollama ollama = new Ollama();
	private final Cloud cloud = new Cloud();
	private final DocumentProcessing document = new DocumentProcessing();
	private final Preprocessing preprocessing = new Preprocessing();
	private final Pdf pdf = new Pdf();

	public String getProvider() { return provider; }
	public void setProvider(String provider) { this.provider = provider; }
	public Duration getRequestTimeout() { return requestTimeout; }
	public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
	public long getMaxImageSizeBytes() { return maxImageSizeBytes; }
	public void setMaxImageSizeBytes(long maxImageSizeBytes) { this.maxImageSizeBytes = maxImageSizeBytes; }
	public Ollama getOllama() { return ollama; }
	public Cloud getCloud() { return cloud; }
	public DocumentProcessing getDocument() { return document; }
	public Preprocessing getPreprocessing() { return preprocessing; }
	public Pdf getPdf() { return pdf; }

	public static class Pdf {
		private int maxPages = 5;
		private int dpi = 180;
		public int getMaxPages() { return maxPages; }
		public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
		public int getDpi() { return dpi; }
		public void setDpi(int dpi) { this.dpi = dpi; }
	}

	public static class Preprocessing {
		private boolean enabled = true;
		private double minimumDeskewAngleDegrees = 0.5;
		private int maxPixelsForDenoise = 3_000_000;

		public boolean isEnabled() { return enabled; }
		public void setEnabled(boolean enabled) { this.enabled = enabled; }
		public double getMinimumDeskewAngleDegrees() { return minimumDeskewAngleDegrees; }
		public void setMinimumDeskewAngleDegrees(double value) { this.minimumDeskewAngleDegrees = value; }
		public int getMaxPixelsForDenoise() { return maxPixelsForDenoise; }
		public void setMaxPixelsForDenoise(int value) { this.maxPixelsForDenoise = value; }
	}

	public static class Ollama {
		private String baseUrl = "http://localhost:11434";
		private String model = "";
		private boolean think = false;
		private int numPredict = 4096;

		public String getBaseUrl() { return baseUrl; }
		public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
		public String getModel() { return model; }
		public void setModel(String model) { this.model = model; }
		public boolean isThink() { return think; }
		public void setThink(boolean think) { this.think = think; }
		public int getNumPredict() { return numPredict; }
		public void setNumPredict(int numPredict) { this.numPredict = numPredict; }
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

	public static class DocumentProcessing {
		private java.math.BigDecimal highConfidenceThreshold = new java.math.BigDecimal("0.90");
		private java.math.BigDecimal reviewThreshold = new java.math.BigDecimal("0.75");

		public java.math.BigDecimal getHighConfidenceThreshold() { return highConfidenceThreshold; }
		public void setHighConfidenceThreshold(java.math.BigDecimal highConfidenceThreshold) { this.highConfidenceThreshold = highConfidenceThreshold; }
		public java.math.BigDecimal getReviewThreshold() { return reviewThreshold; }
		public void setReviewThreshold(java.math.BigDecimal reviewThreshold) { this.reviewThreshold = reviewThreshold; }
	}
}
