package com.example.invoice.repository;

import com.example.invoice.entity.AccountingEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingEntryRepository extends JpaRepository<AccountingEntry, Long> {
	List<AccountingEntry> findByDocumentId(Long documentId);
}
