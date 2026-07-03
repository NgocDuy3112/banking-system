package com.smartbanking.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank String accessToken,
    @NotBlank String refreshToken
) {}