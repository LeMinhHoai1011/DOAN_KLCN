package com.example.invoice.exception;

/** Raised when a configured AI provider cannot complete a request safely. */
public class AiProviderException extends RuntimeException {
	public AiProviderException(String message) {
		super(message);
	}

	public AiProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}
