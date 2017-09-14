package com.example.invoice.repository;

import com.example.invoice.entity.Invoice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
	Optional<Invoice> findByDocumentId(Long documentId);
}
