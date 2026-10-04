package com.example.invoice.service;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.dto.FinancialDashboardResponse;
import com.example.invoice.dto.FinancialTimeSeriesResponse;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.FinancialTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
	private final FinancialTransactionRepository financialTransactionRepository;

	@Transactional(readOnly = true)
	public FinancialDashboardResponse financial(LocalDate dateFrom, LocalDate dateTo) {
		User user = userService.loadCurrent(SecurityContextHolder.getContext().getAuthentication());
		Long companyId = hasRole(user, "ADMIN") ? null : user.getCompany() == null ? -1L : user.getCompany().getId();
		Object[] totals = financialTransactionRepository.summarize(companyId, dateFrom, dateTo).getFirst();
		BigDecimal revenue = (BigDecimal) totals[0]; BigDecimal expense = (BigDecimal) totals[1];
		return new FinancialDashboardResponse(revenue, expense, revenue.subtract(expense), financialTransactionRepository.expensesByCategory(companyId, dateFrom, dateTo).stream().map(row -> new FinancialDashboardResponse.CategoryExpense((String) row[0], (BigDecimal) row[1])).toList());
	}

	@Transactional(readOnly = true)
	public List<FinancialTimeSeriesResponse> timeSeries(LocalDate dateFrom, LocalDate dateTo, String interval) {
		String period = "MONTHLY".equalsIgnoreCase(interval) ? "month" : "day";
		User user = userService.loadCurrent(SecurityContextHolder.getContext().getAuthentication());
		Long companyId = hasRole(user, "ADMIN") ? null : user.getCompany() == null ? -1L : user.getCompany().getId();
		return financialTransactionRepository.timeSeries(companyId, dateFrom, dateTo, period).stream().map(row -> {
			BigDecimal income = (BigDecimal) row[1]; BigDecimal expense = (BigDecimal) row[2];
			return new FinancialTimeSeriesResponse((String) row[0], income, expense, income.subtract(expense));
		}).toList();
	}

	@Transactional(readOnly = true)
	public DashboardStatisticsResponse statistics() {
		User user = userService.loadCurrent(SecurityContextHolder.getContext().getAuthentication());
		if (hasRole(user, "ADMIN")) return statisticsForAll();
		if (hasRole(user, "ACCOUNTANT")) return user.getCompany() == null ? new DashboardStatisticsResponse(0, 0, 0, 0) : statisticsForCompany(user.getCompany().getId());
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
