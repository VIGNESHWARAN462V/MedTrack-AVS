package com.medtrack.avs.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when AI provider integration operations fail or are unconfigured (Phase 20.1).
 * Never embed API keys, auth headers, or raw provider tokens in exception messages.
 */
@Getter
public class AiProviderException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public AiProviderException(String errorCode, String message) {
        this(errorCode, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public AiProviderException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public AiProviderException(String errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }
}
