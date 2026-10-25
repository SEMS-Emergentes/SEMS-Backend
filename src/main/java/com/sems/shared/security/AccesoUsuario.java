package com.sems.shared.security;

import com.sems.iam.infrastructure.authorization.sfs.model.AuthenticatedUserDetails;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Responde la pregunta mas basica de autorizacion: quien pide esto, y es suyo.
 *
 * <p>La autenticacion ya la resuelve el filtro JWT; lo que faltaba era el paso
 * siguiente. Hasta ahora {@code anyRequest().authenticated()} dejaba que
 * cualquier usuario con sesion valida leyera y modificara los recursos de
 * cualquier otro con solo cambiar el identificador de la ruta. Este bean es la
 * pieza que los controladores consultan por {@code @PreAuthorize} para cerrar
 * esa puerta.
 *
 * <p>Se expone con nombre explicito ("accesoUsuario") porque SpEL lo referencia
 * por nombre y no por tipo: si alguien renombra la clase, las anotaciones
 * seguirian compilando y dejarian de proteger en silencio.
 */
@Component("accesoUsuario")
public class AccesoUsuario {

    /**
     * La misma bandera que decide si se exige autenticacion.
     *
     * <p>Cuando esta en falso la configuracion de seguridad abre todas las rutas
     * y ni siquiera registra el filtro JWT, asi que no hay usuario autenticado
     * contra el que comparar. Sin esta bandera aqui, cada comprobacion fallaria
     * por falta de principal y el modo "sin autenticacion" se convertiria en
     * "nada funciona", que no es lo que nadie pide al apagarla.
     */
    @Value("${app.security.auth-required:true}")
    private boolean autorizacionActiva;

    /** El usuario autenticado, o vacio si la peticion es anonima. */
    public Optional<UUID> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUserDetails detalle)) {
            return Optional.empty();
        }
        return Optional.ofNullable(detalle.getUserId());
    }

    /** Un administrador de la plataforma pasa cualquier comprobacion de dueno. */
    public boolean esAdministrador() {
        if (!autorizacionActiva) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    public boolean esMio(UUID userId) {
        if (!autorizacionActiva) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (esAdministrador()) {
            return true;
        }
        return actual().map(userId::equals).orElse(false);
    }

    /**
     * Variante para rutas y cuerpos que traen el identificador como texto.
     *
     * <p>Un identificador mal formado no es un caso de autorizacion sino de
     * validacion, asi que se deja pasar: el controlador respondera 400 como
     * siempre. Lo que no se deja pasar es un UUID valido de otra persona.
     */
    public boolean esMio(String userId) {
        if (!autorizacionActiva) {
            return true;
        }
        UUID id = aUuid(userId);
        return id == null ? malFormado(userId) : esMio(id);
    }

    /** Texto vacio o nulo no identifica a nadie: se rechaza. */
    private boolean malFormado(String userId) {
        return userId != null && !userId.isBlank();
    }

    /** Devuelve null en lugar de lanzar, para no convertir un 400 en un 500. */
    public static UUID aUuid(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
