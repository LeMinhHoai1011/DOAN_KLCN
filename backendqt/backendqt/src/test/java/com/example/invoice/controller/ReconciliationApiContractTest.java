package com.example.invoice.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.service.ReconciliationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReconciliationApiContractTest {
	@Test
	void insufficientDataReturnsNullCalculatedAndDifferenceAmounts() throws Exception {
		ReconciliationService service = org.mockito.Mockito.mock(ReconciliationService.class);
		when(service.run(org.mockito.ArgumentMatchers.eq(247L), isNull(Authentication.class))).thenReturn(List.of(
				new ReconciliationResponse(73L, "SUBTOTAL", new BigDecimal("14800000"), null, null,
						"INSUFFICIENT_DATA", "SUBTOTAL: INSUFFICIENT_DATA; cần items[].amount và subtotal")));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReconciliationController(service))
				.setMessageConverters(new MappingJackson2HttpMessageConverter()).build();

		mvc.perform(post("/api/v1/documents/247/reconciliations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("INSUFFICIENT_DATA"))
				.andExpect(jsonPath("$[0].expectedAmount").value(14800000))
				.andExpect(jsonPath("$[0].actualAmount").doesNotExist())
				.andExpect(jsonPath("$[0].differenceAmount").doesNotExist());
	}
}
