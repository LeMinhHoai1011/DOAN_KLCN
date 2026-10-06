package com.example.invoice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.invoice.dto.invoice.InvoiceItemResponse;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.service.InvoiceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class InvoiceApiContractTest {
	@Test
	void detailApiReturnsCompleteVatInvoiceContract() throws Exception {
		InvoiceService service = org.mockito.Mockito.mock(InvoiceService.class);
		InvoiceItemResponse item = new InvoiceItemResponse(3L, "Dịch vụ kế toán", new BigDecimal("2"), "tháng",
				new BigDecimal("5000000"), new BigDecimal("0.08"), new BigDecimal("800000"), new BigDecimal("10000000"));
		InvoiceResponse response = new InvoiceResponse(1L, 9L, "000018", "1C26TAA", LocalDate.of(2026, 10, 4),
				"Công ty Bán", "0312345678", "TP.HCM", "028 1234 5678", "Công ty Mua", "0398765432", "Hà Nội",
				new BigDecimal("10000000"), new BigDecimal("800000"), new BigDecimal("10800000"), "TM/CK",
				"Mười triệu tám trăm nghìn đồng", "CQT-2026-XYZ", LocalDate.of(2026, 10, 4), List.of(item));
		when(service.findById(1L)).thenReturn(response);
		MockMvc mvc = mvc(service);

		mvc.perform(get("/api/v1/invoices/1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.invoiceSeries").value("1C26TAA"))
				.andExpect(jsonPath("$.sellerPhone").value("028 1234 5678"))
				.andExpect(jsonPath("$.paymentMethod").value("TM/CK"))
				.andExpect(jsonPath("$.amountInWords").value("Mười triệu tám trăm nghìn đồng"))
				.andExpect(jsonPath("$.taxAuthorityCode").value("CQT-2026-XYZ"))
				.andExpect(jsonPath("$.signDate").value("2026-10-04"))
				.andExpect(jsonPath("$.items[0].unit").value("tháng"))
				.andExpect(jsonPath("$.items[0].taxRate").value(0.08))
				.andExpect(jsonPath("$.items[0].taxAmount").value(800000));
	}

	@Test
	void legacyInvoiceWithNullNewFieldsStillSerializes() throws Exception {
		InvoiceService service = org.mockito.Mockito.mock(InvoiceService.class);
		when(service.findById(2L)).thenReturn(new InvoiceResponse(2L, 10L, "OLD-1", null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, List.of()));
		MockMvc mvc = mvc(service);
		mvc.perform(get("/api/v1/invoices/2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.invoiceNumber").value("OLD-1"))
				.andExpect(jsonPath("$.sellerPhone").doesNotExist());
	}

	private MockMvc mvc(InvoiceService service) {
		return MockMvcBuilders.standaloneSetup(new InvoiceController(service))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder().findAndAddModules()
						.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
				.build();
	}
}
