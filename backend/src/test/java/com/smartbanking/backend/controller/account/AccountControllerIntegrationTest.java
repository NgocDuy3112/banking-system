package com.smartbanking.backend.controller.account;

import com.smartbanking.backend.IntegrationTestBase;
import com.smartbanking.backend.dto.account.AccountResponse;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.account.AccountType;
import com.smartbanking.backend.entity.account.Currency;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.user.Role;
import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.auth.UserRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import com.smartbanking.backend.service.token.TokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;


public class AccountControllerIntegrationTest extends IntegrationTestBase {
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired CustomerProfileRepository customerProfileRepository;
    @LocalServerPort int port;
    @Autowired RestClient.Builder restClientBuilder;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;

    private RestClient restClient;
    private String token;
    private String accountNumber;


    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
        customerProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void buildRestClient(@LocalServerPort int port) {
        this.port = port;
        this.restClient = restClientBuilder.baseUrl("http://localhost:" + port).build();
    }

    private User newUserWithProfile() {
        User user = new User(
                "test-" + UUID.randomUUID() + "@example.com",
                "+84975097207",
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        CustomerProfile customerProfile = new CustomerProfile(
                user,
                "Test Profile",
                "012345678901",
                LocalDate.of(1999, 1, 1)
        );
        user.assignProfile(customerProfile);
        return user;
    }

    private String createUserWithAccount() {
        User user = newUserWithProfile();
        Account account = new Account(
                "ACC012345678",
                new BigDecimal("100000.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        user.getProfile().addAccount(account);
        userRepository.save(user);
        return account.getAccountNumber();
    }

    private String createUserAndGetToken() {
        User user = newUserWithProfile();
        userRepository.save(user);
        return tokenService.issueAccessToken(user).token();
    }


    @Test
    void listAccounts_unauthorized_returns401() {
        HttpStatusCode statusCode = restClient
                .get()
                .uri("/api/v1/accounts")
                .exchange((request, response)
                        -> response.getStatusCode()
                );
        assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listAccounts_withToken_noAccounts_returnsEmptyList() {
        String token = createUserAndGetToken();
        AccountResponse[] accounts = restClient
                .get()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(AccountResponse[].class);
        assertThat(accounts).isEmpty();
    }
}