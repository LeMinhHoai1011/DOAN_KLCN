package com.example.invoice.controller;

import com.example.invoice.dto.accounting.AccountingCategoryResponse;
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting-categories")
@RequiredArgsConstructor
public class AccountingCategoryController {
	private final AccountingCategoryRepository categoryRepository;
	private final UserService userService;

	@GetMapping
	@PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
	public List<AccountingCategoryResponse> findAll(@RequestParam(required = false) Long companyId, Authentication authentication) {
		User user = userService.loadCurrent(authentication);
		Long targetCompany = companyId == null ? (user.getCompany() == null ? null : user.getCompany().getId()) : companyId;
		boolean admin = user.getUserRoles().stream().anyMatch(role -> "ADMIN".equals(role.getRole().getCode()));
		if (targetCompany == null) throw new BadRequestException("A company is required");
		if (!admin && (user.getCompany() == null || !targetCompany.equals(user.getCompany().getId()))) throw new BadRequestException("You cannot access another company");
		return categoryRepository.findByCompanyId(targetCompany).stream().filter(AccountingCategory::isActive)
				.map(category -> new AccountingCategoryResponse(category.getId(), category.getCategoryCode(), category.getCategoryName(), category.getDescription())).toList();
	}
}
