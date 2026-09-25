package com.example.invoice.repository;

import com.example.invoice.entity.PermissionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, Long> {
    Optional<PermissionGroup> findByCode(String code);
}
