package com.example.invoice.service;

import com.example.invoice.dto.document.OCRResultRequest;
import com.example.invoice.dto.document.OCRResultResponse;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.OCRResult;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.OCRResultRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OCRResultService {
	private final OCRResultRepository ocrResultRepository;
	private final DocumentService documentService;

	public OCRResultResponse findByDocumentId(Long documentId) {
		return toResponse(ocrResultRepository.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)
				.orElseThrow(() -> new ResourceNotFoundException("OCR result not found")));
	}

	@Transactional
	public OCRResultResponse upsert(Long documentId, OCRResultRequest request) {
		Document document = documentService.load(documentId);
		OCRResult result = ocrResultRepository.findFirstByDocumentIdOrderByProcessedAtDesc(documentId).orElseGet(OCRResult::new);
		result.setDocument(document);
		result.setRawText(request.rawText());
		result.setConfidence(request.confidence());
		result.setProcessedAt(LocalDateTime.now());
		return toResponse(ocrResultRepository.save(result));
	}

	private OCRResultResponse toResponse(OCRResult result) {
		return new OCRResultResponse(result.getId(), result.getDocument().getId(), result.getRawText(), result.getConfidence(), result.getProcessedAt());
	}
}
