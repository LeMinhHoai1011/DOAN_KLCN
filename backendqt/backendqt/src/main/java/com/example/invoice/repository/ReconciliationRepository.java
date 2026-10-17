package com.example.invoice.repository;
import com.example.invoice.entity.Reconciliation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> { @org.springframework.data.jpa.repository.Query("select r from Reconciliation r where r.document.id = :documentId order by r.createdAt desc, r.id desc")
 List<Reconciliation> findByDocumentIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("documentId") Long documentId); }
