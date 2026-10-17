package com.example.invoice.controller;
import com.example.invoice.dto.accounting.*;
import com.example.invoice.entity.AccountingCategoryType;
import com.example.invoice.service.AccountingCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/accounting-categories") @RequiredArgsConstructor
public class AccountingCategoryController {
 private final AccountingCategoryService service;
 @GetMapping @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
 public List<AccountingCategoryResponse> findAll(@RequestParam(required=false) Long companyId,
   @RequestParam(defaultValue="false") boolean includeInactive, @RequestParam(required=false) AccountingCategoryType type, Authentication auth) {
   return service.findAll(companyId,includeInactive,type,auth);
 }
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 public org.springframework.http.ResponseEntity<com.example.invoice.exception.ErrorResponse> invalidRequest(jakarta.servlet.http.HttpServletRequest request) {
   return org.springframework.http.ResponseEntity.badRequest().body(new com.example.invoice.exception.ErrorResponse(
     java.time.LocalDateTime.now(),400,"INVALID_ACCOUNTING_CATEGORY","Thu/Chi hoặc cấu trúc yêu cầu không hợp lệ",request.getRequestURI()));
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
 public AccountingCategoryResponse create(@Valid @RequestBody AccountingCategoryRequest request, Authentication auth) {return service.create(request,auth);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
 public AccountingCategoryResponse update(@PathVariable Long id,@Valid @RequestBody AccountingCategoryRequest request,Authentication auth) {return service.update(id,request,auth);}
}
