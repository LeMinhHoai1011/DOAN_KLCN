package com.example.invoice.repository;

import com.example.invoice.entity.ClassificationCorrection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassificationCorrectionRepository extends JpaRepository<ClassificationCorrection, Long> {
	List<ClassificationCorrection> findByClassificationId(Long classificationId);
}
