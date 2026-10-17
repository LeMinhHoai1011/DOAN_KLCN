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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedMethod(HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED,"METHOD_NOT_ALLOWED","Không hỗ trợ thao tác này trên tài nguyên chỉ đọc.",request.getRequestURI());
    }
    @ExceptionHandler(PipelineException.class)
    ResponseEntity<com.example.invoice.dto.document.PipelineFailure> handlePipeline(PipelineException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(ex.failure());
    }
	@ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
	ResponseEntity<ErrorResponse> handleOptimisticConflict(Exception ex, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "USER_UPDATE_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại và thử lại.", request.getRequestURI());
	}

	@ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class, jakarta.persistence.PessimisticLockException.class,
			jakarta.persistence.LockTimeoutException.class})
	ResponseEntity<ErrorResponse> handleLockConflict(Exception ex, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "AUTHENTICATION_CONFLICT", "Không thể xác thực do cập nhật đồng thời. Vui lòng thử lại.", request.getRequestURI());
	}
	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
	ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, explicitCode(ex.getMessage(), "BAD_REQUEST"), ex.getMessage(), request.getRequestURI());
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
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tên đăng nhập hoặc mật khẩu không đúng", request.getRequestURI());
	}

	@ExceptionHandler(DisabledException.class)
	ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex, HttpServletRequest request) {
		return error(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.", request.getRequestURI());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập", request.getRequestURI());
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<?> handleDatabase(DataAccessException ex, HttpServletRequest request) {
		if (request.getRequestURI().startsWith("/api/v1/documents")) {
			log.error("Document database failure technicalDetails exceptionClass={}", ex.getClass().getName());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					com.example.invoice.service.PipelineFailures.classify(ex, "DATABASE", 0));
		}
		log.error("Database operation failed method={} path={} rootCause={}", request.getMethod(),
				request.getRequestURI(), mostSpecificMessage(ex), ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "Thao tác với cơ sở dữ liệu thất bại", request.getRequestURI());
	}

	private String mostSpecificMessage(DataAccessException exception) {
		Throwable cause = exception.getMostSpecificCause();
		return cause == null || cause.getMessage() == null ? exception.getMessage() : cause.getMessage();
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<?> handleMissingMultipartPart(MissingServletRequestPartException ex, HttpServletRequest request) {
		if (request.getRequestURI().startsWith("/api/v1/documents")) return ResponseEntity.badRequest().body(
				com.example.invoice.service.PipelineFailures.create("INVALID_FILE", "FILE_VALIDATION", false, 0));
		return error(HttpStatus.BAD_REQUEST, "MISSING_MULTIPART_FILE", "Vui lòng chọn một tệp ảnh", request.getRequestURI());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
		if (request.getRequestURI().startsWith("/api/v1/documents")) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
				com.example.invoice.service.PipelineFailures.create("INVALID_FILE", "FILE_VALIDATION", false, 0));
		return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Tệp tải lên vượt quá giới hạn 10 MB của máy chủ", request.getRequestURI());
	}

	@ExceptionHandler(AiProviderException.class)
	ResponseEntity<com.example.invoice.dto.document.PipelineFailure> handleAiProvider(AiProviderException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(com.example.invoice.service.PipelineFailures.classify(ex, "AI_EXTRACTION", 0));
	}

	private String explicitCode(String message, String fallback) {
		if (message == null) return fallback;
		int separator = message.indexOf(':');
		String candidate = (separator < 0 ? message : message.substring(0, separator)).trim();
		return candidate.matches("[A-Z][A-Z0-9_]{2,63}") ? candidate : fallback;
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected server error method={} path={}", request.getMethod(), request.getRequestURI(), ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Máy chủ gặp lỗi không mong muốn", request.getRequestURI());
	}

	private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, String path) {
		return ResponseEntity.status(status).body(new ErrorResponse(LocalDateTime.now(), status.value(), code, message, path));
	}
}
