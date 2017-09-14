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

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {
	private final InvoiceService invoiceService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
		return invoiceService.create(request);
	}

	@GetMapping
	public List<InvoiceResponse> findAll() {
		return invoiceService.findAll();
	}

	@GetMapping("/{id}")
	public InvoiceResponse findById(@PathVariable Long id) {
		return invoiceService.findById(id);
	}

	@PutMapping("/{id}")
	public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
		return invoiceService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		invoiceService.delete(id);
	}
}
