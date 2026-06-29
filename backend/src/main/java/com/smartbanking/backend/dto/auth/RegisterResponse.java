package com.smartbanking.backend.dto.auth;

import java.util.UUID;

public record RegisterResponse(
    UUID userId,
    UUID customerProfileId
) {}