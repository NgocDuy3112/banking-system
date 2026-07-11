package com.smartbanking.backend.service.account;

import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.entity.user.Role;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.kyc.KYCStatus;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.account.AccountType;
import com.smartbanking.backend.entity.account.Currency;
import com.smartbanking.backend.exception.account.AccountAlreadyExistsException;
import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.account.InsufficientFundsException;
import com.smartbanking.backend.exception.kyc.EkycNotApprovedException;
import com.smartbanking.backend.exception.transaction.CurrencyMismatchException;
import com.smartbanking.backend.exception.transaction.SelfTransferException;
import com.smartbanking.backend.repository.account.AccountRepository;

import com.smartbanking.backend.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
    @Mock UserRepository userRepository;
    @Mock AccountRepository accountRepository;
    @InjectMocks AccountService accountService;

    private static final String FROM_ACC  = "123456789";
    private static final String TO_ACC = "987654321";

    @Test
    void updateBalance_validRequest_debitsAndCredits() {
        Account fromAccount = newAccount(FROM_ACC, new BigDecimal("10000.00"), Currency.VND);
        Account toAccount = newAccount(TO_ACC, new BigDecimal("4000.00"), Currency.VND);
        stubLock(fromAccount);
        stubLock(toAccount);

        BalanceUpdateResult result = accountService.updateBalance(FROM_ACC, TO_ACC, new BigDecimal("2000.00"));
        assertThat(result.fromAccountNumber()).isEqualTo(FROM_ACC);
        assertThat(result.toAccountNumber()).isEqualTo(TO_ACC);
        assertThat(result.fromBalanceBefore()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(result.fromBalanceAfter()).isEqualByComparingTo(new BigDecimal("8000.00"));
        assertThat(result.toBalanceBefore()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(result.toBalanceAfter()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(fromAccount.getBalance()).isEqualByComparingTo(new BigDecimal("8000.00"));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    @Test
    void updateBalance_sameFromAndToAccount_throwsSelfTransferException() {
        assertThatThrownBy(() -> accountService.updateBalance(FROM_ACC, FROM_ACC, new BigDecimal("20000.00")))
                .isInstanceOf(SelfTransferException.class)
                .hasFieldOrPropertyWithValue("accountNumber", FROM_ACC)
                .hasMessageContaining(FROM_ACC);
        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
    }

    @Test
    void updateBalance_currencyMismatch_throwsCurrencyMismatchException() {
        Account fromAccount = newAccount(FROM_ACC, new BigDecimal("10000.00"), Currency.VND);
        Account toAccount = newAccount(TO_ACC, new BigDecimal("4000.00"), Currency.USD);
        stubLock(fromAccount);
        stubLock(toAccount);

        assertThatThrownBy(() -> accountService.updateBalance(FROM_ACC, TO_ACC, new BigDecimal("2000.00")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch")
                .hasMessageContaining(FROM_ACC).hasMessageContaining("VND")
                .hasMessageContaining(TO_ACC).hasMessageContaining("USD");
    }

    @Test
    void updateBalance_insufficientFunds_throwsInsufficientFundsException() {
        Account from = newAccount(FROM_ACC, new BigDecimal("1000.00"),  Currency.VND);
        Account to = newAccount(TO_ACC, new BigDecimal("5000.00"), Currency.VND);
        stubLock(from);
        stubLock(to);

        assertThatThrownBy(() ->
                accountService.updateBalance(FROM_ACC, TO_ACC, new BigDecimal("2000.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds")
                .hasMessageContaining(FROM_ACC);
    }

    @Test
    void updateBalance_accountNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findByAccountNumberForUpdate(FROM_ACC))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.updateBalance(FROM_ACC, TO_ACC, new BigDecimal("2000.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(FROM_ACC);
    }

    @Test
    void updateBalance_whenToAccountNumberSortsBeforeFromAccountNumber_reversesLockOrderAndStillTransfersCorrectly() {
        Account from = newAccount(TO_ACC, new BigDecimal("10000.00"), Currency.VND);
        Account to = newAccount(FROM_ACC, new BigDecimal("5000.00"), Currency.VND);
        stubLock(from);
        stubLock(to);

        BalanceUpdateResult result = accountService.updateBalance(TO_ACC, FROM_ACC, new BigDecimal("2000.00"));

        assertThat(result.fromAccountNumber()).isEqualTo(TO_ACC);
        assertThat(result.toAccountNumber()).isEqualTo(FROM_ACC);
        assertThat(result.fromBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(result.fromBalanceAfter()).isEqualByComparingTo("8000.00");
        assertThat(result.toBalanceBefore()).isEqualByComparingTo("5000.00");
        assertThat(result.toBalanceAfter()).isEqualByComparingTo("7000.00");
    }

    @Test
    void openAccount_kycNotApproved_throwsKycNotApprovedException() {
        User user = newCustomer(KYCStatus.PENDING);
        UUID userId = user.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountService.openAccount(userId, null))
                .isInstanceOf(EkycNotApprovedException.class)
                .hasFieldOrPropertyWithValue("currentStatus", KYCStatus.PENDING);

        verify(accountRepository, never()).existsByCustomerProfileId(any());
    }

    @Test
    void openAccount_alreadyHasAccount_throwsAccountAlreadyExistsException() {
        User user = newCustomer(KYCStatus.APPROVED);
        UUID userId = user.getId();
        UUID profileId = user.getProfile().getId();
        String existingAccNum = "123456789012345";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.existsByCustomerProfileId(profileId)).thenReturn(true);
        Account existing = new Account(existingAccNum, new BigDecimal("0.00"), Currency.VND, AccountType.DEBIT);
        when(accountRepository.findByCustomerProfileId(profileId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> accountService.openAccount(userId, null))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasFieldOrPropertyWithValue("accountNumber", existingAccNum);
    }

    @Test
    void openAccount_kycApprovedNoAccount_returnsZeroBalanceVnd() {
        User user = newCustomer(KYCStatus.APPROVED);
        UUID userId = user.getId();
        UUID profileId = user.getProfile().getId();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.existsByCustomerProfileId(profileId)).thenReturn(false);
        when(accountRepository.existsById(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = accountService.openAccount(userId, null);

        assertThat(response).isNotNull();
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.accountNumber()).hasSize(15);
    }

    private Account newAccount(String accountNumber, BigDecimal balance, Currency currency) {
        return new Account(accountNumber, balance, currency, AccountType.DEBIT);
    }

    private void stubLock(Account account) {
        when(accountRepository.findByAccountNumberForUpdate(account.getAccountNumber())).thenReturn(Optional.of(account));
    }

    private User newCustomer(KYCStatus kycStatus) {
        User user = new User(
                "test-" + UUID.randomUUID() + "@example.com",
                "+84" + UUID.randomUUID().toString().substring(0, 9),
                "Password123!",
                Role.CUSTOMER
        );
        CustomerProfile profile = new CustomerProfile(
                user,
                "Test User",
                String.format("%012d", (long) (Math.random() * 1_000_000_000_000L)),
                LocalDate.of(1999, 1, 1)
        );
        user.assignProfile(profile);
        try {
            java.lang.reflect.Field f = CustomerProfile.class.getDeclaredField("kycStatus");
            f.setAccessible(true);
            f.set(profile, kycStatus);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}