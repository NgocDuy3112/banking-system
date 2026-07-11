package com.smartbanking.backend.exception.otp;

import java.util.UUID;

public class OtpLockedException extends RuntimeException {
    private final UUID userId;
    private final int maxAttempts;

    public OtpLockedException(UUID userId, int maxAttempts) {
        super("OTP locked for user " + userId + " after " + maxAttempts + " failed attempts");
        this.userId = userId;
        this.maxAttempts = maxAttempts;
    }

    public UUID getUserId() { return userId; }
    public int getMaxAttempts() { return maxAttempts; }
}