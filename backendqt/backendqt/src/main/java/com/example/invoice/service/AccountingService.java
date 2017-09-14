package com.example.invoice.service;

import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.AccountingEntry;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.User;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.AccountingEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingService {
	private final AccountingCategoryRepository categoryRepository;
	private final AccountingEntryRepository entryRepository;
	private final DocumentService documentService;
	private final UserService userService;

	public List<AccountingCategory> findCategoriesByCompany(Long companyId) {
		return categoryRepository.findByCompanyId(companyId);
	}

	public List<AccountingEntry> findEntriesByDocument(Long documentId) {
		return entryRepository.findByDocumentId(documentId);
	}

	@Transactional
	public AccountingEntry createEntry(Long documentId, Long categoryId, BigDecimal amount, String description,
			LocalDate entryDate, Authentication authentication) {
		Document document = documentService.load(documentId);
		AccountingCategory category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new com.example.invoice.exception.ResourceNotFoundException("Category not found"));
		User createdBy = authentication == null ? null : userService.loadCurrent(authentication);
		AccountingEntry entry = new AccountingEntry();
		entry.setDocument(document);
		entry.setCategory(category);
		entry.setCreatedBy(createdBy);
		entry.setAmount(amount);
		entry.setDescription(description);
		entry.setEntryDate(entryDate);
		return entryRepository.save(entry);
	}
}
