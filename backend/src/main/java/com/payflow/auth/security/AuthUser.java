package com.payflow.auth.security;

import com.payflow.auth.domain.Role;

import java.util.UUID;

public record AuthUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
