package com.example.invoice.controller;

import com.example.invoice.evaluation.LiveEvaluationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Company scope comes from DocumentService.load on every document-level call. */
@RestController
@RequestMapping("/api/v1/evaluation-labels")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
@RequiredArgsConstructor
public class LiveEvaluationController {
    private final LiveEvaluationService service;

    @GetMapping
    public List<LiveEvaluationService.Summary> list(@RequestParam(required = false) Long companyId) { return service.list(companyId); }

    @GetMapping("/report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LiveEvaluationService.Report> report(@RequestParam(required = false) Long companyId) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.report(companyId));
    }

    @PostMapping("/random")
    public LiveEvaluationService.RandomResult random(@RequestBody LiveEvaluationService.RandomRequest request, Authentication authentication) {
        return service.selectRandom(request, authentication);
    }

    @PostMapping("/{documentId}")
    public LiveEvaluationService.Summary select(@PathVariable Long documentId, Authentication authentication) {
        return service.select(documentId, authentication);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<LiveEvaluationService.Workspace> workspace(@PathVariable Long documentId) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.workspace(documentId));
    }

    @PutMapping("/{documentId}")
    public LiveEvaluationService.Workspace save(@PathVariable Long documentId, @RequestBody LiveEvaluationService.DraftRequest request) {
        return service.save(documentId, request);
    }

    @PostMapping("/{documentId}/ocr-draft")
    public Map<String, String> ocrDraft(@PathVariable Long documentId) { return Map.of("text", service.ocrDraft(documentId)); }

    @PostMapping("/{documentId}/confirm")
    public LiveEvaluationService.Workspace confirm(@PathVariable Long documentId, @RequestBody LiveEvaluationService.ConfirmRequest request,
            Authentication authentication) {
        return service.confirm(documentId, request, authentication);
    }

    @PostMapping("/{documentId}/reopen")
    public LiveEvaluationService.Workspace reopen(@PathVariable Long documentId, @RequestParam Integer revision) {
        return service.reopen(documentId, revision);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        String message = switch (exception.getStatusCode().value()) {
            case 404 -> "Chứng từ chưa nằm trong bộ đánh giá.";
            case 409 -> "Nhãn vừa được thay đổi ở nơi khác. Hãy tải lại trước khi lưu.";
            default -> "Không thể truy cập nơi lưu nhãn đánh giá. Vui lòng thử lại.";
        };
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", String.valueOf(exception.getReason()), "message", message));
    }
}
