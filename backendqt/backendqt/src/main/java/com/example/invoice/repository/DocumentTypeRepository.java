package com.example.invoice.repository;

import com.example.invoice.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    Optional<DocumentType> findByCode(String code);
}
