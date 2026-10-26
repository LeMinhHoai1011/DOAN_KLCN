package com.example.invoice.controller;

import com.example.invoice.dto.invoice.InvoiceRequest;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {
	private final InvoiceService invoiceService;

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	@ResponseStatus(HttpStatus.CREATED)
	public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
		return invoiceService.create(request);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER')")
	public List<InvoiceResponse> findAll() {
		return invoiceService.findAll();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER')")
	public InvoiceResponse findById(@PathVariable Long id) {
		return invoiceService.findById(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
	public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
		return invoiceService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		invoiceService.delete(id);
	}

    @PutMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceResponse updateItems(@PathVariable Long id,
            @Valid @RequestBody com.example.invoice.dto.invoice.InvoiceItemsUpdateRequest request) {
        return invoiceService.updateItems(id, request);
    }

    @GetMapping("/{id}/retail-details")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','EMPLOYEE','USER')")
    public com.example.invoice.service.RetailInvoiceDetailsService.View retailDetails(@PathVariable Long id) {
        return invoiceService.getRetailDetails(id);
    }
    @PutMapping("/{id}/retail-details")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public com.example.invoice.service.RetailInvoiceDetailsService.View updateRetailDetails(@PathVariable Long id,
            @RequestBody com.example.invoice.dto.invoice.RetailInvoiceDetails request) {
        return invoiceService.updateRetailDetails(id,request);
    }
}
