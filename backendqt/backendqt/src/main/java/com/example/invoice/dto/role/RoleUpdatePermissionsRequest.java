package com.example.invoice.dto.role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;
public record RoleUpdatePermissionsRequest(
    @NotNull Set<@NotNull @Positive Long> permissionIds,
    Set<@NotNull @Positive Long> expectedPermissionIds) {
    public RoleUpdatePermissionsRequest(Set<Long> permissionIds) { this(permissionIds, null); }
}
