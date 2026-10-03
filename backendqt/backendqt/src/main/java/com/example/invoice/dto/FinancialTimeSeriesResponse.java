package com.example.invoice.dto;
import java.math.BigDecimal;
public record FinancialTimeSeriesResponse(String period, BigDecimal income, BigDecimal expense, BigDecimal cashFlow) {}
