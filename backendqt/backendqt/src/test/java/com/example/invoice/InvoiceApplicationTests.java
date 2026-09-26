package com.example.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.invoice.dto.DashboardStatisticsResponse;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.User;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.service.DashboardService;
import com.example.invoice.service.DocumentService;
import com.example.invoice.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceApplicationTests {
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CompanyRepository companyRepository;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private InvoiceRepository invoiceRepository;

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
	void contextLoads() {
	}

	@Test
	@Transactional
	void adminCanCreateAndLockUserAndLockedLoginExplainsStatus() throws Exception {
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

	@Test
	@Transactional
	void accountantDashboardCountsDocumentsAcrossCompanies() {
		String uniqueId = UUID.randomUUID().toString();
		Company company = new Company();
		company.setCompanyName("Dashboard Test Company " + uniqueId);
		company.setTaxCode("DASH-" + uniqueId);
		companyRepository.saveAndFlush(company);

		Document document = new Document();
		document.setCompany(company);
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
			assertEquals(documentRepository.count(), statistics.totalDocuments());
			assertTrue(documentService.findAll().stream().anyMatch(item -> item.id().equals(document.getId())));
			assertEquals(document.getId(), documentService.findById(document.getId()).id());
			assertTrue(invoiceService.findAll().stream().anyMatch(item -> item.documentId().equals(document.getId())));
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
