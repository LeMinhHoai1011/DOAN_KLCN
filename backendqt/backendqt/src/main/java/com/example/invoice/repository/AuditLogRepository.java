package com.example.invoice.repository;
import com.example.invoice.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditLogRepository extends JpaRepository<AuditLog,Long> {
 List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(String type,String id);
 List<AuditLog> findByEntityTypeAndCompanyIdOrderByCreatedAtDescIdDesc(String type,Long companyId);
}
