package com.example.invoice.repository;
import com.example.invoice.entity.ProcessingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProcessingAttemptRepository extends JpaRepository<ProcessingAttempt,Long> {
    List<ProcessingAttempt> findByProcessingJobIdOrderByAttemptNumberAsc(Long jobId);
}
