package com.example.invoice.repository;

import com.example.invoice.entity.ExtractedField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Long> {
	List<ExtractedField> findByInvoiceId(Long invoiceId);
}
