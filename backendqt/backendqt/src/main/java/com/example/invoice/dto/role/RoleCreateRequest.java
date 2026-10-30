package com.example.invoice.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
		@NotBlank @Size(max = 50) String code,
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		boolean active,
        RoleSnapshot expected) {
    public RoleCreateRequest(String code, String name, String description, boolean active) {
        this(code, name, description, active, null);
    }
    public record RoleSnapshot(String code, String name, String description, boolean active) {}
}
