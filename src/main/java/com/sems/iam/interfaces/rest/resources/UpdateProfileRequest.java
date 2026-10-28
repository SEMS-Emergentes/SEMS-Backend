package com.sems.iam.interfaces.rest.resources;

import jakarta.validation.constraints.Size;

/**
 * Datos de perfil que el propio usuario puede cambiar.
 *
 * <p>Reemplaza los dos campos: el formulario de ajustes los envia siempre
 * juntos, y un vacio significa borrarlo, no dejarlo como estaba.
 */
public record UpdateProfileRequest(@Size(max = 120) String fullName, String segment) {
}
