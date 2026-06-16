package com.nguyenlengocduy.smartbanking.backend.service;

import com.nguyenlengocduy.smartbanking.backend.entity.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PasswordService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final PasswordEncoder encoder;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder must not be null");
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(hashedPassword, "hashedPassword must not be null");
        return encoder.matches(rawPassword, hashedPassword);
    }

    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        validatePolicy(rawPassword);
        return encoder.encode(rawPassword);
    }

    public PasswordChangedEvent changePassword(User user, String newRawPassword) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(newRawPassword, "newRawPassword must not be null");
        validatePolicy(newRawPassword);
        if (encoder.matches(newRawPassword, user.getHashedPassword())) {
            throw new IllegalArgumentException("New password must differ from current password");
        }
        user.setHashedPassword(encoder.encode(newRawPassword));
        return new PasswordChangedEvent(user.getId(), Instant.now());
    }

    private void validatePolicy(String rawPassword) {
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    public record PasswordChangedEvent(UUID userId, Instant changedAt) {}
}
