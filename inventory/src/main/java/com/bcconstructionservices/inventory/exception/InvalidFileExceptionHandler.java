package com.bcconstructionservices.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps InvalidFileException to HTTP 400.
 *
 * <p>NOTE: this is provided as a STANDALONE @RestControllerAdvice because the
 * project's existing GlobalExceptionHandler source wasn't available to edit
 * directly. If you already have a GlobalExceptionHandler, prefer folding the
 * handler method below into it (and delete this class) so all exception
 * mapping lives in one place - the requirement was to "add a handler for it
 * in GlobalExceptionHandler". Matching that handler's existing response
 * shape (a shared error DTO, field, etc.) is preferable to the generic
 * ProblemDetail body used here; adjust accordingly.
 *
 * <p>Two advices both handling different exception types will coexist fine,
 * but two advices handling the SAME exception type would be ambiguous - so
 * make sure InvalidFileException isn't also mapped elsewhere if you keep
 * this class.
 */
@RestControllerAdvice
public class InvalidFileExceptionHandler {

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalidFile(InvalidFileException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}