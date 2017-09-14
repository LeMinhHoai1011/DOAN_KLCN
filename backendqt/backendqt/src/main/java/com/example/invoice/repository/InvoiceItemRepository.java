package com.example.invoice.repository;

import com.example.invoice.entity.InvoiceItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
	List<InvoiceItem> findByInvoiceId(Long invoiceId);
}
