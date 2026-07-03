package com.smartbanking.backend.service.auth;

import com.smartbanking.backend.config.AppAuthProperties;

import com.smartbanking.backend.dto.auth.*;

import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.user.*;

import com.smartbanking.backend.repository.auth.UserRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;

import com.smartbanking.backend.exception.auth.*;

import com.smartbanking.backend.service.token.TokenService;
import com.smartbanking.backend.service.token.TokenService.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Service
public class AuthService {
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final AppAuthProperties authProperties;

    public AuthService(
        UserRepository userRepository, 
        CustomerProfileRepository customerProfileRepository, 
        PasswordService passwordService, 
        TokenService tokenService, 
        RefreshTokenStore refreshTokenStore, 
        AppAuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.refreshTokenStore = refreshTokenStore;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User(
            email,
            request.phoneNumber(),
            passwordService.hash(request.password()),
            Role.CUSTOMER
        );
        

        CustomerProfile profile = new CustomerProfile(
            user,
            request.fullName(),
            request.citizenId(),
            request.dateOfBirth()
        );
        user.assignProfile(profile);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        switch (user.getStatus()) {
            case DISABLED -> throw new UserDisabledException(user.getId());
            case LOCKED -> throw new UserLockedException(user.getId());
            case ACTIVE -> {}
        }
        if (!passwordService.matches(request.password(), user.getHashedPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        refreshTokenStore.delete(user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        AuthenticatedToken auth = tokenService.parseAccessToken(request.accessToken());
        String tokenHash = tokenService.hashRefreshToken(request.refreshToken());
        UUID userId = auth.userId();
        if (!refreshTokenStore.matches(userId, tokenHash)) {
            throw new InvalidTokenException("Refresh token does not match");
        }
        User user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidTokenException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidTokenException("User no longer active");
        }
        refreshTokenStore.delete(userId);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        AuthenticatedToken auth = tokenService.parseAccessToken(request.accessToken());
        String tokenHash = tokenService.hashRefreshToken(request.refreshToken());
        UUID userId = auth.userId();
        if (!refreshTokenStore.matches(userId, tokenHash)) {
            return;
        }
        refreshTokenStore.delete(userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        IssuedAccessToken accessToken = tokenService.issueAccessToken(user);
        IssuedRefreshToken refreshToken = tokenService.issueRefreshToken();
        Duration refreshTokenTtl = authProperties.refresh().ttl();
        refreshTokenStore.store(user.getId(), refreshToken.hash(), refreshTokenTtl);
        Instant refreshExpiresAt = Instant.now().plus(refreshTokenTtl);
        return new AuthResponse(
            accessToken.token(),
            refreshToken.token(),
            accessToken.expiresAt(),
            refreshExpiresAt
        );
    }
}
