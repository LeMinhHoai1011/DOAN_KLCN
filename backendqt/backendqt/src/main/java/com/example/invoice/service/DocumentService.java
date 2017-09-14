package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentCreateRequest;
import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.DocumentUpdateRequest;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.User;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.DocumentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentService {
	private final DocumentRepository documentRepository;
	private final UserService userService;
	private final StorageService storageService;

	@Transactional
	public DocumentResponse create(DocumentCreateRequest request, Authentication authentication) {
		User user = userService.loadCurrent(authentication);
		Document document = new Document();
		document.setOriginalFileName(request.originalFileName());
		document.setFileName(storageService.sanitizeFileName(request.originalFileName()));
		document.setFileType(request.fileType());
		document.setFileSize(request.fileSize());
		document.setFilePath(request.filePath() == null ? storageService.generateObjectKey(request.originalFileName()) : request.filePath());
		document.setUploadedBy(user);
		document.setCompany(user.getCompany());
		return toResponse(documentRepository.save(document));
	}

	@Transactional
	public DocumentResponse createFromUpload(MultipartFile file, Authentication authentication) {
		return create(new DocumentCreateRequest(file.getOriginalFilename(), file.getContentType(), file.getSize(), null), authentication);
	}

	public List<DocumentResponse> findAll() {
		return documentRepository.findAll().stream().map(this::toResponse).toList();
	}

	public DocumentResponse findById(Long id) {
		return toResponse(load(id));
	}

	@Transactional
	public DocumentResponse update(Long id, DocumentUpdateRequest request) {
		Document document = load(id);
		if (request.originalFileName() != null) document.setOriginalFileName(request.originalFileName());
		if (request.fileType() != null) document.setFileType(request.fileType());
		if (request.fileSize() != null) document.setFileSize(request.fileSize());
		if (request.filePath() != null) document.setFilePath(request.filePath());
		if (request.status() != null) document.setStatus(request.status());
		return toResponse(document);
	}

	@Transactional
	public void delete(Long id) {
		documentRepository.delete(load(id));
	}

	Document load(Long id) {
		return documentRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
	}

	DocumentResponse toResponse(Document document) {
		Long uploadedById = document.getUploadedBy() == null ? null : document.getUploadedBy().getId();
		return new DocumentResponse(document.getId(), document.getOriginalFileName(), document.getFileType(),
				document.getFileSize(), document.getFilePath(), document.getStatus(), uploadedById,
				document.getCreatedAt(), document.getUpdatedAt());
	}
}
