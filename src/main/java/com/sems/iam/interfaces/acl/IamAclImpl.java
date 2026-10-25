package com.sems.iam.interfaces.acl;

import com.sems.iam.infrastructure.persistence.jpa.repositories.UserJpaEntity;
import com.sems.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de la capa anticorrupcion de IAM. */
@Service
@RequiredArgsConstructor
public class IamAclImpl implements IamAcl {

    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> emailOf(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return users.findById(userId).map(UserJpaEntity::getEmailAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> idOf(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            return Optional.empty();
        }
        // El alta normaliza el correo a minusculas, asi que la busqueda tambien.
        return users.findByEmailAddress(emailAddress.trim().toLowerCase(Locale.ROOT))
                .map(UserJpaEntity::getUserId);
    }
}
