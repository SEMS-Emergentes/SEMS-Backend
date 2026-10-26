package com.sems.iam.interfaces.rest.resources;
import jakarta.validation.constraints.*;
/**
 * Credenciales de acceso.
 *
 * <p>El correo se recorta en el constructor. Pegarlo con un espacio al final es
 * de lo mas comun y antes devolvia "must be a well-formed email address", un
 * mensaje que manda a revisar el correo cuando el correo esta bien. El alta ya
 * normalizaba; el acceso no.
 */
public record LoginRequest(@NotBlank @Email String emailAddress, @NotBlank String password) {

    public LoginRequest {
        emailAddress = emailAddress == null ? null : emailAddress.trim();
    }
}
