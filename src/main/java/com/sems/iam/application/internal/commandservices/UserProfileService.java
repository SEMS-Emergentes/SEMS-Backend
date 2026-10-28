package com.sems.iam.application.internal.commandservices;

import com.sems.iam.domain.model.exceptions.NotFoundException;
import com.sems.iam.domain.model.valueobjects.UserSegment;
import com.sems.iam.infrastructure.persistence.jpa.repositories.UserJpaEntity;
import com.sems.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.sems.shared.errors.AppException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambios que el usuario hace sobre su propio perfil.
 *
 * <p>Esta aparte de la autenticacion a proposito: cambiar el nombre no toca
 * credenciales ni roles, y mezclarlo con el alta y el login haria pasar por
 * ese codigo una operacion que no necesita ninguna de sus comprobaciones.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository users;

    /** Guarda el nombre y el segmento del usuario indicado. */
    @Transactional
    public void updateProfile(UUID userId, String fullName, String segment) {
        UserJpaEntity usuario = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String nombre = fullName == null ? null : fullName.trim();
        usuario.setFullName(nombre == null || nombre.isEmpty() ? null : nombre);

        UserSegment segmento;
        try {
            segmento = UserSegment.from(segment);
        } catch (IllegalArgumentException invalido) {
            throw AppException.validation(invalido.getMessage());
        }
        usuario.setSegment(segmento == null ? null : segmento.name());

        usuario.setUpdatedAt(Instant.now());
        users.save(usuario);
    }
}
