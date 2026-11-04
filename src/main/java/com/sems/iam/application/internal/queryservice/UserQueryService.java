package com.sems.iam.application.internal.queryservice;

import com.sems.iam.infrastructure.persistence.jpa.repositories.*;
import com.sems.iam.domain.model.exceptions.NotFoundException;
import com.sems.iam.interfaces.rest.resources.UserResource;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserQueryService is the "query" side (read-only) of the CQRS split: commands
 * change data, queries only fetch it. Notice how short these methods are -
 * reads usually have no business rules, so the service just loads data and turns
 * it into a response resource (DTO).
 */
@Service
@RequiredArgsConstructor
// Sin transaccion abierta, la relacion perezosa hacia el rol no se puede
// cargar: spring.jpa.open-in-view esta en false, de modo que la sesion de
// Hibernate ya esta cerrada cuando el controlador serializa la respuesta y
// toResource() falla con LazyInitializationException, que el manejador global
// traduce a un 500 sin pista de la causa. readOnly = true porque estas
// consultas no escriben.
@Transactional(readOnly = true)
public class UserQueryService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    /** Fetches one user by id, or throws 404-style NotFoundException. */
    public UserResource getById(UUID userId) {
        UserJpaEntity user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        return toResource(user);
    }

    /** Fetches every user and maps each one to a resource. */
    public List<UserResource> getAll() { return userRepository.findAll().stream().map(this::toResource).toList(); }

    /**
     * Private helper that converts a database entity into the API resource the
     * outside world sees. It also loads the user's role names. Keeping this
     * mapping in one place avoids repeating it in getById and getAll.
     */
    private UserResource toResource(UserJpaEntity user) {
        List<String> roles = userRoleRepository.findByUser_UserId(user.getUserId()).stream().map(ur -> ur.getRole().getName()).toList();
        return new UserResource(user.getUserId(), user.getEmailAddress(), roles,
                user.getFullName(), user.getSegment());
    }
}
