package com.smartbanking.backend.exception.otp;

import java.util.UUID;

public class OtpExpiredException extends RuntimeException {
    private final UUID userId;

    public OtpExpiredException(UUID userId) {
        super("OTP expired or not found for user " + userId.toString());
        this.userId = userId;
    }

    public UUID getUserId() {
        return this.userId;
    }
}