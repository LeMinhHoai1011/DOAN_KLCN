package com.example.invoice.dto.user;
import java.util.Set;
/** UserService validates the lists: bean validation here would answer 400 before @PreAuthorize can answer 403. */
public record AssignRolesRequest(
    Set<Long> roleIds,
    Set<Long> expectedRoleIds) {
    public AssignRolesRequest(Set<Long> roleIds) { this(roleIds, null); }
}
