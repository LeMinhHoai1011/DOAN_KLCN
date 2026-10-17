package com.example.invoice.controller;

import com.example.invoice.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class PipelineErrorResponseTest {
    @RestController static class Controller {
        @GetMapping("/ai-error") public void failure() { throw new AiProviderException("OLLAMA_TIMEOUT: password=secret-token invoice-private"); }
        @GetMapping("/pipeline-error") public void pipelineFailure() {
            throw new PipelineException(com.example.invoice.service.PipelineFailures.classify(new AiProviderException("OLLAMA_TIMEOUT: secret-token"), "AI_EXTRACTION", 2), 504);
        }
        @GetMapping("/api/v1/documents/1/db-error") public void databaseFailure() {
            throw new org.springframework.dao.InvalidDataAccessResourceUsageException("secret-token SQL and values");
        }
        @GetMapping("/api/v1/documents/1/file-error") public void oversizedFile() {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(10);
        }
    }
    @Test void publicResponseContainsStructuredErrorWithoutTechnicalPayload() throws Exception {
        MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/ai-error"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.errorCode").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.failedStage").value("AI_EXTRACTION"))
                .andExpect(jsonPath("$.retryable").value(true)).andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.userMessage").exists())
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("invoice-private"))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
    @Test void documentErrorsHaveMetadataAndKeepInternalDetailsPrivate() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/pipeline-error")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.attemptCount").value(2)).andExpect(jsonPath("$.error").value("AI_TIMEOUT"))
                .andExpect(content().string(not(containsString("secret-token"))));
        mvc.perform(get("/api/v1/documents/1/db-error")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_ERROR")).andExpect(jsonPath("$.failedStage").value("DATABASE"))
                .andExpect(content().string(not(containsString("secret-token"))));
        mvc.perform(get("/api/v1/documents/1/file-error")).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FILE")).andExpect(jsonPath("$.retryable").value(false));
    }
}
