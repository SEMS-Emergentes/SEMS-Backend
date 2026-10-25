package com.sems.subscriptions.application;

import com.sems.shared.security.AccesoUsuario;
import com.sems.subscriptions.domain.repositories.SubscriptionRepositories.SubscriptionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprueba que la suscripcion es del usuario.
 *
 * <p>Los planes no se comprueban por dueno porque no tienen dueno: el catalogo
 * es el mismo para todos. Sigue exigiendo sesion, como el resto de la API.
 */
@Component("accesoSuscripciones")
@RequiredArgsConstructor
public class AccesoSuscripciones {

    private final SubscriptionRepository subscriptions;
    private final AccesoUsuario accesoUsuario;

    @Transactional(readOnly = true)
    public boolean suscripcion(UUID subscriptionId) {
        if (subscriptionId == null) {
            return true;
        }
        return subscriptions.findById(subscriptionId)
                .map(s -> accesoUsuario.esMio(s.getUserId()))
                .orElse(true);
    }
}
