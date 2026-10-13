package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.invoice.entity.Role;
import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRoleAssignment;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserMapperDynamicRoleTest {
	@Test
	void returnsCustomDatabaseRoleWithoutAnEnumEntry() {
		Role customRole = new Role();
		customRole.setCode("AUDITOR");
		customRole.setActive(true);
		UserRoleAssignment assignment = new UserRoleAssignment();
		assignment.setRole(customRole);
		User user = new User();
		user.setUserRoles(Set.of(assignment));

		var response = new UserMapper().toResponse(user);

		assertEquals("AUDITOR", response.role());
		assertEquals(Set.of("AUDITOR"), response.roles());
	}
}
