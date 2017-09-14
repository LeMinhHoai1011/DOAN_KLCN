package com.example.invoice.repository;

import com.example.invoice.entity.FieldCorrection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldCorrectionRepository extends JpaRepository<FieldCorrection, Long> {
	List<FieldCorrection> findByFieldId(Long fieldId);
}
