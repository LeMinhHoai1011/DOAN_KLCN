package com.example.invoice.repository;

import com.example.invoice.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long>, JpaSpecificationExecutor<FinancialTransaction> {
 boolean existsByAccountingCategoryId(Long categoryId);
 boolean existsByAccountingCategoryIdAndTransactionTypeNot(Long categoryId, String transactionType);

	java.util.Optional<FinancialTransaction> findByCompanyIdAndRequestKey(Long companyId,String key);
 @Query("select t from FinancialTransaction t where t.company.id=:company and (t.document.id=:document or (:invoice is not null and t.invoice.id=:invoice)) order by t.id")
 List<FinancialTransaction> linked(@Param("company") Long company,@Param("document") Long document,@Param("invoice") Long invoice);
 @Query("select count(t) from FinancialTransaction t where t.paymentConfirmation is null and (:company is null or t.company.id=:company)")
 long countUnverified(@Param("company") Long company);
 boolean existsByDocumentId(Long documentId);
	boolean existsByInvoiceId(Long invoiceId);
	@Query("select coalesce(sum(case when t.transactionType = 'INCOME' then t.amount else 0 end), 0), coalesce(sum(case when t.transactionType = 'EXPENSE' then t.amount else 0 end), 0) from FinancialTransaction t where t.paymentConfirmation = 'CONFIRMED' and (:companyId is null or t.company.id = :companyId) and (cast(:dateFrom as date) is null or t.transactionDate >= :dateFrom) and (cast(:dateTo as date) is null or t.transactionDate <= :dateTo)")
	List<Object[]> summarize(@Param("companyId") Long companyId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

	@Query("select category.categoryName, coalesce(sum(t.amount), 0) from FinancialTransaction t left join t.accountingCategory category where t.paymentConfirmation = 'CONFIRMED' and t.transactionType = 'EXPENSE' and (:companyId is null or t.company.id = :companyId) and (cast(:dateFrom as date) is null or t.transactionDate >= :dateFrom) and (cast(:dateTo as date) is null or t.transactionDate <= :dateTo) group by category.categoryName order by sum(t.amount) desc")
	List<Object[]> expensesByCategory(@Param("companyId") Long companyId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

    @Query("select t.transactionDate, coalesce(sum(case when t.transactionType = 'INCOME' then t.amount else 0 end), 0), coalesce(sum(case when t.transactionType = 'EXPENSE' then t.amount else 0 end), 0) from FinancialTransaction t where t.paymentConfirmation = 'CONFIRMED' and (:companyId is null or t.company.id = :companyId) and (cast(:dateFrom as date) is null or t.transactionDate >= :dateFrom) and (cast(:dateTo as date) is null or t.transactionDate <= :dateTo) group by t.transactionDate order by t.transactionDate")
    List<Object[]> dailyTotals(@Param("companyId") Long companyId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
