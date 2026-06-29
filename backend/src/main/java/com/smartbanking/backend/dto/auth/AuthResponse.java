package com.smartbanking.backend.dto.auth;

import java.time.Instant;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt
) {}