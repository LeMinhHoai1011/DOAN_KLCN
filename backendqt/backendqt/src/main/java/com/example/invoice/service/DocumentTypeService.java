package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentTypeRequest;
import com.example.invoice.dto.document.DocumentTypeResponse;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.DocumentTypeRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentTypeService {
	private final DocumentTypeRepository repository;
	private final com.example.invoice.repository.DocumentRepository documentRepository;

	@Transactional(readOnly = true)
	public List<DocumentTypeResponse> findAll() {
		return repository.findAllWithDocumentCount().stream()
				.map(row -> toResponse((DocumentType) row[0], (Long) row[1])).toList();
	}

	@Transactional
	public DocumentTypeResponse create(DocumentTypeRequest request) {
		String code = normalizeCode(request.code());
		if (repository.findByCode(code).isPresent()) throw new IllegalArgumentException("Mã loại chứng từ đã tồn tại");
		DocumentType type = new DocumentType();
		apply(type, request, code);
		return toResponse(repository.save(type));
	}

	@Transactional
	public DocumentTypeResponse update(Long id, DocumentTypeRequest request) {
		DocumentType type = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại chứng từ"));
		String code = normalizeCode(request.code());
		if (!type.getCode().equals(code) && documentRepository.countByTypeId(id) > 0)
			throw new IllegalArgumentException("Không thể đổi mã loại chứng từ đã được sử dụng");
		repository.findByCode(code).filter(other -> !other.getId().equals(id)).ifPresent(other -> { throw new IllegalArgumentException("Mã loại chứng từ đã tồn tại"); });
		apply(type, request, code);
		return toResponse(type);
	}

	private void apply(DocumentType type, DocumentTypeRequest request, String code) {
		type.setCode(code); type.setName(request.name().trim()); type.setDescription(request.description() == null ? null : request.description().trim()); type.setActive(request.active());
	}
	private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
	private DocumentTypeResponse toResponse(DocumentType type) { return toResponse(type, 0); }
	private DocumentTypeResponse toResponse(DocumentType type, long count) { return new DocumentTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(), type.isActive(), count, type.getCreatedAt(), type.getUpdatedAt()); }
}
