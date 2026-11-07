package com.example.invoice.controller;

import com.example.invoice.service.AiAccuracyService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiAccuracyController {
    private final AiAccuracyService service;

    @GetMapping("/evaluation/ai-accuracy")
    @PreAuthorize("hasAuthority('PERMISSION_ACCOUNTING_VIEW') or hasRole('ADMIN')")
    public AiAccuracyService.Report report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try { return service.report(from, to); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE"); }
    }

    @GetMapping("/documents/{id}/ai-changes")
    @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public AiAccuracyService.DocumentChanges documentChanges(@PathVariable Long id) {
        return service.documentChanges(id);
    }
}
