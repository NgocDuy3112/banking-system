package com.smartbanking.backend.exception.auth;

import java.util.UUID;

public class UserLockedException extends RuntimeException {
    private final UUID userId;

    public UserLockedException(UUID userId) {
        super("User " + userId + " is locked");
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}