package com.example.invoice.dto.document;
import java.math.BigDecimal;
public record TransactionAssessmentResponse(String type, BigDecimal confidence, String reason) {}
