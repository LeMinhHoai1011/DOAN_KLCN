package com.example.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.User;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.FieldCorrectionRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.service.DashboardService;
import com.example.invoice.service.DocumentService;
import com.example.invoice.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"app.demo-admin.enabled=false", "app.demo-users.enabled=false"})
@org.springframework.context.annotation.Import(com.example.invoice.support.IsolatedAccountFixtures.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceApplicationTests {
	@Autowired
	private com.example.invoice.config.InternalCompanyProperties internalCompanyProperties;
	private String previousInternalCompanyId;

	@org.junit.jupiter.api.BeforeEach
	void isolateInternalCompanyConfiguration() {
		previousInternalCompanyId = internalCompanyProperties.getId();
		internalCompanyProperties.setId(null);
	}

	@org.junit.jupiter.api.AfterEach
	void restoreInternalCompanyConfiguration() {
		internalCompanyProperties.setId(previousInternalCompanyId);
	}

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CompanyRepository companyRepository;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private ExtractedFieldRepository extractedFieldRepository;

	@Autowired
	private FieldCorrectionRepository fieldCorrectionRepository;

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private DocumentService documentService;

	@Autowired
	private InvoiceService invoiceService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

    @Test
    @Transactional
    void itemOnlyCorrectionPreservesHeaderPersistsRowsAndReconcilesAgain() throws Exception {
        Company company = provisionTestCompany();
        Document document = completionDocument(company,"items-"+UUID.randomUUID()+".pdf",DocumentStatus.PROCESSED);
        Invoice invoice = new Invoice(); invoice.setDocument(document); invoice.setInvoiceNumber("KEEP-NUMBER");
        invoice.setSellerName("Keep seller"); invoice.setSubtotal(new BigDecimal("200"));
        invoice.setVatAmount(new BigDecimal("20")); invoice.setTotalAmount(new BigDecimal("220"));
        var item = new com.example.invoice.entity.InvoiceItem(); item.setInvoice(invoice); item.setProductName("Paper");
        item.setQuantity(new BigDecimal("2")); item.setUnitPrice(new BigDecimal("100"));
        item.setTaxRate(new BigDecimal("10")); item.setAmount(new BigDecimal("150")); item.setTaxAmount(new BigDecimal("15"));
        invoice.getItems().add(item); invoiceRepository.saveAndFlush(invoice);
        Long invoiceId=invoice.getId(), documentId=document.getId();
        String auth="Bearer "+loginToken("admin","Admin@123456");
        String reconciliation="/api/v1/documents/"+documentId+"/reconciliations";
        var before=mockMvc.perform(post(reconciliation).header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(before.getResponse().getContentAsString()).findValuesAsText("status").contains("MISMATCH"));
        String rows="{\"items\":[{\"productName\":\"Corrected paper\",\"quantity\":2,\"unitPrice\":100,\"taxRate\":10,\"taxAmount\":20,\"amount\":200}]}";
        mockMvc.perform(put("/api/v1/invoices/"+invoiceId+"/items").header("Authorization",auth)
                .contentType(MediaType.APPLICATION_JSON).content(rows))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceNumber").value("KEEP-NUMBER"))
                .andExpect(jsonPath("$.sellerName").value("Keep seller")).andExpect(jsonPath("$.totalAmount").value(220));
        entityManager.flush(); entityManager.clear();
        var persisted=invoiceRepository.findById(invoiceId).orElseThrow();
        assertEquals(1,persisted.getItems().size()); assertEquals("Corrected paper",persisted.getItems().getFirst().getProductName());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents/"+documentId+"/business-state")
                .header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.reconciliation").value("WARNING"));
        var after=mockMvc.perform(post(reconciliation).header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(after.getResponse().getContentAsString()).findValuesAsText("status").stream().allMatch("MATCHED"::equals));
        assertEquals(2L,entityManager.createQuery("select count(b) from ReconciliationBatch b where b.company.id=:company",Long.class)
                .setParameter("company",company.getId()).getSingleResult());
        mockMvc.perform(put("/api/v1/invoices/"+invoiceId+"/items").header("Authorization",auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        entityManager.flush(); entityManager.clear();
        assertTrue(invoiceRepository.findById(invoiceId).orElseThrow().getItems().isEmpty());
    }

    @Test
    @Transactional
    void itemOnlyCorrectionRejectsInvalidRowsAndKeepsApprovalAndScopeGuards() throws Exception {
        Company company=provisionTestCompany();
        Document document=completionDocument(company,"item-guard.pdf",DocumentStatus.PROCESSED);
        Invoice invoice=new Invoice(); invoice.setDocument(document); invoiceRepository.saveAndFlush(invoice);
        String endpoint="/api/v1/invoices/"+invoice.getId()+"/items", auth="Bearer "+loginToken("admin","Admin@123456");
        for(String body:java.util.List.of("{}","{\"items\":null}","{\"items\":[null]}",
                "{\"items\":[{\"productName\":\"Paper\",\"quantity\":-1}]}","{\"items\":[{\"productName\":\"\"}]}")) {
            mockMvc.perform(put(endpoint).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put(endpoint).header("Authorization","Bearer "+loginToken("employee","Employee@123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isForbidden());
        mockMvc.perform(put(endpoint).header("Authorization","Bearer "+loginToken("accountant","Accountant@123"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isNotFound());
        document.setReviewStatus(com.example.invoice.entity.ReviewStatus.APPROVED); document.setStatus(DocumentStatus.COMPLETED);
        documentRepository.flush();
        mockMvc.perform(put(endpoint).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void documentExcelUsesListFiltersAcrossPagesIncludingDocumentsWithoutInvoice() throws Exception {
        Company company=provisionTestCompany(); String prefix="export-match-"+UUID.randomUUID();
        var type=new com.example.invoice.entity.DocumentType(); type.setCode("EXPORT_"+UUID.randomUUID());
        type.setName("Export fixture"); type.setActive(true); entityManager.persist(type);
        var expected=new java.util.ArrayList<Long>();
        for(int index=0;index<12;index++) {
            Document d=completionDocument(company,prefix+"-"+String.format("%02d",index)+".pdf",DocumentStatus.PROCESSED);
            d.setType(type); documentRepository.flush(); expected.add(d.getId());
            if(index==0) {
                Invoice i=new Invoice(); i.setDocument(d); i.setInvoiceDate(java.time.LocalDate.of(2000,1,1));
                i.setInvoiceNumber("NOT-THE-FILENAME"); invoiceRepository.saveAndFlush(i);
            }
            entityManager.createQuery("update Document d set d.createdAt=:at where d.id=:id")
                    .setParameter("at",java.time.LocalDateTime.of(2026,2,1,12,0)).setParameter("id",d.getId()).executeUpdate();
        }
        // Each excluded document violates exactly one filter, not several at once.
        for (String reason : java.util.List.of("status","review","type","date","search")) {
            Document excluded=completionDocument(company,"search".equals(reason)?"different-name.pdf":prefix+"-wrong-"+reason+".pdf",
                    "status".equals(reason)?DocumentStatus.FAILED:DocumentStatus.PROCESSED);
            if (!"type".equals(reason)) excluded.setType(type);
            if ("review".equals(reason)) excluded.setReviewStatus(com.example.invoice.entity.ReviewStatus.REJECTED);
            documentRepository.flush();
            entityManager.createQuery("update Document d set d.createdAt=:at where d.id=:id")
                    .setParameter("at",java.time.LocalDateTime.of(2026,2,"date".equals(reason)?2:1,12,0))
                    .setParameter("id",excluded.getId()).executeUpdate();
        }
        entityManager.flush(); entityManager.clear();
        String auth="Bearer "+loginToken("admin","Admin@123456");
        var list=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/documents")
                .header("Authorization",auth).param("search",prefix).param("dateFrom","2026-02-01").param("dateTo","2026-02-01")
                .param("processingStatus","PROCESSED").param("reviewStatus","PENDING").param("typeId",type.getId().toString())
                .param("sort","originalFileName,desc").param("size","1")).andExpect(status().isOk()).andReturn();
        assertEquals(12,objectMapper.readTree(list.getResponse().getContentAsString()).path("totalElements").asInt());
        var exported=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exports/documents")
                .header("Authorization",auth).param("search",prefix).param("dateFrom","2026-02-01").param("dateTo","2026-02-01")
                .param("processingStatus","PROCESSED").param("reviewStatus","PENDING").param("typeId",type.getId().toString())
                .param("sort","originalFileName,desc")).andExpect(status().isOk()).andReturn();
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(exported.getResponse().getContentAsByteArray()))) {
            var sheet=workbook.getSheetAt(0); assertEquals(12,sheet.getLastRowNum());
            var ids=new java.util.HashSet<Long>();
            for(int index=1;index<=12;index++) ids.add(Long.valueOf(sheet.getRow(index).getCell(0).getStringCellValue()));
            assertEquals(new java.util.HashSet<>(expected),ids);
            assertEquals(objectMapper.readTree(list.getResponse().getContentAsString()).path("content").get(0).path("id").asText(),sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("NOT-THE-FILENAME",sheet.getRow(12).getCell(6).getStringCellValue());
            assertNull(sheet.getRow(1).getCell(14)); // Unknown amount remains blank, not zero.
        }
    }

    private Document completionDocument(Company company,String name,DocumentStatus status) {
        Document d=new Document(); d.setCompany(company); d.setOriginalFileName(name);
        d.setFileType("application/pdf"); d.setFileSize(1L); d.setStatus(status);
        return documentRepository.saveAndFlush(d);
    }

    @Test
    @Transactional
    void documentExcelKeepsAccountantScopeAndExportPermission() throws Exception {
        Company company=provisionTestCompany();
        User accountant=userRepository.findByUsername("accountant").orElseThrow();
        accountant.setCompany(company); userRepository.flush();
        String prefix="scope-export-"+UUID.randomUUID();
        Document own=completionDocument(company,prefix+"-own.pdf",DocumentStatus.PROCESSED);
        Company foreign=new Company(); foreign.setCompanyName("Other isolated fixture"); companyRepository.saveAndFlush(foreign);
        completionDocument(foreign,prefix+"-foreign.pdf",DocumentStatus.PROCESSED);
        var result=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exports/documents")
                .header("Authorization","Bearer "+loginToken("accountant","Accountant@123")).param("search",prefix))
                .andExpect(status().isOk()).andReturn();
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertEquals(1,workbook.getSheetAt(0).getLastRowNum());
            assertEquals(own.getId().toString(),workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exports/documents")
                .header("Authorization","Bearer "+loginToken("employee","Employee@123"))).andExpect(status().isForbidden());
    }

	@Test
	void contextLoads() {
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"employee", "accountant"})
	@Transactional
	void auditNonAdminsCannotDirectlyUpdateUsersOrRoles(String caller) throws Exception {
		String token = loginToken(caller, "employee".equals(caller) ? "Employee@123" : "Accountant@123");
		User target = userRepository.findByUsername("admin").orElseThrow();
		var before = new com.example.invoice.service.UserMapper().toResponse(target);
		mockMvc.perform(put("/api/v1/users/" + target.getId()).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"role\":\"ADMIN\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/users/" + target.getId() + "/roles").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[]}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/roles/1/permissions").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"permissionIds\":[]}"))
				.andExpect(status().isForbidden());
		assertEquals(before, new com.example.invoice.service.UserMapper().toResponse(target));
	}

	@Test
	@Transactional
	void adminCannotMoveCompanyOrPartiallyUpdateRoleAndStatus() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User employee = userRepository.findByUsername("employee").orElseThrow();
		Company company = provisionTestCompany();
		mockMvc.perform(put("/api/v1/users/" + employee.getId()).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"role\":\"USER\",\"companyId\":" + company.getId() + "}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("USER_COMPANY_CHANGE_NOT_ALLOWED"));
		assertEquals(com.example.invoice.entity.UserStatus.ACTIVE, employee.getStatus());
		assertNull(employee.getCompany());
		assertEquals(0L, employee.getTokenVersion());
		assertEquals("EMPLOYEE", employee.getUserRoles().iterator().next().getRole().getCode());
	}

	@Test
	@Transactional
	void roleIdAssignmentRejectsInactiveRoleWithoutChangingExistingRoles() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User employee = userRepository.findByUsername("employee").orElseThrow();
		var inactive = new com.example.invoice.entity.Role();
		inactive.setCode("AUDIT_INACTIVE"); inactive.setName("Audit inactive"); inactive.setActive(false);
		entityManager.persist(inactive); entityManager.flush();
		mockMvc.perform(put("/api/v1/users/" + employee.getId() + "/roles").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[" + inactive.getId() + "]}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("USER_ROLE_INACTIVE"));
		assertEquals("EMPLOYEE", employee.getUserRoles().iterator().next().getRole().getCode());
	}

	@Test
	@Transactional
	void legacyCompanyEchoAndNullPreserveUserAndDocumentOwnership() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		Company owner = provisionTestCompany();
		User employee = userRepository.findByUsername("employee").orElseThrow(); employee.setCompany(owner);
		Document document = new Document(); document.setCompany(owner); document.setUploadedBy(employee);
		document.setOriginalFileName("ownership-test.pdf"); document.setFileType("application/pdf"); document.setFileSize(1L);
		documentRepository.saveAndFlush(document); userRepository.flush();
		Long documentId = document.getId();
		for (String companyField : java.util.List.of("", ",\"companyId\":null", ",\"companyId\":" + owner.getId())) {
			mockMvc.perform(put("/api/v1/users/" + employee.getId()).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"" + companyField + "}"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.companyId").value(owner.getId().intValue()));
		}
		userRepository.flush(); entityManager.clear();
		assertEquals(owner.getId(), userRepository.findByUsername("employee").orElseThrow().getCompany().getId());
		assertEquals(owner.getId(), documentRepository.findById(documentId).orElseThrow().getCompany().getId());
		assertEquals(0L, userRepository.findByUsername("employee").orElseThrow().getTokenVersion());
	}

	@Test
	@Transactional
	void historicalRoleListIsFrozenAndIdenticalEchoIsANoOp() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User employee = userRepository.findByUsername("employee").orElseThrow();
		var legacy = entityManager.createQuery("select r from Role r where r.code='USER'", com.example.invoice.entity.Role.class).getSingleResult();
		var assignment = new com.example.invoice.entity.UserRoleAssignment(); assignment.setUser(employee); assignment.setRole(legacy);
		employee.getUserRoles().add(assignment); userRepository.flush();
		var ids = employee.getUserRoles().stream().map(a -> a.getRole().getId()).collect(java.util.stream.Collectors.toSet());
		var row = employee.getRowVersion();
		String endpoint = "/api/v1/users/" + employee.getId() + "/roles";
		mockMvc.perform(put(endpoint).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("roleIds", ids))))
				.andExpect(status().isOk());
		mockMvc.perform(put(endpoint).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("roleIds", java.util.Set.of(legacy.getId())))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("HISTORICAL_USER_ROLES_PROTECTED"));
		mockMvc.perform(put("/api/v1/users/" + employee.getId()).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"role\":\"EMPLOYEE\"}"))
				.andExpect(status().isBadRequest());
		userRepository.flush(); entityManager.clear();
		User after = userRepository.findByUsername("employee").orElseThrow();
		assertEquals(ids, after.getUserRoles().stream().map(a -> a.getRole().getId()).collect(java.util.stream.Collectors.toSet()));
		assertEquals(row, after.getRowVersion()); assertEquals(0L, after.getTokenVersion());
		assertEquals(com.example.invoice.entity.UserStatus.ACTIVE, after.getStatus());
	}

	@Test
	@Transactional
	void roleListRejectsUserEmptyAndMissingIdsWhileDuplicateIdsAreSetSemantics() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User employee = userRepository.findByUsername("employee").orElseThrow();
		var legacy = entityManager.createQuery("select r from Role r where r.code='USER'", com.example.invoice.entity.Role.class).getSingleResult();
		String endpoint = "/api/v1/users/" + employee.getId() + "/roles";
		for (String payload : java.util.List.of("{\"roleIds\":[]}", "{\"roleIds\":[99999999]}", "{\"roleIds\":[" + legacy.getId() + "]}")) {
			mockMvc.perform(put(endpoint).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(payload))
					.andExpect(status().isBadRequest());
		}
		Long originalId = employee.getUserRoles().iterator().next().getRole().getId();
		mockMvc.perform(put(endpoint).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roleIds\":[" + originalId + "," + originalId + "]}"))
				.andExpect(status().isOk());
		assertEquals(1, employee.getUserRoles().size()); assertEquals(0L, employee.getTokenVersion());
	}

	@Test
	@Transactional
	void selfLockAndSystemRoleChangesAreRejectedAndHistoricalPermissionsPreserved() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User admin = userRepository.findByUsername("admin").orElseThrow();
		mockMvc.perform(put("/api/v1/users/" + admin.getId()).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("ADMIN_SELF_CHANGE_FORBIDDEN"));
		var legacy = entityManager.createQuery("select r from Role r where r.code='USER'", com.example.invoice.entity.Role.class).getSingleResult();
		var before = legacy.getRolePermissions().stream().map(a -> a.getPermission().getId()).collect(java.util.stream.Collectors.toSet());
		mockMvc.perform(put("/api/v1/roles/" + legacy.getId() + "/permissions").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"permissionIds\":[]}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("HISTORICAL_USER_PERMISSIONS_PROTECTED"));
		mockMvc.perform(put("/api/v1/roles/" + legacy.getId()).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"CHANGED\",\"name\":\"changed\",\"active\":true}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/roles/" + legacy.getId())
				.header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
		userRepository.flush(); entityManager.clear();
		var after = entityManager.find(com.example.invoice.entity.Role.class, legacy.getId());
		assertEquals(before, after.getRolePermissions().stream().map(a -> a.getPermission().getId()).collect(java.util.stream.Collectors.toSet()));
		assertEquals("USER", after.getCode()); assertTrue(after.isActive());
		assertEquals(com.example.invoice.entity.UserStatus.ACTIVE, userRepository.findByUsername("admin").orElseThrow().getStatus());
	}

	@Test
	@Transactional
	void adminCannotDemoteSelf() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User admin = userRepository.findByUsername("admin").orElseThrow();
		mockMvc.perform(put("/api/v1/users/" + admin.getId()).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"role\":\"EMPLOYEE\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("ADMIN_SELF_CHANGE_FORBIDDEN"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users")
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
	}

	@Test
	@Transactional
	void lockedAdminTokenCannotAccessAdminEndpoint() throws Exception {
		String token = loginToken("admin", "Admin@123456");
		User admin = userRepository.findByUsername("admin").orElseThrow();
		User second = new User(); second.setUsername("second-admin-test"); second.setFullName("Second admin"); second.setEmail("second-admin@example.test");
		second.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("SecondAdmin123!"));
		var role = admin.getUserRoles().iterator().next().getRole();
		var assignment = new com.example.invoice.entity.UserRoleAssignment(); assignment.setUser(second); assignment.setRole(role); second.getUserRoles().add(assignment);
		userRepository.saveAndFlush(second);
		String secondToken = loginToken("second-admin-test", "SecondAdmin123!");
		mockMvc.perform(put("/api/v1/users/" + admin.getId()).header("Authorization", "Bearer " + secondToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"Admin@123456\"}"))
				.andExpect(status().isLocked());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users")
				.header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"employee", "accountant"})
	@Transactional
	void lockedUserTokenIsRejectedOnNextRequest(String caller) throws Exception {
		String password = "employee".equals(caller) ? "Employee@123" : "Accountant@123";
		String token = loginToken(caller, password);
		String adminToken = loginToken("admin", "Admin@123456");
		User user = userRepository.findByUsername(caller).orElseThrow();
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/users/" + user.getId()).header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk());
		userRepository.flush(); entityManager.clear();
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
				.header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("username", caller, "password", password))))
				.andExpect(status().isLocked());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"anonymous", "employee", "accountant", "admin"})
	@Transactional
	void publicRegistrationIsDisabledForEveryCallerWithoutChangingExistingAccounts(String caller) throws Exception {
		String password = switch (caller) {
			case "admin" -> "Admin@123456";
			case "accountant" -> "Accountant@123";
			default -> "Employee@123";
		};
		String token = "anonymous".equals(caller) ? null : loginToken(caller, password);
		var mapper = new com.example.invoice.service.UserMapper();
		var before = userRepository.findAll().stream().map(mapper::toResponse).toList();
		long assignments = entityManager.createQuery("select count(a) from UserRoleAssignment a", Long.class).getSingleResult();
		long companies = companyRepository.count();
		var request = post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("username", "g22-" + UUID.randomUUID(),
						"password", "TestPassword123!", "fullName", "Registration test", "email", "g22@example.test")));
		if (token != null) request.header("Authorization", "Bearer " + token);
		mockMvc.perform(request).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("PUBLIC_REGISTRATION_DISABLED"))
				.andExpect(jsonPath("$.token").doesNotExist());
		entityManager.flush();
		entityManager.clear();
		assertEquals(before, userRepository.findAll().stream().map(mapper::toResponse).toList());
		assertEquals(assignments, entityManager.createQuery("select count(a) from UserRoleAssignment a", Long.class).getSingleResult());
		assertEquals(companies, companyRepository.count());
		if (token != null) {
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
					.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value(caller));
			var claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
			assertEquals(caller, claims.path("sub").asText());
			assertEquals(4, claims.size());
			assertEquals(0, claims.path("tv").asLong());
			assertTrue(claims.has("iat") && claims.has("exp"));
			assertFalse(loginToken(caller, password).isBlank());
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"", "{}", "{invalid-json"})
	@Transactional
	void disabledRegistrationDoesNotRequireAValidPayload(String body) throws Exception {
		long before = userRepository.count();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("PUBLIC_REGISTRATION_DISABLED"));
		assertEquals(before, userRepository.count());
	}

	@Test
	@Transactional
	void adminCanCreateAndLockUserAndLockedLoginExplainsStatus() throws Exception {
		Company internalCompany = provisionTestCompany();
		String uniqueId = UUID.randomUUID().toString();
		String username = "entity-test-" + uniqueId;
		String email = "entity-test-" + uniqueId + "@example.test";
		String password = "Password123!";

		MvcResult adminLoginResult = mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin\",\"password\":\"Admin@123456\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String adminToken = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString())
				.path("token").asText();
		String createPayload = "{\"username\":\"" + username + "\",\"password\":\"" + password
				+ "\",\"fullName\":\"Entity Mapping Test\",\"email\":\"" + email
				+ "\",\"role\":\"EMPLOYEE\"}";

		MvcResult createResult = mockMvc.perform(post("/api/v1/users")
					.header("Authorization", "Bearer " + adminToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content(createPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("EMPLOYEE"))
				.andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
				.andReturn();
		long userId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asLong();

		userRepository.flush();
		entityManager.clear();

		User persistedUser = userRepository.findByUsername(username).orElseThrow();
		assertEquals(userId, persistedUser.getId());
		assertEquals(internalCompany.getId(), persistedUser.getCompany().getId());
		assertEquals(1, persistedUser.getUserRoles().size());
		assertEquals("EMPLOYEE", persistedUser.getUserRoles().iterator().next().getRole().getCode());

		mockMvc.perform(put("/api/v1/users/" + userId)
					.header("Authorization", "Bearer " + adminToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"))
				.andExpect(jsonPath("$.message", containsString("Tài khoản đã bị khóa")));
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "ACCOUNTANT", "EMPLOYEE"})
	@Transactional
	void adminCreatesOnlyNewUserInConfiguredCompanyIgnoringRequestedCompany(String role) throws Exception {
		Company company = provisionTestCompany();
		User existing = userRepository.findByUsername("employee").orElseThrow();
		Long oldCompany = existing.getCompany() == null ? null : existing.getCompany().getId();
		var oldRoles = existing.getUserRoles().stream().map(a -> a.getRole().getCode()).sorted().toList();
		String name = "g21-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + loginToken("admin", "Admin@123456"))
				.contentType(MediaType.APPLICATION_JSON).content(createUserPayload(name, role)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.companyId").value(company.getId().intValue()))
				.andExpect(jsonPath("$.role").value(role));
		userRepository.flush();
		entityManager.clear();
		assertEquals(company.getId(), userRepository.findByUsername(name).orElseThrow().getCompany().getId());
		User unchanged = userRepository.findByUsername("employee").orElseThrow();
		assertEquals(oldCompany, unchanged.getCompany() == null ? null : unchanged.getCompany().getId());
		assertEquals(oldRoles, unchanged.getUserRoles().stream().map(a -> a.getRole().getCode()).sorted().toList());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource(value = {"EMPTY:INTERNAL_COMPANY_NOT_CONFIGURED", "invalid:INTERNAL_COMPANY_INVALID_CONFIG", "99999999:INTERNAL_COMPANY_NOT_FOUND"}, delimiter = ':')
	@Transactional
	void companyConfigurationFailuresReturnCodeWithoutSavingUser(String configuredId, String code) throws Exception {
		internalCompanyProperties.setId("EMPTY".equals(configuredId) ? "" : configuredId);
		long before = userRepository.count();
		mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + loginToken("admin", "Admin@123456"))
				.contentType(MediaType.APPLICATION_JSON).content(createUserPayload("g21-" + UUID.randomUUID(), "EMPLOYEE")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(code));
		assertEquals(before, userRepository.count());
	}

	@Test
	@Transactional
	void adminCannotCreateLegacyUserRole() throws Exception {
		provisionTestCompany();
		long before = userRepository.count();
		mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + loginToken("admin", "Admin@123456"))
				.contentType(MediaType.APPLICATION_JSON).content(createUserPayload("g21-" + UUID.randomUUID(), "USER")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("USER_ROLE_NOT_ALLOWED"));
		assertEquals(before, userRepository.count());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({"employee,Employee@123", "accountant,Accountant@123"})
	@Transactional
	void nonAdminCannotCreateUsers(String username, String password) throws Exception {
		provisionTestCompany();
		long before = userRepository.count();
		mockMvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + loginToken(username, password))
				.contentType(MediaType.APPLICATION_JSON).content(createUserPayload("g21-" + UUID.randomUUID(), "EMPLOYEE")))
				.andExpect(status().isForbidden());
		assertEquals(before, userRepository.count());
	}

	private Company provisionTestCompany() {
		Company company = new Company();
		company.setCompanyName("G2.1 isolated test company");
		company = companyRepository.saveAndFlush(company);
		internalCompanyProperties.setId(company.getId().toString());
		return company;
	}

	private String loginToken(String username, String password) throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password))))
				.andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
	}

	private String createUserPayload(String name, String role) throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of("username", name, "password", "TestPassword123!",
				"fullName", "G2.1 test", "email", name + "@example.test", "role", role, "companyId", 99999998));
	}

	@Test
	@Transactional
	void accountantCannotAccessDocumentsAcrossCompanies() {
		String uniqueId = UUID.randomUUID().toString();
		Company company = new Company();
		company.setCompanyName("Dashboard Test Company " + uniqueId);
		company.setTaxCode("DASH-" + uniqueId);
		companyRepository.saveAndFlush(company);
		User accountant = userRepository.findByUsername("accountant").orElseThrow();
		accountant.setCompany(company);
		userRepository.saveAndFlush(accountant);
		Company otherCompany = new Company();
		otherCompany.setCompanyName("Other Test Company " + uniqueId);
		otherCompany.setTaxCode("OTHER-" + uniqueId);
		companyRepository.saveAndFlush(otherCompany);

		Document document = new Document();
		document.setCompany(otherCompany);
		document.setOriginalFileName("dashboard-test-" + uniqueId + ".pdf");
		document.setFileType("application/pdf");
		document.setFileSize(1L);
		documentRepository.saveAndFlush(document);
		Invoice invoice = new Invoice();
		invoice.setDocument(document);
		invoiceRepository.saveAndFlush(invoice);

		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("accountant", "test"));
		try {
			DashboardStatisticsResponse statistics = dashboardService.statistics();
			assertFalse(documentService.findAll().stream().anyMatch(item -> item.id().equals(document.getId())));
			assertThrows(com.example.invoice.exception.ResourceNotFoundException.class,
					() -> documentService.findById(document.getId()));
			assertFalse(invoiceService.findAll().stream().anyMatch(item -> item.documentId().equals(document.getId())));
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	@Transactional
	void extractedFieldCorrectionPersistsAndReturnsEffectiveValueAfterReload() {
		String uniqueId = UUID.randomUUID().toString();
		Document document = new Document();
		document.setOriginalFileName("correction-" + uniqueId + ".pdf");
		document.setFileType("application/pdf");
		document.setFileSize(1L);
		document.setStatus(DocumentStatus.PROCESSED);
		documentRepository.saveAndFlush(document);

		ExtractedField field = new ExtractedField();
		field.setDocument(document);
		field.setFieldName("deliveryAddress");
		field.setFieldValue("Duong Nguyen Van Linh");
		field.setSource("AI");
		field.setConfidence(new BigDecimal("0.93"));
		extractedFieldRepository.saveAndFlush(field);

		User admin = userRepository.findByUsername("admin").orElseThrow();
		FieldCorrection correction = new FieldCorrection();
		correction.setField(field);
		correction.setCorrectedBy(admin);
		correction.setOldValue("Duong Nguyen Van Linh");
		correction.setNewValue("Đường Nguyễn Văn Linh");
		correction.setReason("Manual correction");
		fieldCorrectionRepository.saveAndFlush(correction);
		entityManager.clear();

		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("admin", "test"));
		try {
			var response = invoiceService.findExtractedFieldsByDocumentId(document.getId()).getFirst();
			assertEquals("Đường Nguyễn Văn Linh", response.fieldValue());
			assertEquals("Duong Nguyen Van Linh", response.originalAiValue());
			assertEquals("Đường Nguyễn Văn Linh", response.correctedValue());
			assertEquals(admin.getId(), response.correctedById());
			assertTrue(response.correctedAt() != null);
			assertEquals(new BigDecimal("0.93"), response.confidence());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
