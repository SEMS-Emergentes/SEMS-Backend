package com.sems.iam.interfaces.rest.resources;

import java.util.List;
import java.util.UUID;

public record UserResource(UUID userId, String emailAddress, List<String> roles,
                           String fullName, String segment) {
}
