package com.example.invoice;

import com.example.invoice.dto.role.*;
import com.example.invoice.entity.*;
import com.example.invoice.exception.RbacConflictException;
import com.example.invoice.repository.*;
import com.example.invoice.service.RoleService;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:rbac_management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "app.internal-company.id=9001" })
@AutoConfigureMockMvc @ActiveProfiles("test")
@Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class RbacManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired UserRepository users;
    @Autowired RoleService service;
    @Autowired com.example.invoice.service.UserService userService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void isolatedDatabaseOnly() {
        assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c ->
            c.getMetaData().getURL().startsWith("jdbc:h2:mem:")));
        if (jdbc.queryForObject("select count(*) from companies where company_id=9001", Integer.class) == 0)
            jdbc.update("insert into companies(company_id,company_name) values (9001,'RBAC test only')");
    }
    String token(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username, "password", password))))
            .andExpect(status().isOk()).andReturn();
        return "Bearer " + body(result).get("token").asText();
    }
    String admin() throws Exception { return token("admin", "Admin@123456"); }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
    JsonNode createRole(String auth) throws Exception {
        return body(mvc.perform(post("/api/v1/roles").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "code", "CUSTOM_" + UUID.randomUUID(), "name", "Custom role", "active", true))))
            .andExpect(status().isCreated()).andReturn());
    }
    Set<Long> ids(String... codes) {
        Set<Long> result = new HashSet<>();
        for (String code : codes) result.add(permissions.findByCode(code).orElseThrow().getId());
        return result;
    }
    ResultActions save(String auth, long id, Set<Long> value, Set<Long> expected) throws Exception {
        return mvc.perform(put("/api/v1/roles/" + id + "/permissions").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "permissionIds", value, "expectedPermissionIds", expected))));
    }
    JsonNode createUser(String auth) throws Exception {
        String name = "rbac-" + UUID.randomUUID();
        return body(mvc.perform(post("/api/v1/users").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", "Test@123456", "fullName", "RBAC test",
                "email", name + "@example.test", "role", "EMPLOYEE")))).andExpect(status().isOk()).andReturn());
    }

    @Test void batchedPermissionSaveRetainsJoinRowsAndRejectsStaleSnapshot() throws Exception {
        String auth = admin(); long id = createRole(auth).get("id").asLong();
        var first = ids("DOCUMENT_VIEW", "DOCUMENT_CREATE");
        save(auth, id, first, Set.of()).andExpect(status().isOk());
        var second = ids("DOCUMENT_VIEW", "DOCUMENT_DOWNLOAD");
        save(auth, id, second, first).andExpect(status().isOk());
        save(auth, id, Set.of(), first).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("RBAC_UPDATE_CONFLICT"));
        assertEquals(Set.of("DOCUMENT_VIEW", "DOCUMENT_DOWNLOAD"), service.findRoleById(id).permissions());
        save(auth, id, Set.of(-1L), second).andExpect(status().isBadRequest());
        assertEquals(2, service.findRoleById(id).permissions().size());
    }

    @Test void simultaneousPermissionWritersHaveExactlyOneWinner() throws Exception {
        long id = createRole(admin()).get("id").asLong();
        var start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (var value : List.of(ids("DOCUMENT_VIEW"), ids("DOCUMENT_CREATE"))) jobs.add(() -> {
                start.await(10, TimeUnit.SECONDS);
                try { service.updateRolePermissions(id, new RoleUpdatePermissionsRequest(value, Set.of())); return true; }
                catch (RbacConflictException conflict) { return false; }
            });
            int winners = 0;
            for (var future : pool.invokeAll(jobs, 20, TimeUnit.SECONDS)) if (future.get()) winners++;
            assertEquals(1, winners); assertEquals(1, service.findRoleById(id).permissions().size());
        } finally { pool.shutdownNow(); }
    }

    @Test void metadataConflictsAndAtomicCopyPreserveSource() throws Exception {
        String auth = admin(); JsonNode source = createRole(auth); long id = source.get("id").asLong();
        save(auth, id, ids("DOCUMENT_VIEW"), Set.of()).andExpect(status().isOk());
        Map<String, Object> expected = new HashMap<>();
        expected.put("code", source.get("code").asText()); expected.put("name", "Custom role");
        expected.put("description", null); expected.put("active", true);
        var update = Map.of("code", source.get("code").asText(), "name", "Renamed", "active", true, "expected", expected);
        String payload = json.writeValueAsString(update);
        mvc.perform(put("/api/v1/roles/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/roles/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        var copy = body(mvc.perform(post("/api/v1/roles/" + id + "/copy").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", "COPY_" + UUID.randomUUID(), "name", "Copied", "active", true))))
            .andExpect(status().isCreated()).andReturn());
        assertEquals(Set.of("DOCUMENT_VIEW"), service.findRoleById(copy.get("id").asLong()).permissions());
        assertEquals("Renamed", service.findRoleById(id).name());
        long count = roles.count();
        mvc.perform(post("/api/v1/roles/" + id + "/copy").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", source.get("code").asText(), "name", "Duplicate", "active", true))))
            .andExpect(status().isBadRequest());
        assertEquals(count, roles.count());
    }

    @Test void multiRoleAssignmentCustomAuthorityAndExistingJwtReflectChanges() throws Exception {
        String auth = admin(); var custom = createRole(auth); long roleId = custom.get("id").asLong();
        save(auth, roleId, ids("DOCUMENT_VIEW"), Set.of()).andExpect(status().isOk());
        var user = createUser(auth); long userId = user.get("id").asLong();
        String existingToken = token(user.get("username").asText(), "Test@123456");
        long employee = roles.findByCode("EMPLOYEE").orElseThrow().getId();
        mvc.perform(put("/api/v1/users/" + userId + "/roles").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("roleIds", Set.of(employee, roleId), "expectedRoleIds", Set.of(employee)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(put("/api/v1/users/" + userId + "/roles").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("roleIds", Set.of(roleId), "expectedRoleIds", Set.of(employee)))))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/users/" + userId + "/roles").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("roleIds", Set.of(roleId), "expectedRoleIds", Set.of(employee, roleId)))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/" + userId + "/access").header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assignedRoles.length()").value(1))
            .andExpect(jsonPath("$.permissions[0]").value("DOCUMENT_VIEW")).andExpect(jsonPath("$.adminBypass").value(false));
        mvc.perform(get("/api/v1/documents").header("Authorization", existingToken)).andExpect(status().isOk());
        save(auth, roleId, Set.of(), ids("DOCUMENT_VIEW")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/documents").header("Authorization", existingToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + userId + "/access").header("Authorization", existingToken)).andExpect(status().isForbidden());
    }

    @Test void nonAdminsCannotConfigureRolesOrReadOtherAccountPermissions() throws Exception {
        String auth = token("accountant", "Accountant@123"); long employee = users.findByUsername("employee").orElseThrow().getId();
        mvc.perform(get("/api/v1/roles").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/permissions/groups").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + employee + "/access").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/" + employee + "/roles").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[1]}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/roles/1/permissions").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"permissionIds\":[]}" )).andExpect(status().isForbidden());
    }

    @Test void concurrentAccountAssignmentsRejectOneStaleWriter() throws Exception {
        String auth = admin(); long userId = createUser(auth).get("id").asLong();
        long employee = roles.findByCode("EMPLOYEE").orElseThrow().getId();
        long accountant = roles.findByCode("ACCOUNTANT").orElseThrow().getId();
        long custom = createRole(auth).get("id").asLong();
        var start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (var value : List.of(Set.of(employee, custom), Set.of(accountant))) jobs.add(() -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin", "unused"));
                try {
                    start.await(10, TimeUnit.SECONDS);
                    userService.assignRoles(userId, new com.example.invoice.dto.user.AssignRolesRequest(value, Set.of(employee)));
                    return true;
                } catch (RbacConflictException conflict) { return false; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            int winners = 0;
            for (var future : pool.invokeAll(jobs, 20, TimeUnit.SECONDS)) if (future.get()) winners++;
            assertEquals(1, winners);
        } finally { pool.shutdownNow(); }
    }

    @Test void readonlyAccessIncludesInactiveAssignmentsButExcludesTheirPermissions() throws Exception {
        String auth = admin(); long userId = createUser(auth).get("id").asLong();
        var created = createRole(auth); long roleId = created.get("id").asLong();
        save(auth, roleId, ids("ACCOUNTING_VIEW"), Set.of()).andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/" + userId + "/roles").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("roleIds", Set.of(roleId))))).andExpect(status().isOk());
        service.updateRole(roleId, new RoleCreateRequest(created.get("code").asText(), "Disabled", null, false));
        mvc.perform(get("/api/v1/users/" + userId + "/access").header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assignedRoles.length()").value(1))
            .andExpect(jsonPath("$.assignedRoles[0].active").value(false)).andExpect(jsonPath("$.permissions.length()").value(0));
    }

    @Test void inactiveAndHistoricalRolesAreRejectedAndSystemRolesRemainProtected() throws Exception {
        String auth = admin(); var user = createUser(auth); long userId = user.get("id").asLong();
        long id = createRole(auth).get("id").asLong(); var custom = service.findRoleById(id);
        service.updateRole(id, new RoleCreateRequest(custom.code(), custom.name(), null, false));
        long historical = roles.findByCode("USER").orElseThrow().getId();
        for (long invalid : List.of(id, historical)) mvc.perform(put("/api/v1/users/" + userId + "/roles")
            .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("roleIds", Set.of(invalid)))))
            .andExpect(status().isBadRequest());
        long adminId = roles.findByCode("ADMIN").orElseThrow().getId();
        mvc.perform(put("/api/v1/roles/" + adminId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ADMIN\",\"name\":\"Admin\",\"active\":false}" )).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/roles/" + adminId).header("Authorization", auth)).andExpect(status().isBadRequest());
    }
}
