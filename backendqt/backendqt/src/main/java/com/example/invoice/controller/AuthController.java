package com.example.invoice.controller;

import com.example.invoice.dto.auth.LoginRequest;
import com.example.invoice.dto.auth.LoginResponse;
import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.exception.ErrorResponse;
import com.example.invoice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;

	@PostMapping("/register")
	public UserResponse register() {
		// Reject regardless of payload validity or caller role, before binding registration data.
		return authService.register(null);
	}

	@ExceptionHandler(AuthService.PublicRegistrationDisabledException.class)
	public ResponseEntity<ErrorResponse> registrationDisabled(
			AuthService.PublicRegistrationDisabledException exception, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
				LocalDateTime.now(), HttpStatus.FORBIDDEN.value(), "PUBLIC_REGISTRATION_DISABLED",
				exception.getMessage(), request.getRequestURI()));
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}
