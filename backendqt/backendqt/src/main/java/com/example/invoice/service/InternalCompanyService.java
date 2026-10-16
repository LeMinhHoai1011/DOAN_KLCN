package com.example.invoice.service;

import com.example.invoice.config.InternalCompanyProperties;
import com.example.invoice.entity.Company;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalCompanyService {
	private final InternalCompanyProperties properties;
	private final CompanyRepository companyRepository;

	/** Checks syntax only; does not query the database or guarantee provisioning. */
	public boolean isConfigured() {
		try {
			requiredId();
			return true;
		} catch (BadRequestException exception) {
			return false;
		}
	}

	/** Resolves afresh on each call; never creates a company or falls back. */
	@Transactional(readOnly = true)
	public Company resolveRequired() {
		Long id = requiredId();
		return companyRepository.findById(id)
				.orElseThrow(() -> new BadRequestException(
						"INTERNAL_COMPANY_NOT_FOUND: Không tìm thấy công ty nội bộ đã cấu hình"));
	}

	private Long requiredId() {
		String raw = properties.getId();
		if (raw == null || raw.isBlank()) {
			throw new BadRequestException(
					"INTERNAL_COMPANY_NOT_CONFIGURED: Chưa cấu hình công ty nội bộ");
		}
		String value = raw.trim();
		try {
			if (!value.matches("[0-9]+")) throw new NumberFormatException();
			long id = Long.parseLong(value);
			if (id <= 0) throw new NumberFormatException();
			return id;
		} catch (NumberFormatException exception) {
			throw new BadRequestException(
					"INTERNAL_COMPANY_INVALID_CONFIG: ID công ty nội bộ phải là số nguyên dương trong phạm vi Long");
		}
	}
}
