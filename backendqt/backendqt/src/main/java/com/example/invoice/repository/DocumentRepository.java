package com.example.invoice.repository;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {
    @Override
    @EntityGraph(attributePaths = {"company", "uploadedBy", "type", "invoice"})
    List<Document> findAll();

    @Override
    @EntityGraph(attributePaths = {"company", "uploadedBy", "type", "invoice"})
    Page<Document> findAll(Specification<Document> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "company")
    Optional<Document> findById(Long id);
    @EntityGraph(attributePaths = {"company", "uploadedBy", "type", "invoice"})
    List<Document> findAllByCompanyId(Long companyId);
    @EntityGraph(attributePaths = "company")
    Optional<Document> findByIdAndCompanyId(Long id, Long companyId);
    @EntityGraph(attributePaths = {"company", "uploadedBy", "type", "invoice"})
    List<Document> findAllByUploadedById(Long uploadedById);
    @EntityGraph(attributePaths = "company")
    Optional<Document> findByIdAndUploadedById(Long id, Long uploadedById);
    long countByCompanyId(Long companyId);
    long countByUploadedById(Long uploadedById);
    long countByStatus(DocumentStatus status);
    long countByCompanyIdAndStatus(Long companyId, DocumentStatus status);
    long countByUploadedByIdAndStatus(Long uploadedById, DocumentStatus status);
    long countByTypeId(Long typeId);
	@Query("select document.status, count(document.id) from Document document group by document.status") List<Object[]> countAllGroupedByStatus();
	@Query("select document.status, count(document.id) from Document document where document.company.id = :companyId group by document.status") List<Object[]> countByCompanyGroupedByStatus(@Param("companyId") Long companyId);
	@Query("select document.status, count(document.id) from Document document where document.uploadedBy.id = :userId group by document.status") List<Object[]> countByUploaderGroupedByStatus(@Param("userId") Long userId);
	@Query("select coalesce(type.code, document.documentType), coalesce(type.name, document.documentType), count(document.id) from Document document left join document.type type where type.id is not null or document.documentType is not null group by coalesce(type.code, document.documentType), coalesce(type.name, document.documentType) order by count(document.id) desc") List<Object[]> countAllGroupedByType();
	@Query("select coalesce(type.code, document.documentType), coalesce(type.name, document.documentType), count(document.id) from Document document left join document.type type where document.company.id = :companyId and (type.id is not null or document.documentType is not null) group by coalesce(type.code, document.documentType), coalesce(type.name, document.documentType) order by count(document.id) desc") List<Object[]> countByCompanyGroupedByType(@Param("companyId") Long companyId);
	@Query("select coalesce(type.code, document.documentType), coalesce(type.name, document.documentType), count(document.id) from Document document left join document.type type where document.uploadedBy.id = :userId and (type.id is not null or document.documentType is not null) group by coalesce(type.code, document.documentType), coalesce(type.name, document.documentType) order by count(document.id) desc") List<Object[]> countByUploaderGroupedByType(@Param("userId") Long userId);
}

