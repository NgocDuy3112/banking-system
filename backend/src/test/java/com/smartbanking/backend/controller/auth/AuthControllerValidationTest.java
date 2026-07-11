package com.smartbanking.backend.controller.auth;

import com.smartbanking.backend.IntegrationTestBase;
import com.smartbanking.backend.dto.auth.AuthResponse;
import com.smartbanking.backend.dto.auth.LoginRequest;
import com.smartbanking.backend.dto.auth.RegisterRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import com.smartbanking.backend.repository.auth.UserRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import com.smartbanking.backend.repository.account.AccountRepository;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;


public class AuthControllerValidationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired CustomerProfileRepository customerProfileRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired StringRedisTemplate redis;

    RestClient restClient;

    final String EMAIL = "validation@example.com";
    final String PASSWORD = "PassW0rd3112!";
    final String FULL_NAME = "Validation Test";
    final String CITIZEN_ID = "012345678901";
    final LocalDate DATE_OF_BIRTH = LocalDate.of(1990, 10, 19);
    final String PHONE_NUMBER = "0987654321";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        customerProfileRepository.deleteAll();
        userRepository.deleteAll();
        redis.delete(redis.keys("refresh:*"));
        restClient = RestClient.create("http://localhost:" + port);
    }

    @Test
    void register_invalidEmail_returns400() {
        RegisterRequest request = new RegisterRequest(
                "invalid-email",
                PASSWORD,
                FULL_NAME,
                CITIZEN_ID,
                DATE_OF_BIRTH,
                PHONE_NUMBER
        );
        try {
            generateAuthResponseFromRegister(request);
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void register_passwordTooShort_returns400() {
        RegisterRequest request = new RegisterRequest(
                EMAIL,
                "short",
                FULL_NAME,
                CITIZEN_ID,
                DATE_OF_BIRTH,
                PHONE_NUMBER
        );
        try {
            generateAuthResponseFromRegister(request);
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void login_invalidEmail_returns400() {
        LoginRequest request = new LoginRequest("not-an-email", PASSWORD);
        try {
            generateAuthResponseFromLogin(request);
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void register_invalidCitizenId_returns400() {
        RegisterRequest request = new RegisterRequest(
                EMAIL,
                PASSWORD,
                FULL_NAME,
                "12345",
                DATE_OF_BIRTH,
                PHONE_NUMBER
        );
        try {
            generateAuthResponseFromRegister(request);
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void register_blankFullName_returns400() {
        RegisterRequest request = new RegisterRequest(
                EMAIL,
                PASSWORD,
                "",
                CITIZEN_ID,
                DATE_OF_BIRTH,
                PHONE_NUMBER
        );
        try {
            generateAuthResponseFromRegister(request);
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void login_blankPassword_returns400() {
        LoginRequest request = new LoginRequest(EMAIL, "");
        try {
            generateAuthResponseFromLogin(request);
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
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
}