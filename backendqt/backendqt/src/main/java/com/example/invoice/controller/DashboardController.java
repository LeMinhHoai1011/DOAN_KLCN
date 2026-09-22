package com.example.invoice.controller;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

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
}
