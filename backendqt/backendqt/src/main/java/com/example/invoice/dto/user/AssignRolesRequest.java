package com.example.invoice.dto.user;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;
public record AssignRolesRequest(
    @NotEmpty Set<@NotNull @Positive Long> roleIds,
    Set<@NotNull @Positive Long> expectedRoleIds) {
    public AssignRolesRequest(Set<Long> roleIds) { this(roleIds, null); }
}
