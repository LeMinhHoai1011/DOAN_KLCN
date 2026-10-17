package com.example.invoice.exception;

import com.example.invoice.dto.document.PipelineFailure;

public class PipelineException extends RuntimeException {
    private final PipelineFailure failure;
    private final int httpStatus;
    public PipelineException(PipelineFailure failure, int httpStatus) {
        this(failure, httpStatus, null);
    }
    public PipelineException(PipelineFailure failure, int httpStatus, Throwable cause) {
        super(failure.userMessage(), cause); this.failure = failure; this.httpStatus = httpStatus;
    }
    public PipelineFailure failure() { return failure; }
    public int httpStatus() { return httpStatus; }
}
