package com.smartbanking.backend.controller.account;

import com.smartbanking.backend.IntegrationTestBase;
import com.smartbanking.backend.dto.account.AccountResponse;
import com.smartbanking.backend.dto.account.OpenAccountRequest;
import com.smartbanking.backend.dto.transaction.TransactionHistoryResponse;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.account.AccountType;
import com.smartbanking.backend.entity.account.Currency;
import com.smartbanking.backend.entity.kyc.KYCStatus;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.transaction.FraudStatus;
import com.smartbanking.backend.entity.transaction.Transaction;
import com.smartbanking.backend.entity.transaction.TransactionStatus;
import com.smartbanking.backend.entity.transaction.TransactionType;
import com.smartbanking.backend.entity.user.Role;
import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.auth.UserRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import com.smartbanking.backend.repository.transaction.TransactionRepository;
import com.smartbanking.backend.service.token.TokenService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


public class AccountControllerIntegrationTest extends IntegrationTestBase {
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired CustomerProfileRepository customerProfileRepository;
    @LocalServerPort int port;
    @Autowired RestClient.Builder restClientBuilder;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionRepository transactionRepository;

    private RestClient restClient;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void buildRestClient(@LocalServerPort int port) {
        this.port = port;
        this.restClient = restClientBuilder.baseUrl("http://localhost:" + port).build();
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

    @Test
    void lockAccount_withToken_activeAccount_returns200AndStatusLocked() {
        String token = createUserWithAccountAndToken();
        AccountResponse response = restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/lock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(AccountResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).isEqualTo("ACC012345678");
        assertThat(response.status()).isEqualTo("LOCKED");
    }

    @Test
    void lockAccount_withToken_alreadyLockedAccount_returns409() {
        String token = createUserWithAccountAndToken();
        restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/lock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC012345678/lock")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 409 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void lockAccount_withToken_otherUsersAccount_returns404() {
        String token = createUserWithAccountAndToken();
        // Create another user with different phone + citizenId (unique fields)
        User otherUser = newUserWithProfile("+84975097208", "012345678902");
        Account otherAccount = new Account(
                "ACC999999999",
                new BigDecimal("50000.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        otherUser.getProfile().addAccount(otherAccount);
        userRepository.save(otherUser);
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC999999999/lock")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 404 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void lockAccount_withToken_accountNotFound_returns404() {
        String token = createUserWithAccountAndToken();
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC000000000/lock")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 404 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void lockAccount_withoutToken_returns401() {
        createUserWithAccountAndToken();
        HttpStatusCode statusCode = restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/lock")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unlockAccount_withToken_lockedAccount_returns200AndStatusActive() {
        String token = createUserWithAccountAndToken();
        // Lock first
        restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/lock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
        // Then unlock
        AccountResponse response = restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/unlock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(AccountResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void unlockAccount_withToken_alreadyActiveAccount_returns409() {
        String token = createUserWithAccountAndToken();
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC012345678/unlock")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 409 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void lockAccount_withToken_closedAccount_returns409() throws Exception {
        String token = createUserWithAccountAndToken();
        markAccountAsClosed("ACC012345678");
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC012345678/lock")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 409 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void getAccountTransactions_withToken_otherUsersAccount_returns404() {
        String token = createUserWithAccountAndToken();
        User otherUser = newUserWithProfile("+84975097208", "012345678902");
        Account otherAccount = new Account(
                "ACC999999999",
                new BigDecimal("50000.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        otherUser.getProfile().addAccount(otherAccount);
        userRepository.save(otherUser);

        try {
            restClient
                    .get()
                    .uri("/api/v1/accounts/ACC999999999/transactions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 404 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void getAccountTransactions_withToken_oneTransaction_returns200AndCorrectDirection() {
        String token = createUserWithAccountAndToken();
        User counterparty = newUserWithProfile("+84975097210", "012345678910");
        Account counterpartyAccount = new Account(
                "ACC999999999",
                new BigDecimal("0.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        counterparty.getProfile().addAccount(counterpartyAccount);
        userRepository.save(counterparty);

        seedTransaction("ACC012345678", "ACC999999999", new BigDecimal("50000.00"), "Test transfer");

        TransactionHistoryResponse[] response = restClient
                .get()
                .uri("/api/v1/accounts/ACC012345678/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TransactionHistoryResponse[].class);

        assertThat(response).isNotNull().hasSize(1);
        assertThat(response[0].direction()).isEqualTo(TransactionHistoryResponse.Direction.DEBIT);
        assertThat(response[0].counterpartyAccountNumber()).isEqualTo("ACC999999999");
        assertThat(response[0].amount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void getAccountTransactions_withToken_invalidFromDateFormat_returns400() {
        String token = createUserWithAccountAndToken();
        try {
            restClient
                    .get()
                    .uri("/api/v1/accounts/ACC012345678/transactions?fromDate=abc")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 400 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }


    @Test
    void getAccountTransactions_withoutToken_returns401() {
        String accountNumber = createUserWithAccountAndToken();
        HttpStatusCode statusCode = restClient
                .get()
                .uri("/api/v1/accounts/" + accountNumber + "/transactions")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void closeAccount_withToken_emptyBalance_returns200AndStatusClosed() {
        String token = createUserWithZeroBalanceAccountAndToken();
        AccountResponse response = restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(AccountResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).isEqualTo("ACC012345678");
        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void closeAccount_withoutToken_returns401() {
        createUserWithZeroBalanceAccountAndToken();
        HttpStatusCode statusCode = restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/close")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(statusCode).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void closeAccount_withToken_otherUsersAccount_returns404() {
        String token = createUserWithZeroBalanceAccountAndToken();
        User otherUser = newUserWithProfile("+84975097208", "012345678902");
        Account otherAccount = new Account(
                "ACC999999999",
                new BigDecimal("0.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        otherUser.getProfile().addAccount(otherAccount);
        userRepository.save(otherUser);
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC999999999/close")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 404 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void closeAccount_withToken_alreadyClosedAccount_returns409() throws Exception {
        String token = createUserWithZeroBalanceAccountAndToken();
        restClient
                .patch()
                .uri("/api/v1/accounts/ACC012345678/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
        try {
            restClient
                    .patch()
                    .uri("/api/v1/accounts/ACC012345678/close")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 409 but got 2xx instead");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    private User newUserWithProfile() {
        return newUserWithProfile(
                "+84975097207",
                String.format("%012d", (long) (Math.random() * 1_000_000_000_000L))
        );
    }

    private User newUserWithProfile(String phoneNumber, String citizenId) {
        User user = new User(
                "test-" + UUID.randomUUID() + "@example.com",
                phoneNumber,
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        CustomerProfile customerProfile = new CustomerProfile(
                user,
                "Test Profile",
                citizenId,
                LocalDate.of(1999, 1, 1)
        );
        user.assignProfile(customerProfile);
        return user;
    }

    @Test
    void openAccount_withoutToken_returns401() {
        HttpStatusCode status = restClient
                .post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange((req, res) -> res.getStatusCode());
        assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void openAccount_kycPending_returns403() {
        String token = createUserWithEkycAndToken(KYCStatus.PENDING);
        try {
            restClient.post()
                    .uri("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 403");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void openAccount_kycApproved_returns201WithZeroBalanceVnd() {
        String token = createUserWithEkycAndToken(KYCStatus.APPROVED);
        ResponseEntity<AccountResponse> responseEntity = restClient.post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(AccountResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accountNumber()).hasSize(15);
        assertThat(body.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.currency()).isEqualTo("VND");
        assertThat(body.status()).isEqualTo("ACTIVE");
        assertThat(responseEntity.getHeaders().getLocation()).isNotNull();
        assertThat(responseEntity.getHeaders().getLocation().getPath())
                .endsWith("/api/v1/accounts/" + body.accountNumber());
    }

    @Test
    void openAccount_kycApprovedSecondCall_returns409WithLocationHeader() {
        String token = createUserWithEkycAndToken(KYCStatus.APPROVED);
        AccountResponse first = restClient.post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(AccountResponse.class);

        try {
            restClient.post()
                    .uri("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .toBodilessEntity();
            Assertions.fail("Expected 409");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            String location = ex.getResponseHeaders() != null
                    ? ex.getResponseHeaders().getFirst(HttpHeaders.LOCATION)
                    : null;
            assertThat(location).isNotNull();
            assertThat(location).endsWith("/api/v1/accounts/" + first.accountNumber());
        }
    }

    @Test
    void openAccount_kycApprovedWithUsdCurrency_returns201WithUsd() {
        String token = createUserWithEkycAndToken(KYCStatus.APPROVED);
        OpenAccountRequest body = new OpenAccountRequest(Currency.USD);
        AccountResponse response = restClient.post()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(AccountResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
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

    private String createUserWithAccountAndToken() {
        User user = newUserWithProfile();
        Account account = new Account(
                "ACC012345678",
                new BigDecimal("100000.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        user.getProfile().addAccount(account);
        userRepository.save(user);
        return tokenService.issueAccessToken(user).token();
    }

    private void markAccountAsClosed(String accountNumber) throws Exception {
        Account account = accountRepository.findById(accountNumber).orElseThrow();
        java.lang.reflect.Field statusField = Account.class.getDeclaredField("accountStatus");
        statusField.setAccessible(true);
        statusField.set(account, com.smartbanking.backend.entity.account.AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    private String createUserWithZeroBalanceAccountAndToken() {
        User user = newUserWithProfile();
        Account account = new Account(
                "ACC012345678",
                new BigDecimal("0.00"),
                Currency.VND,
                AccountType.DEBIT
        );
        user.getProfile().addAccount(account);
        userRepository.save(user);
        return tokenService.issueAccessToken(user).token();
    }


    private Transaction seedTransaction(
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String description
    ) {
        Transaction tx = new Transaction(
                UUID.randomUUID(),
                "REF" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                fromAccount,
                toAccount,
                amount,
                BigDecimal.ZERO,
                TransactionType.INTERNAL,
                TransactionStatus.SUCCESS,
                null,
                FraudStatus.CLEAR,
                BigDecimal.ZERO,
                amount,
                BigDecimal.ZERO,
                amount,
                description
        );
        return transactionRepository.save(tx);
    }

    private String createUserWithEkycAndToken(KYCStatus status) {
        User user = newUserWithProfile();
        userRepository.save(user);
        try {
            CustomerProfile profile = user.getProfile();
            java.lang.reflect.Field f = CustomerProfile.class.getDeclaredField("kycStatus");
            f.setAccessible(true);
            f.set(profile, status);
            customerProfileRepository.save(profile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tokenService.issueAccessToken(user).token();
    }
}