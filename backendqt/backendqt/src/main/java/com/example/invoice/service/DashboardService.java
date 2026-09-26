package com.example.invoice.service;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
	private final DocumentRepository documentRepository;
	private final InvoiceRepository invoiceRepository;
	private final ClassificationRepository classificationRepository;
	private final UserService userService;

	@Transactional(readOnly = true)
	public DashboardStatisticsResponse statistics() {
		User user = userService.loadCurrent(SecurityContextHolder.getContext().getAuthentication());
		if (hasRole(user, "ADMIN") || hasRole(user, "ACCOUNTANT")) return statisticsForAll();
		if (isEmployee(user)) return statisticsForUser(user.getId());
		if (user.getCompany() == null) return new DashboardStatisticsResponse(0, 0, 0, 0);
		return statisticsForCompany(user.getCompany().getId());
	}

	private DashboardStatisticsResponse statisticsForAll() {
		return new DashboardStatisticsResponse(
				documentRepository.count(),
				invoiceRepository.count(),
				classificationRepository.countByStatus(ClassificationStatus.CLASSIFIED)
						+ classificationRepository.countByStatus(ClassificationStatus.VERIFIED),
				classificationRepository.countByStatus(ClassificationStatus.REVIEW_REQUIRED));
	}

	private DashboardStatisticsResponse statisticsForCompany(Long companyId) {
		return new DashboardStatisticsResponse(documentRepository.countByCompanyId(companyId),
				invoiceRepository.countByDocumentCompanyId(companyId), classifiedForCompany(companyId),
				classificationRepository.countByDocumentCompanyIdAndStatus(companyId, ClassificationStatus.REVIEW_REQUIRED));
	}

	private DashboardStatisticsResponse statisticsForUser(Long userId) {
		return new DashboardStatisticsResponse(documentRepository.countByUploadedById(userId),
				invoiceRepository.countByDocumentUploadedById(userId), classifiedForUser(userId),
				classificationRepository.countByDocumentUploadedByIdAndStatus(userId, ClassificationStatus.REVIEW_REQUIRED));
	}

	private long classifiedForCompany(Long companyId) {
		return classificationRepository.countByDocumentCompanyIdAndStatus(companyId, ClassificationStatus.CLASSIFIED)
				+ classificationRepository.countByDocumentCompanyIdAndStatus(companyId, ClassificationStatus.VERIFIED);
	}

	private long classifiedForUser(Long userId) {
		return classificationRepository.countByDocumentUploadedByIdAndStatus(userId, ClassificationStatus.CLASSIFIED)
				+ classificationRepository.countByDocumentUploadedByIdAndStatus(userId, ClassificationStatus.VERIFIED);
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getUserRoles().stream()
				.anyMatch(assignment -> roleCode.equals(assignment.getRole().getCode()));
	}

	private boolean isEmployee(User user) {
		return hasRole(user, "EMPLOYEE") || hasRole(user, "USER");
	}
}
