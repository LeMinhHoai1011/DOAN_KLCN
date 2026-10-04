package com.example.invoice.config;

import com.example.invoice.entity.Permission;
import com.example.invoice.entity.PermissionGroup;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.RolePermission;
import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRoleAssignment;
import com.example.invoice.repository.PermissionGroupRepository;
import com.example.invoice.repository.PermissionRepository;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
@Order(2)
@RequiredArgsConstructor
public class RolePermissionInitializer implements CommandLineRunner {
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void fixLegacyRolesSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE roles ALTER COLUMN role_name DROP NOT NULL");
        } catch (org.springframework.dao.DataAccessException ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE roles DROP COLUMN IF EXISTS name");
        } catch (org.springframework.dao.DataAccessException ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE roles ALTER COLUMN created_at DROP NOT NULL");
        } catch (org.springframework.dao.DataAccessException ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE roles ALTER COLUMN updated_at DROP NOT NULL");
        } catch (org.springframework.dao.DataAccessException ignored) {}
    }

    private final RoleRepository roleRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        fixLegacyRolesSchema();
        // 1. Seed Permission Groups
        PermissionGroup userGroup = createGroupIfMissing("USER_MANAGEMENT", "User Management", "Permissions related to users");
        PermissionGroup docGroup = createGroupIfMissing("DOCUMENT", "Document", "Permissions related to documents");
        PermissionGroup invoiceGroup = createGroupIfMissing("INVOICE", "Invoice", "Permissions related to invoices");
        PermissionGroup ocrGroup = createGroupIfMissing("OCR", "OCR", "Permissions related to OCR");
        PermissionGroup classificationGroup = createGroupIfMissing("CLASSIFICATION", "Classification", "Permissions related to AI classification");
        PermissionGroup accGroup = createGroupIfMissing("ACCOUNTING", "Accounting", "Permissions related to accounting");
        PermissionGroup reportGroup = createGroupIfMissing("REPORT", "Report", "Permissions related to reports");
        PermissionGroup systemGroup = createGroupIfMissing("SYSTEM", "System", "System level permissions");

        // 2. Seed Permissions
        Permission userView = createPermissionIfMissing("USER_VIEW", "View Users", userGroup);
        Permission userCreate = createPermissionIfMissing("USER_CREATE", "Create Users", userGroup);
        Permission userUpdate = createPermissionIfMissing("USER_UPDATE", "Update Users", userGroup);
        Permission userDelete = createPermissionIfMissing("USER_DELETE", "Delete Users", userGroup);

        Permission docView = createPermissionIfMissing("DOCUMENT_VIEW", "View Documents", docGroup);
        Permission docCreate = createPermissionIfMissing("DOCUMENT_CREATE", "Create Documents", docGroup);
        Permission docUpdate = createPermissionIfMissing("DOCUMENT_UPDATE", "Update Documents", docGroup);
        Permission docDelete = createPermissionIfMissing("DOCUMENT_DELETE", "Delete Documents", docGroup);
        Permission docDownload = createPermissionIfMissing("DOCUMENT_DOWNLOAD", "Download Documents", docGroup);

        Permission invView = createPermissionIfMissing("INVOICE_VIEW", "View Invoices", invoiceGroup);
        Permission invCreate = createPermissionIfMissing("INVOICE_CREATE", "Create Invoices", invoiceGroup);
        Permission invUpdate = createPermissionIfMissing("INVOICE_UPDATE", "Update Invoices", invoiceGroup);
        Permission invDelete = createPermissionIfMissing("INVOICE_DELETE", "Delete Invoices", invoiceGroup);

        Permission ocrView = createPermissionIfMissing("OCR_VIEW", "View OCR", ocrGroup);
        Permission ocrUpdate = createPermissionIfMissing("OCR_UPDATE", "Update OCR", ocrGroup);

        Permission classView = createPermissionIfMissing("CLASSIFICATION_VIEW", "View Classification", classificationGroup);
        Permission classUpdate = createPermissionIfMissing("CLASSIFICATION_UPDATE", "Update Classification", classificationGroup);

        Permission accView = createPermissionIfMissing("ACCOUNTING_VIEW", "View Accounting", accGroup);
        Permission accCreate = createPermissionIfMissing("ACCOUNTING_CREATE", "Create Accounting", accGroup);
        Permission accUpdate = createPermissionIfMissing("ACCOUNTING_UPDATE", "Update Accounting", accGroup);
        Permission accDelete = createPermissionIfMissing("ACCOUNTING_DELETE", "Delete Accounting", accGroup);

        Permission reportView = createPermissionIfMissing("REPORT_VIEW", "View Reports", reportGroup);
        Permission reportExport = createPermissionIfMissing("REPORT_EXPORT", "Export Reports", reportGroup);

        Permission sysConfig = createPermissionIfMissing("SYSTEM_CONFIG", "System Configuration", systemGroup);

        // 3. Seed Roles & Map Permissions
        List<Permission> adminPerms = Arrays.asList(
                userView, userCreate, userUpdate, userDelete,
                docView, docCreate, docUpdate, docDelete, docDownload,
                invView, invCreate, invUpdate, invDelete,
                ocrView, ocrUpdate, classView, classUpdate,
                accView, accCreate, accUpdate, accDelete,
                reportView, reportExport, sysConfig
        );
        Role roleAdmin = createRoleIfMissing("ADMIN", "Administrator", "Full access administrator");
        assignPermissions(roleAdmin, adminPerms);

        List<Permission> accountantPerms = Arrays.asList(
                docView, docCreate, docUpdate, docDownload,
                invView, invCreate, invUpdate,
                ocrView, classView,
                accView, accCreate, accUpdate, accDelete,
                reportView, reportExport
        );
        Role roleAccountant = createRoleIfMissing("ACCOUNTANT", "Accountant", "Accounting operations");
        assignPermissions(roleAccountant, accountantPerms);

        List<Permission> employeePerms = Arrays.asList(
                docView, docCreate, docDownload,
                invView, invCreate,
                ocrView, classView
        );
        Role roleEmployee = createRoleIfMissing("EMPLOYEE", "Employee", "Standard employee operations");
        assignPermissions(roleEmployee, employeePerms);

        List<Permission> userPerms = Arrays.asList(
                docView, docDownload, invView
        );
        Role roleUser = createRoleIfMissing("USER", "Standard User", "Basic read access");
        assignPermissions(roleUser, userPerms);

        // 4. Migrate Existing Users
        migrateExistingUsers();
    }

    private PermissionGroup createGroupIfMissing(String code, String name, String desc) {
        return permissionGroupRepository.findByCode(code).orElseGet(() -> {
            PermissionGroup g = new PermissionGroup();
            g.setCode(code);
            g.setName(name);
            g.setDescription(desc);
            g.setActive(true);
            return permissionGroupRepository.save(g);
        });
    }

    private Permission createPermissionIfMissing(String code, String name, PermissionGroup group) {
        return permissionRepository.findByCode(code).orElseGet(() -> {
            Permission p = new Permission();
            p.setCode(code);
            p.setName(name);
            p.setGroup(group);
            p.setActive(true);
            return permissionRepository.save(p);
        });
    }

    private Role createRoleIfMissing(String code, String name, String desc) {
        return roleRepository.findByCode(code).orElseGet(() -> {
            Role r = new Role();
            r.setCode(code);
            r.setName(name);
            r.setDescription(desc);
            r.setActive(true);
            return roleRepository.save(r);
        });
    }

    private void assignPermissions(Role role, List<Permission> permissions) {
        for (Permission permission : permissions) {
            boolean alreadyAssigned = role.getRolePermissions().stream()
                    .anyMatch(assignment -> assignment.getPermission().getId().equals(permission.getId()));
            if (!alreadyAssigned) {
                RolePermission assignment = new RolePermission();
                assignment.setRole(role);
                assignment.setPermission(permission);
                role.getRolePermissions().add(assignment);
            }
        }
        roleRepository.save(role);
    }

    private void migrateExistingUsers() {
        List<User> users = userRepository.findAll();
        for (User u : users) {
            String legacyRoleCode = readLegacyRoleCode(u.getId());
            if (legacyRoleCode == null) {
                continue;
            }
            Role role = roleRepository.findByCode(legacyRoleCode.toUpperCase()).orElse(null);
            if (role == null) {
                continue;
            }
            Set<String> builtInRoles = Set.of("ADMIN", "ACCOUNTANT", "EMPLOYEE", "USER");
            boolean alreadyAssigned = u.getUserRoles().stream()
                    .anyMatch(assignment -> assignment.getRole().getId().equals(role.getId()));
            if (!alreadyAssigned) {
                u.getUserRoles().removeIf(assignment -> builtInRoles.contains(assignment.getRole().getCode()));
                UserRoleAssignment assignment = new UserRoleAssignment();
                assignment.setUser(u);
                assignment.setRole(role);
                u.getUserRoles().add(assignment);
                userRepository.save(u);
            }
        }
    }

    private String readLegacyRoleCode(Long userId) {
        try {
            return jdbcTemplate.queryForObject("select role from users where user_id = ?", String.class, userId);
        } catch (org.springframework.dao.DataAccessException ignored) {
            return null;
        }
    }
}

