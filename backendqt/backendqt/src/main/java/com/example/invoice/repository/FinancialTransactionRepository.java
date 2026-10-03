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
	@Query("select coalesce(sum(case when t.transactionType = 'INCOME' then t.amount else 0 end), 0), coalesce(sum(case when t.transactionType = 'EXPENSE' then t.amount else 0 end), 0) from FinancialTransaction t where (:companyId is null or t.company.id = :companyId) and (:dateFrom is null or t.transactionDate >= :dateFrom) and (:dateTo is null or t.transactionDate <= :dateTo)")
	List<Object[]> summarize(@Param("companyId") Long companyId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

	@Query("select t.accountingCategory.categoryName, coalesce(sum(t.amount), 0) from FinancialTransaction t where t.transactionType = 'EXPENSE' and (:companyId is null or t.company.id = :companyId) and (:dateFrom is null or t.transactionDate >= :dateFrom) and (:dateTo is null or t.transactionDate <= :dateTo) group by t.accountingCategory.categoryName order by sum(t.amount) desc")
	List<Object[]> expensesByCategory(@Param("companyId") Long companyId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);

	@Query(value = "select to_char(date_trunc(cast(:period as text), transaction_date), case when :period = 'month' then 'YYYY-MM' else 'YYYY-MM-DD' end), coalesce(sum(case when transaction_type = 'INCOME' then amount else 0 end),0), coalesce(sum(case when transaction_type = 'EXPENSE' then amount else 0 end),0) from financial_transactions where (:companyId is null or company_id = :companyId) and (:dateFrom is null or transaction_date >= :dateFrom) and (:dateTo is null or transaction_date <= :dateTo) group by 1 order by 1", nativeQuery = true)
	List<Object[]> timeSeries(@Param("companyId") Long companyId, @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo, @Param("period") String period);
}
