package com.sems.iam.interfaces.rest.exception;

import com.sems.iam.domain.model.exceptions.ConflictException;
import com.sems.iam.domain.model.exceptions.NotFoundException;
import com.sems.iam.domain.model.exceptions.UnauthorizedException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler centralises error handling for the whole API. Thanks to
 * @RestControllerAdvice, whenever a controller throws an exception, the matching
 * method below catches it and turns it into a clean JSON response with the right
 * HTTP status code. This keeps the controllers free of try/catch blocks and
 * guarantees every error looks the same to the client.
 *
 * Each method is mapped to an exception type with @ExceptionHandler, and the
 * design translates our domain exceptions into standard HTTP statuses:
 *   NotFoundException     -> 404 Not Found
 *   ConflictException     -> 409 Conflict
 *   Unauthorized/IllegalArgument -> 401 Unauthorized
 *   AccessDeniedException -> 403 Forbidden
 *   validation errors     -> 400 Bad Request
 *   anything else         -> 500 Internal Server Error
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> handleNotFound(NotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> handleConflict(ConflictException ex) {
        return response(HttpStatus.CONFLICT, ex.getMessage());
    }

    // One handler can cover several exception types by listing them in the array.
    @ExceptionHandler({UnauthorizedException.class, IllegalArgumentException.class})
    public ResponseEntity<?> handleUnauthorized(RuntimeException ex) {
        return response(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleForbidden(AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Handles bean-validation failures (e.g. a missing @NotBlank field). We pull
     * out the FIRST field error and build a readable message like
     * "email must not be blank" so the client knows exactly what to fix.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("Validation error");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * A URL that matches no route is a 404, not a server failure.
     *
     * <p>Without this handler the catch-all below turned every mistyped path
     * into a 500 with "Unexpected error", which points whoever is integrating
     * at the wrong problem: it reads as "the server broke" when the real answer
     * is "that endpoint does not exist".
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "No endpoint " + ex.getHttpMethod() + " " + ex.getResourcePath());
    }

    /**
     * Catch-all for any unexpected exception. Note we return a generic
     * "Unexpected error" message on purpose: leaking internal details (stack
     * traces, SQL, etc.) to the client would be a security risk.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    /**
     * Shared helper that builds the consistent error body. Putting it in one
     * place means every error response has the same fields: a timestamp, the
     * numeric status, its reason phrase, and a human-readable message.
     */
    private ResponseEntity<?> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
