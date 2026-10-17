package com.example.invoice.service;

import com.example.invoice.dto.document.PipelineFailure;
import com.example.invoice.exception.PipelineException;
import java.time.LocalDateTime;
import java.util.Locale;

/** Allowlisted public messages; raw exception messages never enter the API/history. */
public final class PipelineFailures {
    private PipelineFailures() {}
    public static String technicalDetails(Throwable exception) {
        Throwable root = exception; String state = "unknown";
        for (int depth = 0; root != null && depth < 12; depth++) {
            if (root instanceof java.sql.SQLException sql && sql.getSQLState() != null && sql.getSQLState().matches("[0-9A-Z]{5}")) state = sql.getSQLState();
            if (root.getCause() == null) break;
            root = root.getCause();
        }
        return "exception=" + exception.getClass().getName() + "; root=" + root.getClass().getName() + "; sqlState=" + state;
    }
    public static PipelineFailure classify(Throwable exception, String stage, int attempt) {
        if (exception instanceof PipelineException pipeline) {
            var prior = pipeline.failure();
            return new PipelineFailure(prior.errorCode(), prior.failedStage(), prior.retryable(), attempt, prior.timestamp(), prior.userMessage());
        }
        String text = String.valueOf(exception.getMessage()).toUpperCase(Locale.ROOT);
        String code; boolean retryable = false;
        if (exception instanceof org.springframework.dao.DataAccessException) {
            code = "DATABASE_ERROR"; retryable = exception instanceof org.springframework.dao.TransientDataAccessException;
        } else if (stage.equals("STORAGE") || text.contains("MINIO") || text.contains("STORAGE_FAILED")) {
            code = "STORAGE_FAILED"; retryable = temporaryStorage(exception);
        } else if (text.contains("TIMEOUT") || hasCause(exception, java.net.SocketTimeoutException.class)) {
            code = "AI_TIMEOUT"; retryable = stage.equals("AI_EXTRACTION");
        } else if (text.contains("OCR_LOW") || text.contains("LOW_QUALITY")) code = "OCR_LOW_QUALITY";
        else if (text.contains("OCR_FAILED") || text.contains("OCR_LANGUAGE") || stage.equals("OCR")) code = "OCR_FAILED";
        else if (text.contains("JSON") || text.contains("INVALID_RESPONSE") || text.contains("EMPTY_RESPONSE")
                || text.contains("TOKEN_LIMIT") || text.contains("MODEL_NOT_FOUND")) code = "AI_INVALID_RESPONSE";
        else if (hasCause(exception, java.net.ConnectException.class) || text.contains("CONNECTION_ERROR") || text.contains("UNAVAILABLE") || text.contains("HTTP 503") || text.contains("HTTP 429")) {
            code = "EXTRACTION_FAILED"; retryable = true;
        } else if (stage.equals("TEXT_EXTRACTION") && exception instanceof com.example.invoice.exception.BadRequestException) code = "INVALID_FILE";
        else if (stage.equals("VALIDATION")) code = "VALIDATION_FAILED";
        else if (stage.equals("UPLOAD")) code = "UPLOAD_FAILED";
        else if (stage.equals("FILE_VALIDATION") || exception instanceof IllegalArgumentException) code = "INVALID_FILE";
        else code = "EXTRACTION_FAILED";
        return create(code, code.equals("STORAGE_FAILED") ? "STORAGE" : stage, retryable, attempt);
    }
    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) if (type.isInstance(cause)) return true;
        return false;
    }
    private static boolean temporaryStorage(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof io.minio.errors.ServerException) return true;
            if (cause instanceof io.minio.errors.ErrorResponseException minio) {
                return java.util.Set.of("InternalError", "ServiceUnavailable", "SlowDown", "RequestTimeout").contains(minio.errorResponse().code());
            }
        }
        return false;
    }
    public static PipelineFailure create(String code, String stage, boolean retryable, int attempt) {
        String message = switch (code) {
            case "UPLOAD_FAILED" -> "Không thể tải lên tệp. Vui lòng kiểm tra kết nối và thử lại.";
            case "INVALID_FILE" -> "Tệp không hợp lệ hoặc không được hỗ trợ. Vui lòng kiểm tra PDF/JPG/PNG và kích thước tệp.";
            case "OCR_FAILED" -> "Không thể nhận dạng văn bản. Vui lòng kiểm tra tệp và cấu hình OCR.";
            case "OCR_LOW_QUALITY" -> "Chất lượng OCR chưa đạt; kết quả cần được kiểm tra.";
            case "AI_TIMEOUT" -> "AI xử lý quá thời gian cho phép. Có thể thử lại khi dịch vụ sẵn sàng.";
            case "AI_INVALID_RESPONSE" -> "AI trả kết quả không hợp lệ. Vui lòng kiểm tra dịch vụ hoặc cấu hình trước khi xử lý lại.";
            case "STORAGE_FAILED" -> "Không thể truy cập kho tệp. Vui lòng kiểm tra dịch vụ lưu trữ.";
            case "DATABASE_ERROR" -> "Không thể hoàn tất thao tác cơ sở dữ liệu.";
            case "VALIDATION_FAILED" -> "Dữ liệu chưa đáp ứng quy tắc kiểm tra; cần kiểm tra thủ công.";
            default -> "Không thể hoàn tất trích xuất dữ liệu. Vui lòng kiểm tra dịch vụ xử lý.";
        };
        return new PipelineFailure(code, stage, retryable, attempt, LocalDateTime.now(), message);
    }
}
