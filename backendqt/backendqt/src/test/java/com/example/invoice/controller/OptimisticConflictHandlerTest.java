package com.example.invoice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import com.example.invoice.exception.GlobalExceptionHandler;

class OptimisticConflictHandlerTest {
	@Test
	void optimisticConflictReturnsControlled409() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(new ConflictController()).setControllerAdvice(new GlobalExceptionHandler()).build();
		mvc.perform(put("/conflict")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("USER_UPDATE_CONFLICT"));
	}

	@RestController
	static class ConflictController {
		@PutMapping("/conflict")
		void update() { throw new org.springframework.orm.ObjectOptimisticLockingFailureException("User", 1L); }
	}
}
