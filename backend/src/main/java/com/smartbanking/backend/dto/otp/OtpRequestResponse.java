package com.smartbanking.backend.dto.otp;

public record OtpRequestResponse(
        int expiresInSeconds
) { }