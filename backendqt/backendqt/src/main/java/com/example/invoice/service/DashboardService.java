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
	public FinancialDashboardResponse financial(LocalDate dateFrom, LocalDate dateTo) { return financialScoped(dateFrom,dateTo,null); }
 @Transactional(readOnly=true)
 public FinancialDashboardResponse financialScoped(LocalDate dateFrom,LocalDate dateTo,Long requestedCompany) {
        com.example.invoice.util.DateRanges.validate(dateFrom, dateTo);
		User user = userService.loadCurrent(SecurityContextHolder.getContext().getAuthentication());
		Long companyId = resolveFinancialCompany(user, requestedCompany);
		Object[] totals = financialTransactionRepository.summarize(companyId, dateFrom, dateTo).getFirst();
		BigDecimal revenue = (BigDecimal) totals[0]; BigDecimal expense = (BigDecimal) totals[1];
		return new FinancialDashboardResponse(revenue, expense, revenue.subtract(expense), financialTransactionRepository.expensesByCategory(companyId, dateFrom, dateTo).stream().map(row -> new FinancialDashboardResponse.CategoryExpense((String) row[0], (BigDecimal) row[1])).toList(), financialTransactionRepository.countUnverified(companyId));
	}

    @Transactional(readOnly = true)
    public List<FinancialTimeSeriesResponse> timeSeries(LocalDate from, LocalDate to, String interval) {
        return timeSeriesScoped(from, to, interval, null);
    }

    @Transactional(readOnly = true)
    public List<FinancialTimeSeriesResponse> timeSeriesScoped(LocalDate from, LocalDate to, String interval, Long requestedCompany) {
        com.example.invoice.util.DateRanges.validate(from, to);
        if (!"DAILY".equalsIgnoreCase(interval) && !"MONTHLY".equalsIgnoreCase(interval))
            throw new com.example.invoice.exception.BadRequestException("Kỳ tổng hợp phải là DAILY hoặc MONTHLY.");
        User user = userService.loadCurrent(SecurityContextHolder.getContext().getAuthentication());
        Long company = resolveFinancialCompany(user, requestedCompany);
        var rows = financialTransactionRepository.dailyTotals(company, from, to);
        return com.example.invoice.util.FinancialSeries.build(rows, from, to, "MONTHLY".equalsIgnoreCase(interval));
    }

    private Long resolveFinancialCompany(User user, Long requestedCompany) {
        if (requestedCompany != null && !hasRole(user,"ADMIN") && (user.getCompany()==null || !requestedCompany.equals(user.getCompany().getId())))
            throw new org.springframework.security.access.AccessDeniedException("Bạn không thể truy cập công ty khác");
        return requestedCompany != null ? requestedCompany : hasRole(user,"ADMIN") ? null : user.getCompany()==null ? -1L : user.getCompany().getId();
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
				classificationRepository.countCurrentDocumentsByStatusIn(classifiedStatuses()),
				documentRepository.countByStatus(com.example.invoice.entity.DocumentStatus.NEED_REVIEW),
				documentRepository.countByStatus(com.example.invoice.entity.DocumentStatus.FAILED),
				mapTypes(documentRepository.countAllGroupedByType()), mapStatuses(documentRepository.countAllGroupedByStatus()));
	}

	private DashboardStatisticsResponse statisticsForCompany(Long companyId) {
		return new DashboardStatisticsResponse(documentRepository.countByCompanyId(companyId),
				invoiceRepository.countByDocumentCompanyId(companyId), classifiedForCompany(companyId),
				documentRepository.countByCompanyIdAndStatus(companyId, com.example.invoice.entity.DocumentStatus.NEED_REVIEW),
				documentRepository.countByCompanyIdAndStatus(companyId, com.example.invoice.entity.DocumentStatus.FAILED),
				mapTypes(documentRepository.countByCompanyGroupedByType(companyId)), mapStatuses(documentRepository.countByCompanyGroupedByStatus(companyId)));
	}

	private DashboardStatisticsResponse statisticsForUser(Long userId) {
		return new DashboardStatisticsResponse(documentRepository.countByUploadedById(userId),
				invoiceRepository.countByDocumentUploadedById(userId), classifiedForUser(userId),
				documentRepository.countByUploadedByIdAndStatus(userId, com.example.invoice.entity.DocumentStatus.NEED_REVIEW),
				documentRepository.countByUploadedByIdAndStatus(userId, com.example.invoice.entity.DocumentStatus.FAILED),
				mapTypes(documentRepository.countByUploaderGroupedByType(userId)), mapStatuses(documentRepository.countByUploaderGroupedByStatus(userId)));
	}

	private List<DashboardStatisticsResponse.DocumentTypeCount> mapTypes(List<Object[]> rows) {
		return rows.stream().map(row -> new DashboardStatisticsResponse.DocumentTypeCount((String) row[0], (String) row[1], (Long) row[2])).toList();
	}

	private List<DashboardStatisticsResponse.DocumentStatusCount> mapStatuses(List<Object[]> rows) {
		return rows.stream().map(row -> new DashboardStatisticsResponse.DocumentStatusCount(row[0].toString(), (Long) row[1])).toList();
	}

	private long classifiedForCompany(Long companyId) {
		return classificationRepository.countCurrentDocumentsByCompanyAndStatusIn(companyId, classifiedStatuses());
	}

	private long classifiedForUser(Long userId) {
		return classificationRepository.countCurrentDocumentsByUploaderAndStatusIn(userId, classifiedStatuses());
	}

	private java.util.Set<ClassificationStatus> classifiedStatuses() {
		return java.util.EnumSet.of(ClassificationStatus.CLASSIFIED, ClassificationStatus.VERIFIED,
				ClassificationStatus.ACCEPTED, ClassificationStatus.CORRECTED);
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getUserRoles().stream()
				.anyMatch(assignment -> assignment.getRole().isActive() && roleCode.equals(assignment.getRole().getCode()));
	}

	private boolean isEmployee(User user) {
		return hasRole(user, "EMPLOYEE") || hasRole(user, "USER");
	}
}
