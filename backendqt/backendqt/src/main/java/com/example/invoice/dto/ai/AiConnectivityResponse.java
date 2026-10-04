package com.example.invoice.dto.ai;

import java.util.List;

public record AiConnectivityResponse(String provider, String model, List<String> availableModels) {
}
