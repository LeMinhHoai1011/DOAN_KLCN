package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentCreateRequest;
import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.DocumentUpdateRequest;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.DocumentVersion;
import com.example.invoice.entity.User;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.DocumentVersionRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentService {
	private final DocumentRepository documentRepository;
	private final DocumentTypeRepository documentTypeRepository;
	private final DocumentVersionRepository documentVersionRepository;
	private final UserService userService;
	private final StorageService storageService;
	private final MinioClient minioClient;

	@Value("${app.minio.bucket:invoice-files}")
	private String bucket;

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
		
		document = documentRepository.save(document);
		
		// Create first version
		DocumentVersion version = new DocumentVersion();
		version.setDocument(document);
		version.setCreatedBy(user);
		version.setVersionNumber(1);
		version.setOriginalFileName(document.getOriginalFileName());
		version.setContentType(document.getFileType());
		version.setFileSize(document.getFileSize());
		version.setFileName(document.getFileName());
		version.setObjectKey(document.getFilePath());
		documentVersionRepository.save(version);
		
		return toResponse(document);
	}

	@Transactional
	public DocumentResponse createFromUpload(MultipartFile file, Long typeId, Authentication authentication) {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("Uploaded file must not be empty");
		}

		String originalFileName = storageService.sanitizeFileName(file.getOriginalFilename());
		
		// Add company scoping to object key for better isolation
		User user = userService.loadCurrent(authentication);
		String companyPrefix = user.getCompany() != null ? "company-" + user.getCompany().getId() + "/" : "system/";
		String objectKey = companyPrefix + storageService.generateObjectKey(originalFileName);
		
		uploadToMinio(file, objectKey);

		Document document = new Document();
		document.setOriginalFileName(originalFileName);
		document.setFileName(originalFileName);
		document.setFileType(file.getContentType());
		document.setFileSize(file.getSize());
		document.setFilePath(objectKey);
		document.setUploadedBy(user);
		document.setCompany(user.getCompany());
		
		if (typeId != null) {
			DocumentType type = documentTypeRepository.findById(typeId)
					.orElseThrow(() -> new ResourceNotFoundException("Document Type not found"));
			document.setType(type);
			document.setDocumentType(type.getCode()); // keep legacy string synced
		}
		
		document = documentRepository.save(document);
		
		DocumentVersion version = new DocumentVersion();
		version.setDocument(document);
		version.setCreatedBy(user);
		version.setVersionNumber(1);
		version.setOriginalFileName(originalFileName);
		version.setContentType(file.getContentType());
		version.setFileSize(file.getSize());
		version.setFileName(originalFileName);
		version.setObjectKey(objectKey);
		documentVersionRepository.save(version);
		
		return toResponse(document);
	}

	@Transactional
	public DocumentResponse uploadNewVersion(Long id, MultipartFile file, Authentication authentication) {
		if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file must not be empty");
		
		Document document = load(id);
		User user = userService.loadCurrent(authentication);
		
		String originalFileName = storageService.sanitizeFileName(file.getOriginalFilename());
		String companyPrefix = user.getCompany() != null ? "company-" + user.getCompany().getId() + "/" : "system/";
		String objectKey = companyPrefix + storageService.generateObjectKey(originalFileName);
		
		uploadToMinio(file, objectKey);
		
		// Determine next version number
		List<DocumentVersion> versions = documentVersionRepository.findByDocumentId(id);
		int nextVersion = versions.stream().mapToInt(DocumentVersion::getVersionNumber).max().orElse(0) + 1;
		
		// Create new version
		DocumentVersion version = new DocumentVersion();
		version.setDocument(document);
		version.setCreatedBy(user);
		version.setVersionNumber(nextVersion);
		version.setOriginalFileName(originalFileName);
		version.setContentType(file.getContentType());
		version.setFileSize(file.getSize());
		version.setFileName(originalFileName);
		version.setObjectKey(objectKey);
		documentVersionRepository.save(version);
		
		// Update document latest metadata
		document.setOriginalFileName(originalFileName);
		document.setFileName(originalFileName);
		document.setFileType(file.getContentType());
		document.setFileSize(file.getSize());
		document.setFilePath(objectKey);
		
		return toResponse(documentRepository.save(document));
	}

	@Transactional(readOnly = true)
	public InputStreamResource download(Long id) {
		Document document = load(id);
		try {
			InputStream stream = minioClient.getObject(GetObjectArgs.builder()
					.bucket(bucket)
					.object(document.getFilePath())
					.build());
			return new InputStreamResource(stream);
		} catch (Exception e) {
			throw new IllegalStateException("Could not download file from MinIO", e);
		}
	}

	@Transactional(readOnly = true)
	public List<DocumentResponse> findAll() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN") || hasRole(user, "ACCOUNTANT")) {
			return documentRepository.findAll().stream().map(this::toResponse).toList();
		}
		if (hasRole(user, "EMPLOYEE") || hasRole(user, "USER")) {
			return documentRepository.findAllByUploadedById(user.getId()).stream().map(this::toResponse).toList();
		}
		if (user.getCompany() == null) return java.util.List.of();
		return documentRepository.findAllByCompanyId(user.getCompany().getId()).stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
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
		Document document = load(id);
		// Optionally delete versions and from MinIO
		List<DocumentVersion> versions = documentVersionRepository.findByDocumentId(id);
		for (DocumentVersion version : versions) {
			try {
				minioClient.removeObject(RemoveObjectArgs.builder()
						.bucket(bucket)
						.object(version.getObjectKey())
						.build());
			} catch (Exception ignored) {
			}
		}
		documentVersionRepository.deleteAll(versions);
		documentRepository.delete(document);
	}

	@Transactional(readOnly = true)
	public Document load(Long id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN") || hasRole(user, "ACCOUNTANT")) {
			return documentRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
		}
		if (hasRole(user, "EMPLOYEE") || hasRole(user, "USER")) {
			return documentRepository.findByIdAndUploadedById(id, user.getId())
					.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
		}
		if (user.getCompany() == null) throw new ResourceNotFoundException("Document not found");
		return documentRepository.findByIdAndCompanyId(id, user.getCompany().getId())
				.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
	}

	public DocumentResponse toResponse(Document document) {
		Long uploadedById = document.getUploadedBy() == null ? null : document.getUploadedBy().getId();
		return new DocumentResponse(document.getId(), document.getOriginalFileName(), document.getFileType(),
				document.getFileSize(), document.getFilePath(), document.getStatus(), uploadedById,
				document.getCreatedAt(), document.getUpdatedAt());
	}
	
	private void uploadToMinio(MultipartFile file, String objectKey) {
		try (InputStream inputStream = file.getInputStream()) {
			if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			}
			minioClient.putObject(PutObjectArgs.builder()
					.bucket(bucket)
					.object(objectKey)
					.stream(inputStream, file.getSize(), -1)
					.contentType(file.getContentType())
					.build());
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to upload file to MinIO", exception);
		}
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getUserRoles().stream()
				.anyMatch(assignment -> roleCode.equals(assignment.getRole().getCode()));
	}
}
