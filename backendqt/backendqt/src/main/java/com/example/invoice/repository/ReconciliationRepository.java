package com.example.invoice.repository;
import com.example.invoice.entity.Reconciliation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> { List<Reconciliation> findByDocumentIdOrderByCreatedAtDesc(Long documentId); }
