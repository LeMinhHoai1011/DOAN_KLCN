package com.example.invoice.repository;

import com.example.invoice.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {
    @Override
    @EntityGraph(attributePaths = "company")
    Optional<Document> findById(Long id);
    List<Document> findAllByCompanyId(Long companyId);
    @EntityGraph(attributePaths = "company")
    Optional<Document> findByIdAndCompanyId(Long id, Long companyId);
    List<Document> findAllByUploadedById(Long uploadedById);
    @EntityGraph(attributePaths = "company")
    Optional<Document> findByIdAndUploadedById(Long id, Long uploadedById);
    long countByCompanyId(Long companyId);
    long countByUploadedById(Long uploadedById);
}

