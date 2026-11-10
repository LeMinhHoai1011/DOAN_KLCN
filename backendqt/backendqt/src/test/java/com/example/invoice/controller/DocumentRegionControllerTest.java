package com.example.invoice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.invoice.dto.document.RegionDtos.RegionRect;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionResponse;
import com.example.invoice.dto.document.RegionDtos.RegionTextResponse;
import com.example.invoice.dto.document.RegionDtos.RegionWord;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.GlobalExceptionHandler;
import com.example.invoice.service.RegionSelectionService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DocumentRegionControllerTest {
	private final RegionSelectionService service = mock(RegionSelectionService.class);
	private final MockMvc mvc = mvc();

	@Test
	void returnsTextInsideRegion() throws Exception {
		when(service.extractText(eq(13L), eq(new RegionRect(1, 90.0, 190.0, 400.0, 30.0)), any())).thenReturn(
				new RegionTextResponse(1, 1000, 1400, "00A1B2C3", List.of("00A1B2C3"),
						List.of(new RegionWord("w4", "00A1B2C3", 0.9, 260, 200, 200, 20))));

		mvc.perform(post("/api/v1/documents/13/region-text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"page\":1,\"x\":90,\"y\":190,\"width\":400,\"height\":30}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.text").value("00A1B2C3"))
				.andExpect(jsonPath("$.words[0].id").value("w4"));
	}

	@Test
	void rejectsMissingOrNegativeCoordinatesBeforeCallingService() throws Exception {
		mvc.perform(post("/api/v1/documents/13/region-text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"page\":1,\"x\":-5,\"y\":190,\"width\":400,\"height\":30}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/documents/13/region-text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"page\":0,\"x\":5,\"y\":190,\"width\":400}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/documents/13/region-text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"page\":1,\"x\":5,\"y\":190,\"width\":0,\"height\":30}")).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void mapsServiceValidationAndRoleErrors() throws Exception {
		when(service.extractText(eq(13L), any(), any())).thenThrow(new BadRequestException("Vùng chọn vượt ra ngoài trang"));
		mvc.perform(post("/api/v1/documents/13/region-text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"page\":1,\"x\":900,\"y\":190,\"width\":400,\"height\":30}")).andExpect(status().isBadRequest());
		when(service.extractText(eq(14L), any(), any())).thenThrow(new AccessDeniedException("no"));
		mvc.perform(post("/api/v1/documents/14/region-text").contentType(MediaType.APPLICATION_JSON)
				.content("{\"page\":1,\"x\":9,\"y\":190,\"width\":40,\"height\":30}")).andExpect(status().isForbidden());
	}

	@Test
	void recordsInvoiceSelections() throws Exception {
		when(service.recordInvoiceSelections(eq(13L), any(), any())).thenReturn(List.of(
				new RegionSelectionResponse(5L, "signDate", 1, 100, 240, 180, 20, "05/03/2025", "2025-03-05", "MANUAL_REGION")));
		mvc.perform(post("/api/v1/documents/13/region-selections").contentType(MediaType.APPLICATION_JSON)
				.content("{\"selections\":[{\"fieldName\":\"signDate\",\"region\":{\"page\":1,\"x\":100,\"y\":240,\"width\":180,\"height\":20},"
						+ "\"extractedText\":\"05/03/2025\",\"finalValue\":\"2025-03-05\"}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].source").value("MANUAL_REGION"));
	}

	@Test
	void rejectsEmptyOrInvalidSelectionBatch() throws Exception {
		mvc.perform(post("/api/v1/documents/13/region-selections").contentType(MediaType.APPLICATION_JSON)
				.content("{\"selections\":[]}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/documents/13/region-selections").contentType(MediaType.APPLICATION_JSON)
				.content("{\"selections\":[{\"fieldName\":\"signDate\",\"region\":{\"page\":1,\"x\":1,\"y\":1,\"width\":10,\"height\":10},\"finalValue\":\"\"}]}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void endpointsUseFieldCorrectionRoles() throws Exception {
		for (String name : List.of("regionText", "recordSelections")) {
			Method method = java.util.Arrays.stream(DocumentRegionController.class.getMethods())
					.filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
			org.junit.jupiter.api.Assertions.assertEquals("hasRole('ADMIN') or hasRole('ACCOUNTANT')",
					method.getAnnotation(PreAuthorize.class).value());
		}
	}

	private MockMvc mvc() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		return MockMvcBuilders.standaloneSetup(new DocumentRegionController(service))
				.setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
	}
}
