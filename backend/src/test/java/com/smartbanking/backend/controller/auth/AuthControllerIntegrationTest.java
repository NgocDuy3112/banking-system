package com.smartbanking.backend.controller.auth;

import com.smartbanking.backend.IntegrationTestBase;
import com.smartbanking.backend.config.AppAuthProperties;
import com.smartbanking.backend.dto.auth.*;
import com.smartbanking.backend.repository.auth.UserRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthControllerIntegrationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired AppAuthProperties appAuthProperties;
    @Autowired UserRepository userRepository;
    @Autowired CustomerProfileRepository customerProfileRepository;
    @Autowired StringRedisTemplate redis;

    RestClient restClient;

    final String EMAIL = "nguyenlengocduy@example.com";
    final String PASSWORD = "PassW0rd3112!";
    final String FULL_NAME = "Ngoc Duy";
    final String CITIZEN_ID = "012345678901";
    final LocalDate DATE_OF_BIRTH = LocalDate.of(1990, 10, 19);
    final String PHONE_NUMBER = "0987654321";

    @BeforeEach
    void setUp() {
        customerProfileRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redis.keys("refresh:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        restClient = RestClient.create("http://localhost:" + port);
    }

    @Test
    void register_valid_returns201AndTokens() {
        RegisterRequest request = generateRegisterRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromRegister(request);
        HttpStatusCode statusCode = auth.getStatusCode();
        AuthResponse authResponse = auth.getBody();
        assertThat(statusCode).isEqualTo(HttpStatus.CREATED);
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotNull();
        assertThat(authResponse.refreshToken()).isNotNull();
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(customerProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void register_duplicateEmail_returns409() {
        RegisterRequest request = generateRegisterRequest();
        generateAuthResponseFromRegister(request);
        try {
            generateAuthResponseFromRegister(request);
            Assertions.fail("Expected 409 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            HttpStatusCode statusCode = ex.getStatusCode();
            assertThat(statusCode).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void login_validCredentials_returns200AndTokens() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(generateRegisterRequest());
        LoginRequest loginRequest = generateLoginRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromLogin(loginRequest);
        HttpStatusCode statusCode = auth.getStatusCode();
        AuthResponse authResponse = auth.getBody();

        assertThat(statusCode).isEqualTo(HttpStatus.OK);
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.accessToken()).isNotNull();
        assertThat(authResponse.refreshToken()).isNotNull();

        UUID expectedUserId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        UUID actualUserId = parseJwtSubject(authResponse.accessToken());
        assertThat(actualUserId).isEqualTo(expectedUserId);
    }

    @Test
    void login_wrongEmail_returns401() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest wrongRequest = new LoginRequest("ngocduy@example.com", PASSWORD);
        try {
            generateAuthResponseFromLogin(wrongRequest);
            Assertions.fail("Expected 401 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            HttpStatusCode statusCode = ex.getStatusCode();
            assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void login_wrongPassword_returns401() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest wrongRequest = new LoginRequest(EMAIL, "NgocDuy");
        try {
            generateAuthResponseFromLogin(wrongRequest);
            Assertions.fail("Expected 401 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            HttpStatusCode statusCode = ex.getStatusCode();
            assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void refresh_valid_returns200AndNewTokens() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest loginRequest = generateLoginRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromLogin(loginRequest);
        AuthResponse authResponse = auth.getBody();
        RefreshRequest refreshRequest = new RefreshRequest(
                authResponse.accessToken(),
                authResponse.refreshToken()
        );
        ResponseEntity<AuthResponse> refreshAuth = generateAuthResponseFromRefresh(refreshRequest);
        HttpStatusCode statusRefresh = refreshAuth.getStatusCode();
        AuthResponse refreshAuthResponse = refreshAuth.getBody();
        assertThat(statusRefresh).isEqualTo(HttpStatus.OK);
        assertThat(refreshAuthResponse).isNotNull();
        assertThat(refreshAuthResponse.accessToken()).isNotEqualTo(authResponse.accessToken());
    }

    @Test
    void refresh_invalidRefreshToken_returns401() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest loginRequest = generateLoginRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromLogin(loginRequest);
        AuthResponse authResponse = auth.getBody();
        RefreshRequest badRefreshRequest = new RefreshRequest(
                authResponse.accessToken(),
                "totally-invalid-refresh-token"
        );
        try {
            generateAuthResponseFromRefresh(badRefreshRequest);
            Assertions.fail("Expected 401 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            HttpStatusCode statusCode = ex.getStatusCode();
            assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void refresh_rotatesRefreshToken_oldOneInvalidated() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest loginRequest = generateLoginRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromLogin(loginRequest);
        AuthResponse authResponse = auth.getBody();
        RefreshRequest refreshRequest = new RefreshRequest(
                authResponse.accessToken(),
                authResponse.refreshToken()
        );
        ResponseEntity<AuthResponse> firstRefreshAuth = generateAuthResponseFromRefresh(refreshRequest);
        HttpStatusCode statusFirstRefresh = firstRefreshAuth.getStatusCode();
        assertThat(statusFirstRefresh).isEqualTo(HttpStatus.OK);
        try {
            generateAuthResponseFromRefresh(refreshRequest);
            Assertions.fail("Expected 401 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void logout_validTokens_returns204AndDeletesRefreshToken() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest loginRequest = generateLoginRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromLogin(loginRequest);
        AuthResponse authResponse = auth.getBody();
        UUID userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        assertThat(redis.hasKey(refreshKeyFor(userId))).isTrue();
        LogoutRequest logoutRequest = new LogoutRequest(
                authResponse.accessToken(),
                authResponse.refreshToken()
        );
        ResponseEntity<Void> response = generateVoidResponseFromLogout(logoutRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(redis.hasKey(refreshKeyFor(userId))).isFalse();
    }

    @Test
    void logout_invalidRefreshToken_isIdempotent() {
        RegisterRequest registerRequest = generateRegisterRequest();
        generateAuthResponseFromRegister(registerRequest);
        LoginRequest loginRequest = generateLoginRequest();
        ResponseEntity<AuthResponse> auth = generateAuthResponseFromLogin(loginRequest);
        AuthResponse authResponse = auth.getBody();
        UUID userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        LogoutRequest logoutRequest = new LogoutRequest(
                authResponse.accessToken(),
                "totally-invalid-refresh-token"
        );
        ResponseEntity<Void> response = generateVoidResponseFromLogout(logoutRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(redis.hasKey(refreshKeyFor(userId))).isTrue();
    }

    private String refreshKeyFor(UUID userId) {
        return "refresh:" + userId;
    }

    private UUID parseJwtSubject(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                appAuthProperties.jwt().secret().getBytes(StandardCharsets.UTF_8)
        );
        String sub = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(sub);
    }

    private RegisterRequest generateRegisterRequest() {
        return new RegisterRequest(
                EMAIL,
                PASSWORD,
                FULL_NAME,
                CITIZEN_ID,
                DATE_OF_BIRTH,
                PHONE_NUMBER
        );
    }

    private LoginRequest generateLoginRequest() {
        return new LoginRequest(EMAIL, PASSWORD);
    }

    private ResponseEntity<AuthResponse> generateAuthResponseFromRegister(RegisterRequest request) {
        return restClient
                .post()
                .uri("/api/auth/register")
                .body(request)
                .retrieve()
                .toEntity(AuthResponse.class);
    }

    private ResponseEntity<AuthResponse> generateAuthResponseFromLogin(LoginRequest request) {
        return restClient
                .post()
                .uri("/api/auth/login")
                .body(request)
                .retrieve()
                .toEntity(AuthResponse.class);
    }

    private ResponseEntity<AuthResponse> generateAuthResponseFromRefresh(RefreshRequest request) {
        return restClient
                .post()
                .uri("/api/auth/refresh")
                .body(request)
                .retrieve()
                .toEntity(AuthResponse.class);
    }

    private ResponseEntity<Void> generateVoidResponseFromLogout(LogoutRequest request) {
        return restClient
                .post()
                .uri("/api/auth/logout")
                .body(request)
                .retrieve()
                .toEntity(Void.class);
    }
}