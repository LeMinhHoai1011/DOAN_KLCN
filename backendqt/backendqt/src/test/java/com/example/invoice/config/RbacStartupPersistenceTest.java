package com.example.invoice.config;

import static org.junit.jupiter.api.Assertions.*;
import com.example.invoice.entity.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(RolePermissionInitializer.class)
class RbacStartupPersistenceTest {
	@Autowired EntityManager em;
	@Autowired RolePermissionInitializer initializer;

	private void startupEmitsNoSql() {
		var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		long before = statistics.getPrepareStatementCount();
		initializer.run(); initializer.run();
		assertEquals(before, statistics.getPrepareStatementCount(), "Startup must issue no SQL, including DDL/legacy SELECT");
	}

	@Test
	void emptyDatabaseDoesNotSilentlyReceiveRbacUsersOrCompanies() {
		startupEmitsNoSql();
		for (String entity : new String[] {"User", "Company", "Role", "Permission", "PermissionGroup", "RolePermission", "UserRoleAssignment"}) {
			assertEquals(0L, em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult());
		}
	}

	@Test
	void existingCustomizedRbacAndUserVersionsArePreserved() {
		PermissionGroup group = new PermissionGroup(); group.setCode("CUSTOM"); group.setName("Custom"); group.setActive(true); em.persist(group);
		Permission retained = permission("DOCUMENT_VIEW", group);
		permission("DOCUMENT_DOWNLOAD", group); // Deliberately revoked: no assignment.
		Role historical = new Role(); historical.setCode("USER"); historical.setName("Historical USER"); historical.setActive(false);
		RolePermission rp = new RolePermission(); rp.setRole(historical); rp.setPermission(retained); historical.getRolePermissions().add(rp); em.persist(historical);
		Role renamed = new Role(); renamed.setCode("CUSTOM_ADMIN"); renamed.setName("Renamed"); renamed.setActive(false); em.persist(renamed);
		Company company = new Company(); company.setCompanyName("Preserved owner"); em.persist(company);
		User user = new User(); user.setUsername("historical"); user.setFullName("Historical"); user.setEmail("history@example.test");
		user.setPassword("not-a-real-password"); user.setCompany(company); user.revokeTokens();
		UserRoleAssignment assignment = new UserRoleAssignment(); assignment.setUser(user); assignment.setRole(historical); user.getUserRoles().add(assignment); em.persist(user);
		em.flush();
		Long rowVersion = user.getRowVersion(), userId = user.getId(), roleId = historical.getId(), renamedId = renamed.getId(), companyId = company.getId();
		startupEmitsNoSql(); em.flush(); em.clear();
		User after = em.find(User.class, userId);
		assertEquals(rowVersion, after.getRowVersion()); assertEquals(1L, after.getTokenVersion());
		assertEquals(companyId, after.getCompany().getId());
		assertEquals(1, after.getUserRoles().size());
		assertEquals(roleId, after.getUserRoles().iterator().next().getRole().getId());
		Role roleAfter = em.find(Role.class, roleId);
		assertEquals("USER", roleAfter.getCode()); assertFalse(roleAfter.isActive()); assertEquals("Historical USER", roleAfter.getName());
		assertEquals(1, roleAfter.getRolePermissions().size());
		assertEquals("DOCUMENT_VIEW", roleAfter.getRolePermissions().iterator().next().getPermission().getCode());
		assertEquals("CUSTOM_ADMIN", em.find(Role.class, renamedId).getCode()); assertFalse(em.find(Role.class, renamedId).isActive());
		assertEquals(1L, em.createQuery("select count(u) from User u", Long.class).getSingleResult());
		assertEquals(1L, em.createQuery("select count(c) from Company c", Long.class).getSingleResult());
		assertEquals(2L, em.createQuery("select count(r) from Role r", Long.class).getSingleResult());
	}

	private Permission permission(String code, PermissionGroup group) {
		Permission permission = new Permission(); permission.setCode(code); permission.setName(code); permission.setActive(true); permission.setGroup(group); em.persist(permission); return permission;
	}
}
