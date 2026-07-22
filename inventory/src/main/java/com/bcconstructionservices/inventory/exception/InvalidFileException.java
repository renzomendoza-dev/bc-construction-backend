package com.bcconstructionservices.inventory.exception;

/**
 * Thrown when an uploaded file fails validation before being stored:
 * empty file, disallowed content type, or exceeding the configured max size.
 * Mapped to HTTP 400 (see GlobalExceptionHandler / InvalidFileExceptionHandler).
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}