package com.example.invoice.dto.user;
import java.util.List;
import java.util.Set;
public record UserAccessResponse(List<AssignedRole> assignedRoles, Set<String> permissions, boolean adminBypass) {
    public record AssignedRole(Long id, String code, String name, boolean active) {}
}
