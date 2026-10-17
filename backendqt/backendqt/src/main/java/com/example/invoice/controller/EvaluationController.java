package com.example.invoice.controller;

import com.example.invoice.evaluation.EvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/evaluation")
@PreAuthorize("hasRole('ADMIN')")
public class EvaluationController {
    private final EvaluationService service;
    private final ObjectMapper json;
    public EvaluationController(EvaluationService service,ObjectMapper json){this.service=service;this.json=json;}

    @GetMapping("/statistics")
    public EvaluationService.Report statistics(@RequestParam(required=false) String model,
            @RequestParam(required=false) String strategy,@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return service.report(model,strategy,from,to);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required=false) String model,
            @RequestParam(required=false) String strategy,@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to)throws java.io.IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=ocr-ai-evaluation.json")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsBytes(service.report(model,strategy,from,to)));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> invalid(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(java.util.Map.of("error",exception.getReason(),
                "message","Không thể đọc dữ liệu đánh giá hợp lệ. Kiểm tra bộ kiểm thử và cấu hình được cấp quyền."));
    }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> invalidDate() {return ResponseEntity.badRequest().body(java.util.Map.of("error","INVALID_FILTER","message","Bộ lọc thời gian phải có định dạng ISO-8601 và múi giờ."));}
}
