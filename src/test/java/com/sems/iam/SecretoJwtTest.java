package com.sems.iam;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sems.iam.infrastructure.tokens.jwt.services.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El servicio no arranca con un secreto de los publicados en el repositorio.
 *
 * <p>Un JWT firmado con una clave que esta en GitHub lo puede fabricar
 * cualquiera, con el rol que quiera. Sin esta guarda, olvidar JWT_SECRET en un
 * despliegue no daba ningun sintoma.
 */
class SecretoJwtTest {

    @Test
    @DisplayName("se niega a firmar con el valor por defecto de application.properties")
    void rechazaElValorPorDefecto() {
        assertThatThrownBy(() -> new JwtService(
                "replace_with_minimum_32_chars_secret_key_for_hs256", 120))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("se niega a firmar con la plantilla de .env.example")
    void rechazaLaPlantilla() {
        assertThatThrownBy(() -> new JwtService(
                "cambia_esto_por_un_secreto_de_al_menos_32_caracteres", 120))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("acepta un secreto propio")
    void aceptaUnSecretoPropio() {
        assertThatCode(() -> new JwtService("un_secreto_privado_de_mas_de_32_caracteres_x", 120))
                .doesNotThrowAnyException();
    }
}
