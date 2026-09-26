package com.example.invoice.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
	ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request.getRequestURI());
	}

	@ExceptionHandler(BadCredentialsException.class)
	ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password", request.getRequestURI());
	}

	@ExceptionHandler(DisabledException.class)
	ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex, HttpServletRequest request) {
		return error(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.", request.getRequestURI());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", request.getRequestURI());
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ErrorResponse> handleDatabase(DataAccessException ex, HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "Database operation failed", request.getRequestURI());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected server error", request.getRequestURI());
	}

	private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, String path) {
		return ResponseEntity.status(status).body(new ErrorResponse(LocalDateTime.now(), status.value(), code, message, path));
	}
}
