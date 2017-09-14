package com.example.invoice.dto;

public record DashboardStatisticsResponse(
		long totalDocuments,
		long totalInvoices,
		long totalClassified,
		long totalReviewRequired) {
}
