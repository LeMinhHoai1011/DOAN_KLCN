package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentCreateRequest;
import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.DocumentUpdateRequest;
import com.example.invoice.dto.document.CompanyRoleResponse;
import com.example.invoice.dto.document.TransactionAssessmentResponse;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentVersion;
import com.example.invoice.entity.User;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.DocumentVersionRepository;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.ProcessingLogRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DocumentService {
	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
	private final DocumentRepository documentRepository;
	private final DocumentTypeRepository documentTypeRepository;
	private final DocumentVersionRepository documentVersionRepository;
	private final ClassificationRepository classificationRepository;
	private final ProcessingLogRepository processingLogRepository;
	private final UserService userService;
	private final StorageService storageService;
	private final MinioClient minioClient;
	private final FileValidationService fileValidationService;
	@jakarta.persistence.PersistenceContext
	private jakarta.persistence.EntityManager mutationEntityManager;
	@Autowired
	private com.example.invoice.repository.ProcessingJobRepository activeProcessingJobs;
    @Autowired private DocumentAuditService documentAudit;
    private String before(Document d){return documentAudit==null?null:documentAudit.capture(d);}
    private void remember(Document d,String action,String previous){if(documentAudit!=null)documentAudit.append(d,action,previous);}

	@Transactional
	public Document lockForWorkflow(Long id) {
		load(id); // Preserve existing scope before acquiring a row lock.
		Document document = documentRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
		if (mutationEntityManager != null) mutationEntityManager.refresh(document, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
		load(id); // Recheck the refreshed scope after waiting for the lock.
		if (document.getStatus() == DocumentStatus.PROCESSING || (activeProcessingJobs != null
				&& activeProcessingJobs.existsByDocumentIdAndJobTypeAndStatus(id, "OCR_AI_PIPELINE", "RUNNING")))
			throw new com.example.invoice.exception.PipelineException(new com.example.invoice.dto.document.PipelineFailure(
					"PROCESSING_IN_PROGRESS", "CLAIM", false, 0, java.time.LocalDateTime.now(),
					"Chứng từ đang được xử lý. Chưa thể đổi thông tin hoặc tải phiên bản mới."), 409);
		return document;
	}

	@Transactional
	public Document lockForEdit(Long id) {
		Document document = lockForWorkflow(id);
		if (document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.APPROVED || document.getStatus() == DocumentStatus.COMPLETED)
			throw new com.example.invoice.exception.BadRequestException("Chứng từ đã duyệt hoặc đã khóa, không thể chỉnh sửa");
		return document;
	}

	/** Compatibility constructor for isolated scope tests that do not update document types. */
	DocumentService(DocumentRepository documentRepository, DocumentTypeRepository documentTypeRepository,
			DocumentVersionRepository documentVersionRepository, ClassificationRepository classificationRepository,
			UserService userService, StorageService storageService, MinioClient minioClient) {
		this(documentRepository, documentTypeRepository, documentVersionRepository, classificationRepository,
				userService, storageService, minioClient, null);
	}

	/** Compatibility constructor for isolated scope tests that do not update document types. */
	DocumentService(DocumentRepository documentRepository, DocumentTypeRepository documentTypeRepository,
			DocumentVersionRepository documentVersionRepository, ClassificationRepository classificationRepository,
			UserService userService, StorageService storageService, MinioClient minioClient, FileValidationService fileValidationService) {
		this(documentRepository, documentTypeRepository, documentVersionRepository, classificationRepository, null,
				userService, storageService, minioClient, fileValidationService);
	}

	@Value("${app.minio.bucket:invoice-files}")
	private String bucket;

	@Transactional
	public DocumentResponse create(DocumentCreateRequest request, Authentication authentication) {
        userService.loadCurrent(authentication);
        throw new com.example.invoice.exception.BadRequestException("FILE_UPLOAD_REQUIRED: Vui lòng tải file qua API upload; không nhận tham chiếu lưu trữ từ client");
	}

	@Transactional
	public DocumentResponse createFromUpload(MultipartFile file, Authentication authentication) {
		fileValidationService.validateUpload(file);

		String originalFileName = storageService.sanitizeFileName(file.getOriginalFilename());
		
		// Add company scoping to object key for better isolation
		User user = userService.loadCurrent(authentication);
		String companyPrefix = user.getCompany() != null ? "company-" + user.getCompany().getId() + "/" : "system/";
		String objectKey = companyPrefix + storageService.generateObjectKey(originalFileName);

		uploadToMinio(file, objectKey);
		registerRollbackCleanup(objectKey);

		Document document = new Document();
		document.setOriginalFileName(originalFileName);
		document.setFileName(originalFileName);
		document.setFileType(file.getContentType());
		document.setFileSize(file.getSize());
		document.setFilePath(objectKey);
		document.setUploadedBy(user);
		document.setCompany(user.getCompany());
		
		document = documentRepository.saveAndFlush(document);
		
		DocumentVersion version = new DocumentVersion();
		version.setDocument(document);
		version.setCreatedBy(user);
		version.setVersionNumber(1);
		version.setOriginalFileName(originalFileName);
		version.setContentType(file.getContentType());
		version.setFileSize(file.getSize());
		version.setFileName(originalFileName);
		version.setObjectKey(objectKey);
		documentVersionRepository.saveAndFlush(version);
        remember(document,"UPLOAD",null);
		
		return toResponse(document);
	}

	@Transactional
	public DocumentResponse uploadNewVersion(Long id, MultipartFile file, Authentication authentication) {
		fileValidationService.validateUpload(file);
		
		Document document = lockForEdit(id);
        String previous=before(document);
		User user = userService.loadCurrent(authentication);
		
		String originalFileName = storageService.sanitizeFileName(file.getOriginalFilename());
		String companyPrefix = document.getCompany() != null ? "company-" + document.getCompany().getId() + "/" : "system/";
		String objectKey = companyPrefix + storageService.generateObjectKey(originalFileName);

		// Determine next version number
		List<DocumentVersion> versions = documentVersionRepository.findByDocumentId(id);
        if(versions.isEmpty()&&document.getFilePath()!=null&&!document.getFilePath().isBlank())
            throw new com.example.invoice.exception.BadRequestException("LEGACY_FILE_VERSION_REVIEW_REQUIRED: File cũ chưa có lịch sử phiên bản; cần xác minh baseline trước khi thay file");
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
        if(document.getReviewStatus()==com.example.invoice.entity.ReviewStatus.PENDING)document.setStatus(DocumentStatus.UPLOADED);
		uploadToMinio(file, objectKey);
		registerRollbackCleanup(objectKey);
		documentVersionRepository.flush();
		documentRepository.saveAndFlush(document);
        remember(document,"FILE_VERSION_UPLOAD",previous);
        return toResponse(document);
	}

	@Transactional
	public Document claimForAiProcessing(Long id, boolean reprocess) {
		// load() enforces tenant/user scope before the pessimistic write lock is acquired.
		load(id);
		Document document = documentRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
		if (mutationEntityManager != null) mutationEntityManager.refresh(document, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
		load(id);
		if (document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.REJECTED || document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.CORRECTED)
			throw new com.example.invoice.exception.BadRequestException("Chứng từ đã trả lại cần được gửi lại qua workflow");
		if (document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.APPROVED || document.getStatus() == DocumentStatus.COMPLETED)
			throw new com.example.invoice.exception.BadRequestException("Không thể chạy lại AI cho chứng từ đã được kế toán duyệt");
		if (document.getStatus() == DocumentStatus.PROCESSING)
			throw new com.example.invoice.exception.BadRequestException("Chứng từ đang được xử lý");
		if (!reprocess && document.getStatus() != DocumentStatus.UPLOADED)
			throw new com.example.invoice.exception.BadRequestException("Chứng từ đã được xử lý; hãy dùng chức năng xử lý lại để chạy AI lần nữa");
		document.setStatus(DocumentStatus.PROCESSING);
		return document;
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
			throw new IllegalStateException("Không thể tải tệp xuống từ MinIO", e);
		}
	}

	@Transactional(readOnly = true)
	public byte[] loadFileBytes(Long id) {
		Document document = load(id);
		try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
				.bucket(bucket)
				.object(document.getFilePath())
				.build())) {
			return stream.readAllBytes();
		} catch (Exception exception) {
			throw new com.example.invoice.exception.PipelineException(PipelineFailures.classify(exception, "STORAGE", 0), 503, exception);
		}
	}

	@Transactional
	public void updateProcessingStatus(Long id, DocumentStatus status) {
		load(id).setStatus(status);
	}

	@Transactional(readOnly = true)
	public List<DocumentResponse> findAll() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN")) {
			return toResponses(documentRepository.findAll());
		}
		if (hasRole(user, "ACCOUNTANT")) {
			if (user.getCompany() == null) return java.util.List.of();
			return toResponses(documentRepository.findAllByCompanyId(user.getCompany().getId()));
		}
		if (hasRole(user, "EMPLOYEE") || hasRole(user, "USER")) {
			return toResponses(documentRepository.findAllByUploadedById(user.getId()));
		}
		if (user.getCompany() == null) return java.util.List.of();
		return toResponses(documentRepository.findAllByCompanyId(user.getCompany().getId()));
	}

	@Transactional(readOnly = true)
	public String storeProcessedImage(Long id, byte[] bytes, String contentType) {
		Document document = load(id);
		String companyPrefix = document.getCompany() == null ? "system" : "company-" + document.getCompany().getId();
		String objectKey = companyPrefix + "/processed/" + id + "/" + java.util.UUID.randomUUID() + ".png";
		try (InputStream input = new ByteArrayInputStream(bytes)) {
			if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(input, bytes.length, -1).contentType(contentType).build());
			return objectKey;
		} catch (Exception exception) {
			throw new IllegalStateException("Không thể lưu ảnh đã tiền xử lý", exception);
		}
	}

	@Transactional(readOnly = true)
	public Page<DocumentResponse> findPage(LocalDate dateFrom, LocalDate dateTo, Long companyId,
			DocumentStatus processingStatus, com.example.invoice.entity.ReviewStatus reviewStatus, Long typeId,
			Long uploaderId, String search, Pageable pageable, Authentication authentication) {
        com.example.invoice.util.DateRanges.validate(dateFrom, dateTo);
		User user = userService.loadCurrent(authentication);
		Specification<Document> specification = scopeFor(user, companyId);
		if (dateFrom != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
		if (dateTo != null) specification = specification.and((root, query, builder) -> builder.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
		if (processingStatus != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), processingStatus));
		if (reviewStatus != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("reviewStatus"), reviewStatus));
		if (typeId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("type").get("id"), typeId));
		if (uploaderId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("uploadedBy").get("id"), uploaderId));
		if (search != null && !search.isBlank()) {
			String value = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
			specification = specification.and((root, query, builder) -> builder.like(builder.lower(root.get("originalFileName")), value));
		}
		Page<Document> documents = documentRepository.findAll(specification, pageable);
		Map<Long, java.math.BigDecimal> confidenceByDocument = currentConfidenceByDocument(documents.getContent());
		return documents.map(document -> toResponse(document, confidenceByDocument.get(document.getId())));
	}

	@Transactional(readOnly = true)
	public DocumentResponse findById(Long id) {
		return toResponse(load(id));
	}

	@Transactional
	public DocumentResponse update(Long id, DocumentUpdateRequest request, Authentication authentication) {
		Document document = lockForEdit(id);
        if(request.filePath()!=null || request.fileType()!=null&&!request.fileType().equalsIgnoreCase(document.getFileType()) || request.fileSize()!=null&&!request.fileSize().equals(document.getFileSize()))
            throw new com.example.invoice.exception.BadRequestException("FILE_VERSION_REQUIRED: Nội dung và metadata lưu trữ chỉ thay đổi qua upload phiên bản");
        String previous=before(document);
		if (request.status() != null && request.status() != document.getStatus())
			throw new com.example.invoice.exception.BadRequestException("Trạng thái chỉ được thay đổi qua workflow hoặc pipeline xử lý");
		if (request.originalFileName() != null) document.setOriginalFileName(request.originalFileName());

		if (request.typeId() != null && (document.getType() == null || !request.typeId().equals(document.getType().getId()))) {
			if (document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.APPROVED)
				throw new IllegalArgumentException("Không thể đổi loại chứng từ đã được duyệt");
			DocumentType newType = documentTypeRepository.findById(request.typeId()).filter(DocumentType::isActive)
					.orElseThrow(() -> new IllegalArgumentException("Loại chứng từ không tồn tại hoặc đã ngừng hoạt động"));
			String oldCode = document.getType() == null ? document.getDocumentType() : document.getType().getCode();
			document.setType(newType);
			document.setDocumentType(newType.getCode());
			classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(id).ifPresent(classification -> {
				classification.setPredictedLabel(newType.getCode());
				classification.setAiGenerated(false);
				classification.setStatus(com.example.invoice.entity.ClassificationStatus.CORRECTED);
			});
			com.example.invoice.entity.ProcessingLog history = new com.example.invoice.entity.ProcessingLog();
			history.setDocument(document); history.setProcessType("DOCUMENT_TYPE");
			history.setAgentStep("MANUAL_CORRECTION"); history.setStatus("SUCCESS");
			history.setMessage("oldType=" + (oldCode == null ? "UNDETERMINED" : oldCode) + "; newType=" + newType.getCode()
					+ "; correctedBy=" + userService.loadCurrent(authentication).getId());
			processingLogRepository.save(history);
		}
		remember(document,"DOCUMENT_UPDATE",previous);
        return toResponse(document);
	}

	@Transactional
	public void delete(Long id) {
        load(id);
        throw new com.example.invoice.exception.BadRequestException("DOCUMENT_RETENTION_REQUIRED: Chưa cho phép xóa vật lý chứng từ, file hoặc lịch sử");
	}

	@Transactional(readOnly = true)
	public Document load(Long id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN")) {
			return documentRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
		}
		if (hasRole(user, "ACCOUNTANT")) {
			if (user.getCompany() == null) throw new ResourceNotFoundException("Không tìm thấy chứng từ");
			return documentRepository.findByIdAndCompanyId(id, user.getCompany().getId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
		}
		if (hasRole(user, "EMPLOYEE") || hasRole(user, "USER")) {
			return documentRepository.findByIdAndUploadedById(id, user.getId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
		}
		if (user.getCompany() == null) throw new ResourceNotFoundException("Không tìm thấy chứng từ");
		return documentRepository.findByIdAndCompanyId(id, user.getCompany().getId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
	}

	public DocumentResponse toResponse(Document document) {
		java.math.BigDecimal confidence = classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
				.map(com.example.invoice.entity.Classification::getConfidence).orElse(null);
		return toResponse(document, confidence);
	}

	private DocumentResponse toResponse(Document document, java.math.BigDecimal confidence) {
        if (document.getStatus() == DocumentStatus.PROCESSING || document.getStatus() == DocumentStatus.FAILED)
            confidence = null; // A previous attempt is not the current classification score.
		Long uploadedById = document.getUploadedBy() == null ? null : document.getUploadedBy().getId();
		String typeCode = document.getType() == null ? document.getDocumentType() : document.getType().getCode();
		String typeName = document.getType() == null ? null : document.getType().getName();
		java.math.BigDecimal amount = document.getInvoice() == null ? null : document.getInvoice().getTotalAmount();
		return new DocumentResponse(document.getId(), document.getOriginalFileName(), document.getFileType(),
				document.getFileSize(), null, document.getStatus(), document.getStatus(), document.getReviewStatus(),
				document.getCompany() == null ? null : document.getCompany().getId(), document.getType() == null ? null : document.getType().getId(),
				typeCode, typeCode, typeName, amount, confidence,
				new CompanyRoleResponse(document.getCompanyRole(), document.getCompanyRoleConfidence(), document.getCompanyRoleReason()),
				document.getDocumentDirection(), new TransactionAssessmentResponse(document.getTransactionAssessmentType(), document.getTransactionAssessmentConfidence(), document.getTransactionAssessmentReason()), uploadedById,
				document.getCreatedAt(), document.getUpdatedAt());
	}

	private List<DocumentResponse> toResponses(List<Document> documents) {
		Map<Long, java.math.BigDecimal> confidenceByDocument = currentConfidenceByDocument(documents);
		return documents.stream().map(document -> toResponse(document, confidenceByDocument.get(document.getId()))).toList();
	}

	private Map<Long, java.math.BigDecimal> currentConfidenceByDocument(List<Document> documents) {
		if (documents.isEmpty()) return Map.of();
		List<Long> ids = documents.stream().map(Document::getId).toList();
		Map<Long, java.math.BigDecimal> result = new LinkedHashMap<>();
		classificationRepository.findCurrentByDocumentIds(ids)
				.forEach(classification -> result.put(classification.getDocument().getId(), classification.getConfidence()));
		return result;
	}

	private Specification<Document> scopeFor(User user, Long requestedCompanyId) {
		if (hasRole(user, "ADMIN")) {
			return requestedCompanyId == null ? Specification.where(null)
					: (root, query, builder) -> builder.equal(root.get("company").get("id"), requestedCompanyId);
		}
		if (hasRole(user, "EMPLOYEE") || hasRole(user, "USER")) {
			if (requestedCompanyId != null && (user.getCompany() == null || !requestedCompanyId.equals(user.getCompany().getId()))) throw new IllegalArgumentException("Bạn không thể truy cập công ty khác");
			return (root, query, builder) -> builder.equal(root.get("uploadedBy").get("id"), user.getId());
		}
		if (user.getCompany() == null) return (root, query, builder) -> builder.disjunction();
		if (requestedCompanyId != null && !requestedCompanyId.equals(user.getCompany().getId())) throw new IllegalArgumentException("Bạn không thể truy cập công ty khác");
		return (root, query, builder) -> builder.equal(root.get("company").get("id"), user.getCompany().getId());
	}
	
	private void uploadToMinio(MultipartFile file, String objectKey) {
		try (InputStream inputStream = file.getInputStream()) {
            String checksum;
            try(InputStream source=file.getInputStream()){
                var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int count;
                while((count=source.read(buffer))!=-1)digest.update(buffer,0,count);
                checksum=java.util.HexFormat.of().formatHex(digest.digest());
            }
			if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			}
			minioClient.putObject(PutObjectArgs.builder()
					.bucket(bucket)
					.object(objectKey)
					.stream(inputStream, file.getSize(), -1)
					.contentType(file.getContentType())
                    .userMetadata(java.util.Map.of("sha256",checksum))
					.build());
		} catch (Exception exception) {
			throw new com.example.invoice.exception.PipelineException(PipelineFailures.classify(exception, "STORAGE", 0), 503, exception);
		}
	}

	private String validateExistingObject(DocumentCreateRequest request, User user) {
		if (request.filePath() == null || request.filePath().isBlank())
			throw new com.example.invoice.exception.BadRequestException("Object key của tệp là bắt buộc");
		String expectedPrefix = user.getCompany() == null ? "system/" : "company-" + user.getCompany().getId() + "/";
		String objectKey = request.filePath().trim();
		if (!objectKey.startsWith(expectedPrefix))
			throw new com.example.invoice.exception.BadRequestException("Object không thuộc phạm vi được phép");
		try {
			StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
			if (stat.size() != request.fileSize())
				throw new com.example.invoice.exception.BadRequestException("Kích thước metadata không khớp object");
			if (stat.contentType() != null && !stat.contentType().equalsIgnoreCase(request.fileType()))
				throw new com.example.invoice.exception.BadRequestException("MIME metadata không khớp object");
			return objectKey;
		} catch (com.example.invoice.exception.BadRequestException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new com.example.invoice.exception.BadRequestException("Object không tồn tại hoặc không thể xác minh");
		}
	}

	private void registerRollbackCleanup(String objectKey) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCompletion(int status) {
				if (status != STATUS_ROLLED_BACK) return;
				try {
					minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
				} catch (Exception exception) {
					log.error("MINIO_ROLLBACK_CLEANUP_FAILED objectKey={} action=manual_cleanup_required", objectKey, exception);
				}
			}
		});
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getUserRoles().stream()
				.anyMatch(assignment -> assignment.getRole().isActive() && roleCode.equals(assignment.getRole().getCode()));
	}
}
