package com.sems.iam.domain.model.commands;

public record RegisterUserCommand(String emailAddress, String password, String role,
                                  String fullName, String segment) {
}
