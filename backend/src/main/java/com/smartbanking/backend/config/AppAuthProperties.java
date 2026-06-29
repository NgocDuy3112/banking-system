package com.smartbanking.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix="app")
public record AppAuthProperties(Jwt jwt, Refresh refresh) {
    public AppAuthProperties {
        if (jwt == null) {
            throw new IllegalStateException("app.jwt must be configured");
        }
        if (refresh == null) {
            throw new IllegalStateException("app.refresh must be configured");
        }
    }

    public record Jwt(
        @NotBlank String secret,
        @NotNull Duration ttl,
        @NotBlank String issuer
    ) {}

    public record Refresh(@NotNull Duration ttl) {}
}
