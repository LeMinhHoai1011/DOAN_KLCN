package com.example.invoice.controller;

import com.example.invoice.service.ExcelExportService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/exports") @RequiredArgsConstructor
public class ExportController {
	private static final MediaType XLSX=MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
	private final ExcelExportService exportService;
	@GetMapping("/invoices") @PreAuthorize("hasAuthority('PERMISSION_REPORT_EXPORT') or hasRole('ADMIN')")
	public ResponseEntity<byte[]> invoices(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,@RequestParam(required=false) String search){return file(exportService.invoices(fromDate,toDate,search),"invoices");}
	@GetMapping("/financial-report") @PreAuthorize("hasAuthority('PERMISSION_REPORT_EXPORT') or hasRole('ADMIN')")
	public ResponseEntity<byte[]> report(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,Authentication authentication){return file(exportService.financialReport(fromDate,toDate,authentication),"financial_report");}
	private ResponseEntity<byte[]> file(byte[] body,String prefix){String name=prefix+"_"+java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))+".xlsx";return ResponseEntity.ok().contentType(XLSX).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(body);}
}
