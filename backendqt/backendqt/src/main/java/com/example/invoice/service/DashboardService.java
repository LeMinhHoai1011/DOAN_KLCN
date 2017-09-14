package com.example.invoice.service;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {
	private final DocumentRepository documentRepository;
	private final InvoiceRepository invoiceRepository;
	private final ClassificationRepository classificationRepository;

	public DashboardStatisticsResponse statistics() {
		return new DashboardStatisticsResponse(
				documentRepository.count(),
				invoiceRepository.count(),
				classificationRepository.countByStatus(ClassificationStatus.CLASSIFIED)
						+ classificationRepository.countByStatus(ClassificationStatus.VERIFIED),
				classificationRepository.countByStatus(ClassificationStatus.REVIEW_REQUIRED));
	}
}
