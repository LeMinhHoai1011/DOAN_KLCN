package com.smartinvoice.demo.controller;

import com.smartinvoice.demo.model.Document;
import com.smartinvoice.demo.repository.DocumentRepository;
import com.smartinvoice.demo.service.MinioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentUploadController {

    private final DocumentRepository documentRepository;
    private final MinioService minioService;

    public DocumentUploadController(
            DocumentRepository documentRepository,
            MinioService minioService) {

        this.documentRepository = documentRepository;
        this.minioService = minioService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        try {

            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("File không được để trống");
            }

            // Upload file thật lên MinIO
            String objectName = minioService.uploadFile(file);

            // Lưu thông tin file vào PostgreSQL
            Document document = new Document();

            document.setFileName(file.getOriginalFilename());
            document.setFileType(file.getContentType());
            document.setFileUrl(objectName);
            document.setDocumentType("Chưa phân loại");
            document.setStatus("UPLOADED");
            document.setUploadDate(LocalDateTime.now());

            Document savedDocument =
                    documentRepository.save(document);

            return ResponseEntity.ok(savedDocument);

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body("Upload thất bại: " + e.getMessage());
        }
    }
}