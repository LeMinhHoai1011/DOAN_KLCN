package com.smartinvoice.demo.repository;

import com.smartinvoice.demo.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}