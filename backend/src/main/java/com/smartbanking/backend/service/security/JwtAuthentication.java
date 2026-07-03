package com.smartbanking.backend.service.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collection;
import java.util.UUID;

public class JwtAuthentication extends AbstractAuthenticationToken {
    private final UUID userId;

    public JwtAuthentication(
            UUID userId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.userId = userId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }
}