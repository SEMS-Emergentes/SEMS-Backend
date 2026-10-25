package com.sems.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sems.shared.security.AccesoUsuario;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba que apagar la autenticacion no deja la aplicacion inservible.
 *
 * <p>{@code app.security.auth-required=false} abre todas las rutas y no registra
 * el filtro JWT, de modo que no hay ningun usuario autenticado. Las
 * comprobaciones de propiedad anadidas con {@code @PreAuthorize} se apoyan en
 * ese usuario, asi que sin este cuidado el modo "sin autenticacion" habria
 * pasado a denegar todas las peticiones con 403: lo contrario de lo que se pide
 * al apagar la bandera.
 */
@SpringBootTest(properties = "app.security.auth-required=false")
@ActiveProfiles("test")
class AutorizacionDesactivadaTest {

    @Autowired
    private AccesoUsuario accesoUsuario;

    @Test
    @DisplayName("con la autorizacion apagada toda comprobacion de dueno pasa")
    void conLaBanderaApagadaTodoPasa() {
        assertThat(accesoUsuario.esAdministrador()).isTrue();
        assertThat(accesoUsuario.esMio(UUID.randomUUID())).isTrue();
        assertThat(accesoUsuario.esMio("cualquier-cosa")).isTrue();
        assertThat(accesoUsuario.esMio((UUID) null)).isTrue();
    }
}
