package com.example.invoice.dto;
import java.math.BigDecimal;
import java.util.List;
public record FinancialDashboardResponse(BigDecimal totalRevenue, BigDecimal totalExpense, BigDecimal cashFlow, List<CategoryExpense> expensesByCategory, long unverifiedTransactionCount) { public FinancialDashboardResponse(BigDecimal revenue,BigDecimal expense,BigDecimal cashFlow,List<CategoryExpense> categories){this(revenue,expense,cashFlow,categories,0);} public record CategoryExpense(String category, BigDecimal amount) {} }
