package com.example.invoice.repository;

import com.example.invoice.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from Company c where c.id = :id")
 java.util.Optional<Company> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

}
