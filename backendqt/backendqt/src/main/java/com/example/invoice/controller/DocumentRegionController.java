package com.example.invoice.controller;

import com.example.invoice.dto.document.RegionDtos.RegionRect;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionBatchRequest;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionResponse;
import com.example.invoice.dto.document.RegionDtos.RegionTextResponse;
import com.example.invoice.service.RegionSelectionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual region selection on the document preview (same roles as field correction). */
@RestController
@RequestMapping("/api/v1/documents/{id}")
@RequiredArgsConstructor
public class DocumentRegionController {
	private final RegionSelectionService regionSelectionService;

	/** Read-only: OCR words inside the rectangle, in reading order. Nothing is saved. */
	@PostMapping("/region-text")
	@PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNTANT')")
	public RegionTextResponse regionText(@PathVariable Long id, @Valid @RequestBody RegionRect request,
			Authentication authentication) {
		return regionSelectionService.extractText(id, request, authentication);
	}

	/** Provenance for invoice-form values taken from a region, recorded after the invoice is saved. */
	@PostMapping("/region-selections")
	@PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNTANT')")
	public List<RegionSelectionResponse> recordSelections(@PathVariable Long id,
			@Valid @RequestBody RegionSelectionBatchRequest request, Authentication authentication) {
		return regionSelectionService.recordInvoiceSelections(id, request, authentication);
	}
}
