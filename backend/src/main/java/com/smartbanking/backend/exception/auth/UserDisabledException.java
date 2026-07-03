package com.smartbanking.backend.exception.auth;

import java.util.UUID;

public class UserDisabledException extends RuntimeException {
    private final UUID userId;

    public UserDisabledException(UUID userId) {
        super("User " + userId + " is disabled");
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}