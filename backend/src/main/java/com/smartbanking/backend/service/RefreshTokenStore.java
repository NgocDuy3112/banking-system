package com.smartbanking.backend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class RefreshTokenStore {
    private static final String KEY_PREFIX = "refresh:";
    private final StringRedisTemplate redis;

    private static String keyFor(UUID userId) {
        return KEY_PREFIX + userId;
    }

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    public void store(UUID userId, String tokenHash, Duration ttl) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive: " + ttl);
        }
        redis.opsForValue().set(keyFor(userId), tokenHash, ttl);
    }

    public Optional<String> findHashByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return Optional.ofNullable(redis.opsForValue().get(keyFor(userId)));
    }

    public boolean delete(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return Boolean.TRUE.equals(redis.delete(keyFor(userId)));
    }

    public boolean matches(UUID userId, String tokenHash) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        return findHashByUserId(userId).map(stored -> stored.equals(tokenHash)).orElse(false);
    }
}
