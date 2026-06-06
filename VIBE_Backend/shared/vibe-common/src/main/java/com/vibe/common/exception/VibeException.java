package com.vibe.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class VibeException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public VibeException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static VibeException notFound(String resource) {
        return new VibeException(resource + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
    public static VibeException conflict(String message) {
        return new VibeException(message, HttpStatus.CONFLICT, "CONFLICT");
    }
    public static VibeException badRequest(String message) {
        return new VibeException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }
    public static VibeException unauthorized(String message) {
        return new VibeException(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
    public static VibeException forbidden(String message) {
        return new VibeException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
    public static VibeException internal(String message) {
        return new VibeException(message, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }
}
