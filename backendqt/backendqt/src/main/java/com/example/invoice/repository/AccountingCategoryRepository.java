package com.example.invoice.repository;

import com.example.invoice.entity.AccountingCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingCategoryRepository extends JpaRepository<AccountingCategory, Long> {
	List<AccountingCategory> findByCompanyId(Long companyId);
	List<AccountingCategory> findByCompanyIdAndActiveTrueOrderByCategoryCodeAsc(Long companyId);
	Optional<AccountingCategory> findByCompanyIdAndCategoryCodeAndActiveTrue(Long companyId, String categoryCode);
}
