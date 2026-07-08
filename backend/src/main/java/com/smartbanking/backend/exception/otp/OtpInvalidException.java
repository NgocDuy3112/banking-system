package com.smartbanking.backend.exception.otp;

import java.util.UUID;

public class OtpInvalidException extends RuntimeException {
    private final UUID userId;

    public OtpInvalidException(UUID userId) {
        super("Invalid OTP code for user " + userId.toString());
        this.userId = userId;
    }

    public UUID getUserId() {
        return this.userId;
    }
}