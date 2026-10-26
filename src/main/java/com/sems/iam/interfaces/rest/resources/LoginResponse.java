package com.sems.iam.interfaces.rest.resources;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta de autenticacion.
 *
 * <p>Lleva dos tokens: el de acceso, de vida corta, que viaja en cada peticion,
 * y el de refresco, de vida larga, que solo se usa para pedir uno nuevo. Separar
 * ambos limita el danio si el de acceso se filtra.
 */
public record LoginResponse(String token, String refreshToken, UUID userId,
                            String emailAddress, List<String> roles,
                            String fullName, String segment) {
}
