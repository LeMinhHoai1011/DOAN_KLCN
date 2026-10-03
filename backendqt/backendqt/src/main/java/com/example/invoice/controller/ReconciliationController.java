package com.example.invoice.controller;
import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.service.ReconciliationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/documents/{documentId}/reconciliations") @RequiredArgsConstructor
public class ReconciliationController { private final ReconciliationService service;
 @GetMapping @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public List<ReconciliationResponse> find(@PathVariable Long documentId){return service.find(documentId);}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public List<ReconciliationResponse> run(@PathVariable Long documentId,Authentication authentication){return service.run(documentId,authentication);}
 @PutMapping("/{id}/decision") @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public ReconciliationResponse decide(@PathVariable Long documentId,@PathVariable Long id,@RequestParam String status,@RequestParam(required=false) String comment,Authentication authentication){return service.decide(documentId,id,status,comment,authentication);}
}
