package com.example.invoice.dto.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceItemRequestValidationTest {
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private InvoiceItemRequest row(String quantity, String rate, String money) {
		return new InvoiceItemRequest("Chiết khấu", new BigDecimal(quantity), null, new BigDecimal(money),
				new BigDecimal(rate), new BigDecimal(money), new BigDecimal(money));
	}

	@Test void discountRowsMayCarryNegativeMoney() {
		assertTrue(validator.validate(row("1", "0.08", "-20000")).isEmpty());
	}

	@Test void quantityAndRateStayNonNegative() {
		assertEquals(1, validator.validate(row("-1", "0.08", "20000")).size());
		assertEquals(1, validator.validate(row("1", "-0.08", "20000")).size());
	}
}
