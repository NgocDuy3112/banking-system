package com.smartbanking.backend.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix="app.minio")
public record AppMinioProperties (
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    Duration presignedUrlTtl
) {
    public AppMinioProperties {
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