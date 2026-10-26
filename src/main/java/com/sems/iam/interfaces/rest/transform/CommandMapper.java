package com.sems.iam.interfaces.rest.transform;

import com.sems.iam.domain.model.commands.*;
import com.sems.iam.interfaces.rest.resources.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * CommandMapper converts the API request objects (DTOs received over HTTP) into
 * the domain "command" objects the application services expect. This small
 * translation layer keeps the two worlds decoupled: the public request shape can
 * change without affecting the internal commands, and vice versa.
 *
 * All three methods are named toCommand; Java tells them apart by their
 * parameter types ("method overloading").
 */
@Component
public class CommandMapper {
    public RegisterUserCommand toCommand(RegisterRequest request) { return new RegisterUserCommand(request.emailAddress(), request.password(),
            request.role(), request.fullName(), request.segment()); }
    public LoginCommand toCommand(LoginRequest request) { return new LoginCommand(request.emailAddress(), request.password()); }
    // Here the id comes from the URL path, while the role comes from the body,
    // so this overload takes both pieces and combines them into one command.
    public AssignRoleCommand toCommand(UUID userId, AssignRoleRequest request) { return new AssignRoleCommand(userId, request.role()); }
}
