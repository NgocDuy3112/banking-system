package com.smartbanking.backend.service;

import com.smartbanking.backend.config.AppAuthProperties;
import com.smartbanking.backend.entity.user.*;
import com.smartbanking.backend.exception.auth.InvalidTokenException;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;


@Service
public class TokenService {
    private static final SecureRandom RNG = new SecureRandom();

    private final AppAuthProperties props;
    private final SecretKey signingKey;

    public record IssuedAccessToken(String token, Instant expiresAt) {}

    public record AuthenticatedToken(UUID userId, Role role, String email) {}

    public record IssuedRefreshToken(String token, String hash) {}

    public TokenService(AppAuthProperties props) {
        this.props = Objects.requireNonNull(props, "props must not be null");
        byte[] keyBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedAccessToken issueAccessToken(User user) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(user.getId(), "user.id must not be null");
        Duration ttl = props.jwt().ttl();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwt().ttl());
        String token = Jwts.builder()
                .issuer(props.jwt().issuer())
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    public AuthenticatedToken parseAccessToken(String token) {
        Objects.requireNonNull(token, "tokesn must not be null");
        try {
            Claims claims = Jwts.parser()
                            .verifyWith(signingKey)
                            .requireIssuer(props.jwt().issuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            String email = claims.get("email", String.class);
            return new AuthenticatedToken(userId, role, email);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(e.getMessage(), e);
        }
    }

    public IssuedRefreshToken issueRefreshToken() {
        byte[] random = new byte[32];
        RNG.nextBytes(random);
        String plainText = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash = hashRefreshToken(plainText);
        return new IssuedRefreshToken(plainText, hash);
    }

    public String hashRefreshToken(String plainText) {
        Objects.requireNonNull(plainText, "plainText must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
