package com.smartbanking.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank String accessToken,
    @NotBlank String refreshToken
) {}