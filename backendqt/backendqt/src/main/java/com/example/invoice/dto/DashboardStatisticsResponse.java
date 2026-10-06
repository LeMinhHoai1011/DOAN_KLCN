package com.example.invoice.dto;

import java.util.List;

public record DashboardStatisticsResponse(long totalDocuments, long totalInvoices, long totalClassified,
		long totalReviewRequired, long totalFailed, List<DocumentTypeCount> documentsByType,
		List<DocumentStatusCount> documentsByStatus) {
	public DashboardStatisticsResponse(long totalDocuments, long totalInvoices, long totalClassified, long totalReviewRequired) {
		this(totalDocuments, totalInvoices, totalClassified, totalReviewRequired, 0, List.of(), List.of());
	}
	public record DocumentTypeCount(String code, String name, long count) {}
	public record DocumentStatusCount(String status, long count) {}
}
