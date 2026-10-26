package com.sems.iam.interfaces.rest;

import com.sems.iam.application.internal.commandservices.AccountRecoveryService;
import com.sems.iam.application.internal.commandservices.UserProfileService;
import com.sems.iam.application.internal.queryservice.UserQueryService;
import com.sems.iam.infrastructure.authorization.sfs.model.AuthenticatedUserDetails;
import com.sems.iam.interfaces.rest.resources.UpdateProfileRequest;
import com.sems.iam.interfaces.rest.resources.UserResource;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserQueryService userQueryService;
    private final AccountRecoveryService accountRecoveryService;
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserResource> me(@AuthenticationPrincipal AuthenticatedUserDetails user) { return ResponseEntity.ok(userQueryService.getById(user.getUserId())); }

    /**
     * Cambia el perfil propio.
     *
     * <p>Como en la baja, el usuario sale del token y no de la ruta: nadie
     * puede renombrar a otro.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResource> updateMe(
            @AuthenticationPrincipal AuthenticatedUserDetails user,
            @Valid @RequestBody UpdateProfileRequest request) {
        userProfileService.updateProfile(user.getUserId(), request.fullName(), request.segment());
        return ResponseEntity.ok(userQueryService.getById(user.getUserId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserResource>> all() { return ResponseEntity.ok(userQueryService.getAll()); }

    /**
     * Da de baja la propia cuenta.
     *
     * <p>Solo la propia: el principal sale del token, no de la ruta, asi que no
     * hay forma de pedir la baja de otra persona. Un administrador que necesite
     * cerrar cuentas ajenas necesitaria otro endpoint y otra comprobacion.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeMyAccount(@AuthenticationPrincipal AuthenticatedUserDetails user) {
        accountRecoveryService.closeAccount(user.getUserId());
    }
}
