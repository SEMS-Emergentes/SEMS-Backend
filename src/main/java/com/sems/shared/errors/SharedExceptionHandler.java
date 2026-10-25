package com.sems.shared.errors;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce excepciones a respuestas HTTP con el contrato de error compartido.
 *
 * <p>Cada categoria de error del dominio tiene un unico codigo de estado, igual
 * que en los servicios originales: validacion es 400, no encontrado es 404 y
 * una regla de negocio rota es 409.
 */
@Slf4j
// Primero este. Hay dos @RestControllerAdvice en la aplicacion y el otro
// termina con un catch-all de Exception. Spring elige el advice entero por
// orden y se queda con el primero que tenga algun metodo aplicable, asi que un
// catch-all sin orden declarado se queda con TODO lo que le llegue antes: los
// 404, 400, 409 y 401 de AppException salian como 500 "Unexpected error" segun
// como hubiera quedado el orden de beans ese arranque. Declararlo es lo que
// hace que el resultado no dependa del azar.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SharedExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        HttpStatus status = switch (ex.code()) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (status.is5xxServerError()) {
            log.error("Error interno: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.code().name(), ex.getMessage()));
    }

    /** Falla la validacion de un cuerpo anotado con {@code @Valid}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(AppException.Code.VALIDATION_ERROR.name(), detail));
    }

    /** Falla la validacion de un parametro suelto. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(AppException.Code.VALIDATION_ERROR.name(), ex.getMessage()));
    }

    /** Un identificador mal formado en la ruta no es un error del servidor. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(AppException.Code.VALIDATION_ERROR.name(), ex.getMessage()));
    }
}
