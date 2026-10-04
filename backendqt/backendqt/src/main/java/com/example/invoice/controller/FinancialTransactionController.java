package com.example.invoice.controller;

import com.example.invoice.dto.accounting.FinancialTransactionRequest;
import com.example.invoice.dto.accounting.FinancialTransactionResponse;
import com.example.invoice.service.FinancialTransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/financial-transactions")
@RequiredArgsConstructor
public class FinancialTransactionController {
	private final FinancialTransactionService financialTransactionService;

	@GetMapping
	@PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
	public Page<FinancialTransactionResponse> findAll(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) String transactionType, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long documentId, @RequestParam(required = false) Long invoiceId,
			@RequestParam(required = false) Long companyId, Pageable pageable, Authentication authentication) {
		return financialTransactionService.findAll(dateFrom, dateTo, transactionType, categoryId, documentId, invoiceId, companyId, pageable, authentication);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_CREATE') or hasRole('ADMIN')")
	public FinancialTransactionResponse create(@Valid @RequestBody FinancialTransactionRequest request, Authentication authentication) {
		return financialTransactionService.create(request, authentication);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_UPDATE') or hasRole('ADMIN')")
	public FinancialTransactionResponse update(@PathVariable Long id, @Valid @RequestBody FinancialTransactionRequest request, Authentication authentication) {
		return financialTransactionService.update(id, request, authentication);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_DELETE') or hasRole('ADMIN')")
	public void delete(@PathVariable Long id, Authentication authentication) {
		financialTransactionService.delete(id, authentication);
	}
}
