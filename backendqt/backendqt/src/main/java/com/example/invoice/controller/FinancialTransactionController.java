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
 @org.springframework.beans.factory.annotation.Autowired private com.example.invoice.service.PaymentLinkService payments;
 @org.springframework.beans.factory.annotation.Autowired private com.example.invoice.service.ReconciliationService reconciliation;
 @org.springframework.beans.factory.annotation.Autowired private com.example.invoice.service.PaymentReconciliationService paymentReconciliation;
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public org.springframework.http.ResponseEntity<com.example.invoice.exception.ErrorResponse> conflict(org.springframework.web.server.ResponseStatusException e,jakarta.servlet.http.HttpServletRequest request){String message=e.getReason()==null?"Thao tác không hợp lệ":e.getReason();String code=message.split(":",2)[0];return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(new com.example.invoice.exception.ErrorResponse(java.time.LocalDateTime.now(),e.getStatusCode().value(),code,message,request.getRequestURI()));}
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 public org.springframework.http.ResponseEntity<com.example.invoice.exception.ErrorResponse> invalid(jakarta.servlet.http.HttpServletRequest request){return org.springframework.http.ResponseEntity.badRequest().body(new com.example.invoice.exception.ErrorResponse(java.time.LocalDateTime.now(),400,"INVALID_PAYMENT_REQUEST","Cấu trúc yêu cầu thanh toán không hợp lệ",request.getRequestURI()));}
 @GetMapping("/unverified") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public Page<FinancialTransactionResponse> unverified(@RequestParam(required=false) Long companyId,Pageable pageable,Authentication auth){return financialTransactionService.unverified(companyId,pageable,auth);}
 @GetMapping("/payment-methods") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public java.util.List<String> methods(){return FinancialTransactionService.PAYMENT_METHODS;}
 @GetMapping("/link-options/{kind}") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public Page<com.example.invoice.dto.accounting.PaymentLinkOption> options(@PathVariable String kind,@RequestParam(defaultValue="") String search,@RequestParam(required=false) Long companyId,@RequestParam(required=false) Long documentId,@RequestParam(defaultValue="0") int page,Authentication auth){if(!java.util.List.of("documents","invoices").contains(kind))throw new com.example.invoice.exception.BadRequestException("Loại tra cứu không hợp lệ");return payments.search(search,companyId,documentId,"invoices".equals(kind),page,auth);}
 @GetMapping("/payment-preview") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_CREATE') or hasAuthority('PERMISSION_ACCOUNTING_UPDATE') or hasRole('ADMIN')")
 public com.example.invoice.dto.accounting.PaymentPreview preview(@RequestParam(required=false) Long documentId,@RequestParam(required=false) Long invoiceId,@RequestParam(required=false) Long companyId,@RequestParam(required=false) Long editingId,Authentication auth){return payments.preview(documentId,invoiceId,companyId,editingId,auth);}
 @GetMapping("/payment-summary") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public com.example.invoice.dto.accounting.PaymentSummary summary(@RequestParam(required=false) Long documentId,@RequestParam(required=false) Long invoiceId,@RequestParam(required=false) Long companyId,Authentication auth){return payments.summary(documentId,invoiceId,companyId,auth);}
 @GetMapping("/{id}/history") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public java.util.List<com.example.invoice.dto.accounting.PaymentHistoryEvent> history(@PathVariable Long id,Authentication auth){return financialTransactionService.history(id,auth).stream().map(e->new com.example.invoice.dto.accounting.PaymentHistoryEvent(e.getId(),e.getAction(),e.getUser()==null?null:e.getUser().getId(),e.getCreatedAt(),e.getOldValue(),e.getNewValue())).toList();}
 public record CancelRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=1000) String reason,@jakarta.validation.constraints.NotNull java.time.LocalDateTime expectedUpdatedAt){}
 @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_DELETE') or hasRole('ADMIN')")
 public FinancialTransactionResponse cancel(@PathVariable Long id,@Valid @RequestBody CancelRequest request,Authentication auth){return financialTransactionService.cancel(id,request.reason(),request.expectedUpdatedAt(),auth);}
 @PostMapping("/payment-reconciliation") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_UPDATE') or hasRole('ADMIN')")
 public com.example.invoice.dto.accounting.PaymentSummary reconcile(@RequestParam(required=false) Long documentId,@RequestParam(required=false) Long invoiceId,@RequestParam(required=false) Long companyId,Authentication auth){return reconciliation.runPayments(documentId,invoiceId,companyId,auth);}
 @GetMapping("/payment-reconciliation/history") @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public java.util.List<com.example.invoice.dto.accounting.PaymentHistoryEvent> reconciliationHistory(@RequestParam(required=false) Long documentId,@RequestParam(required=false) Long invoiceId,@RequestParam(required=false) Long companyId,Authentication auth){return paymentReconciliation.history(documentId,invoiceId,companyId,auth);}


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
