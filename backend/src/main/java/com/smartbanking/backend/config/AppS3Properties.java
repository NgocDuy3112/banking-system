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
        if (provider != S3Provider.AWS) {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("app.s3.endpoint is required when provider is not AWS");
            }
        }
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("app.s3.region must be defined");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("app.minio.endpoint must be defined");
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalArgumentException("app.minio.access-key must be defined");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("app.minio.secret-key must be defined");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("app.minio.bucket must be defined");
        }
        if (presignedUrlTtl == null || presignedUrlTtl.isNegative()) {
            throw new IllegalArgumentException("app.minio.presigned-url-ttl must be defined");
        }
    }
}