package com.example.invoice.dto;
import java.math.BigDecimal;
import java.util.List;
public record FinancialDashboardResponse(BigDecimal totalRevenue, BigDecimal totalExpense, BigDecimal cashFlow, List<CategoryExpense> expensesByCategory) { public record CategoryExpense(String category, BigDecimal amount) {} }
