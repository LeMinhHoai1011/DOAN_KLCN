package com.example.invoice.controller;
import com.example.invoice.dto.document.DocumentBusinessState;
import com.example.invoice.service.DocumentBusinessStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/documents/{documentId}/business-state")
public class DocumentBusinessStateController {
 private final DocumentBusinessStateService service;
 @GetMapping @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
 public DocumentBusinessState get(@PathVariable Long documentId) { return service.get(documentId); }
}
