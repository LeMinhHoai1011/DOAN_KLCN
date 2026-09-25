package com.example.invoice.controller;

import com.example.invoice.dto.company.CompanyResponse;
import com.example.invoice.repository.CompanyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {
	private final CompanyRepository companyRepository;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<CompanyResponse> findAll() {
		return companyRepository.findAll().stream()
				.map(company -> new CompanyResponse(company.getId(), company.getCompanyName(), company.getTaxCode()))
				.toList();
	}
}
