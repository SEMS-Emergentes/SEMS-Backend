package com.sems.iam.interfaces.rest.exception;

import com.sems.iam.domain.model.exceptions.ConflictException;
import com.sems.iam.domain.model.exceptions.NotFoundException;
import com.sems.iam.domain.model.exceptions.UnauthorizedException;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
// El ultimo, porque abajo hay un catch-all de Exception. Los manejadores
// concretos de esta clase siguen atendiendo sus propios tipos; lo que no debe
// pasar es que el catch-all se adelante a los de otro advice.
@Order(Ordered.LOWEST_PRECEDENCE)
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
     * The path exists but not for this HTTP verb: that is a 405.
     *
     * <p>Sibling case to the one above, and it fell through the same hole. A GET
     * on a route that only answers PUT and DELETE reported 500 "Unexpected
     * error", which reads as a broken server when the real answer is "wrong
     * verb for this path".
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return response(HttpStatus.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for this endpoint");
    }

    /**
     * The body could not be read at all: that is a 400.
     *
     * <p>Third of the same family, and the one that was still missing. A field
     * sent with the wrong type, or a payload whose JSON is simply broken, never
     * reaches validation — Jackson gives up first — so it fell through to the
     * catch-all and came back as 500 "Unexpected error". Every integration
     * mistake looked like a server crash, which sends whoever is wiring up a
     * client to read our logs instead of their payload.
     *
     * <p>The reply names the field when Jackson knows it and stops there. The
     * parser's own message quotes the offending content and the Java types
     * behind it, and neither belongs in a response.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String campo = ex.getCause() instanceof InvalidFormatException formato
                ? formato.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(java.util.Objects::nonNull)
                        .reduce((a, b) -> b)
                        .orElse(null)
                : null;
        return response(HttpStatus.BAD_REQUEST, campo == null
                ? "the request body could not be read"
                : "field '" + campo + "' has the wrong type");
    }

    /**
     * The database refused the data: that is the request's problem, not ours.
     *
     * <p>A field longer than its column reached the driver and came back as a
     * 500 with "Unexpected error". It is the same shape as the three above: an
     * input the caller could fix, reported as if the server had broken.
     *
     * <p>This is a net, not a substitute for validating. Where a length or a
     * rule is known, the domain rejects it first with a message that says which
     * field and why; what lands here is what slipped past.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        return response(HttpStatus.BAD_REQUEST,
                "the request contains a value the database rejected; check field lengths and uniqueness");
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
