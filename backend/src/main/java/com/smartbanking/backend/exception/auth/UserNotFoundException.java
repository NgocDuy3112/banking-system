package com.smartbanking.backend.exception.auth;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    private final UUID userId;

    public UserNotFoundException(UUID userId) {
      super("User not found for id: " + userId);
      this.userId = userId;
    }

    public UUID getUserId() {
      return userId;
    }
}