package com.example.invoice.repository;
import com.example.invoice.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob,Long> {
    boolean existsByDocumentIdAndJobTypeAndStatus(Long documentId, String jobType, String status);
    Optional<ProcessingJob> findFirstByDocumentIdAndJobTypeOrderByIdDesc(Long documentId, String jobType);
    List<ProcessingJob> findByDocumentIdAndJobTypeOrderByIdDesc(Long documentId, String jobType);
}
