package com.smartbanking.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;


@Validated
@ConfigurationProperties(prefix="app.minio")
public record AppS3Properties(
    @NotNull S3Provider provider,
    @NotBlank String region,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket,
    @NotNull Duration presignedUrlTtl,
    String endpoint
) {
    public AppS3Properties {
        if (provider != S3Provider.AWS && (endpoint == null || endpoint.isBlank())) {
            throw new IllegalStateException(
                    "app.minio.endpoint is required when app.minio.provider is not AWS");
        }
    }
}