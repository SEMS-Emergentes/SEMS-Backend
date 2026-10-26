package com.sems.iam.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de una cuenta.
 *
 * <p>El nombre y el segmento son opcionales y se guardan: el formulario de la
 * web los pedia desde el principio, pero antes se quedaban en el navegador y
 * desaparecian al entrar desde otro equipo.
 */
public record RegisterRequest(
        @NotBlank @Email String emailAddress,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String role,
        @Size(max = 120) String fullName,
        String segment) {
}
