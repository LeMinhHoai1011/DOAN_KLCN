package com.example.invoice.controller;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import java.util.List;
import com.example.invoice.dto.FinancialDashboardResponse;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
	private final DashboardService dashboardService;

	@GetMapping("/statistics")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER')")
	public DashboardStatisticsResponse statistics() {
		return dashboardService.statistics();
	}

	@GetMapping("/financial")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public FinancialDashboardResponse financial(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
		return dashboardService.financial(dateFrom, dateTo);
	}

	@GetMapping("/financial/time-series")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public List<com.example.invoice.dto.FinancialTimeSeriesResponse> timeSeries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo, @RequestParam(defaultValue = "DAILY") String interval) {
		return dashboardService.timeSeries(dateFrom, dateTo, interval);
	}
}
