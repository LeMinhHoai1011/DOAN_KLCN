package com.example.invoice.repository;

import com.example.invoice.entity.AccountingCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingCategoryRepository extends JpaRepository<AccountingCategory, Long> {
 @org.springframework.data.jpa.repository.Query("select c from AccountingCategory c where c.company.id = :companyId and (:includeInactive = true or c.active = true) and (:type is null or c.categoryType = :type) order by c.categoryCode, c.id")
 List<AccountingCategory> search(@org.springframework.data.repository.query.Param("companyId") Long companyId,
   @org.springframework.data.repository.query.Param("includeInactive") boolean includeInactive,
   @org.springframework.data.repository.query.Param("type") com.example.invoice.entity.AccountingCategoryType type);
 @org.springframework.data.jpa.repository.Query("select count(c) > 0 from AccountingCategory c where c.company.id = :companyId and upper(trim(c.categoryCode)) = :code and (:excludeId is null or c.id <> :excludeId)")
 boolean duplicateCode(@org.springframework.data.repository.query.Param("companyId") Long companyId,
   @org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("excludeId") Long excludeId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from AccountingCategory c where c.id = :id")
 Optional<AccountingCategory> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

	List<AccountingCategory> findByCompanyId(Long companyId);
	List<AccountingCategory> findByCompanyIdAndActiveTrueOrderByCategoryCodeAsc(Long companyId);
	Optional<AccountingCategory> findByCompanyIdAndCategoryCodeAndActiveTrue(Long companyId, String categoryCode);
}
