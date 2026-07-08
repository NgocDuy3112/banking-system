package com.smartbanking.backend.service.transaction;

import com.smartbanking.backend.dto.transaction.TransactionRequest;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.account.AccountStatus;
import com.smartbanking.backend.entity.account.AccountType;
import com.smartbanking.backend.entity.account.Currency;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.transaction.Transaction;
import com.smartbanking.backend.entity.transaction.TransactionStatus;
import com.smartbanking.backend.entity.transaction.TransactionType;
import com.smartbanking.backend.entity.user.Role;
import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.exception.account.AccountNotActiveException;
import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.account.InsufficientFundsException;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;
import com.smartbanking.backend.exception.otp.OtpExpiredException;
import com.smartbanking.backend.exception.otp.OtpInvalidException;
import com.smartbanking.backend.exception.transaction.CurrencyMismatchException;
import com.smartbanking.backend.exception.transaction.SelfTransferException;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import com.smartbanking.backend.repository.transaction.TransactionRepository;
import com.smartbanking.backend.service.otp.OtpService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {
    @Mock OtpService otpService;
    @Mock CustomerProfileRepository customerProfileRepository;
    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks TransactionService transactionService;

    private static final String FROM_ACC = "123456789";
    private static final String TO_ACC = "987654321";
    private static final String OTP_CODE = "123456";

    private User newUser() {
        return new User("user@example.com", "0987654321", "hashPassword", Role.CUSTOMER);
    }

    private CustomerProfile newCustomerProfile(User user) {
        return new CustomerProfile(user, "Test", "012345678901", LocalDate.of(1999, 1, 1));
    }

    private Account newAccount(String accountNumber, BigDecimal balance, Currency currency) {
        return new Account(accountNumber, balance, currency, AccountType.DEBIT);
    }

    private TransactionRequest newTransactionRequest(BigDecimal amount) {
        return new TransactionRequest(FROM_ACC, TO_ACC, amount, "note", OTP_CODE);
    }

    private void stubOtpAndProfile(UUID userId, CustomerProfile profile) {
        doNothing().when(otpService).verifyTransferOtp(userId, OTP_CODE);
        when(customerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    }

    private void stubAccounts(Account fromAccount, Account toAccount) {
        when(accountRepository.findByAccountNumberAndCustomerProfileId(FROM_ACC, fromAccount.getCustomerProfile().getId()))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate(FROM_ACC)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate(TO_ACC)).thenReturn(Optional.of(toAccount));
    }

    @Test
    void transfer_validRequest_updatesBalancesAndReturnsResponse() {
        UUID userId = UUID.randomUUID();
        User user = newUser();
        CustomerProfile customerProfile = newCustomerProfile(user);
        user.assignProfile(customerProfile);

        Account fromAccount = newAccount(FROM_ACC, new BigDecimal("10000.00"), Currency.VND);
        Account toAccount = newAccount(TO_ACC, new BigDecimal("5000.00"), Currency.VND);
        customerProfile.addAccount(fromAccount);

        stubOtpAndProfile(userId, customerProfile);
        stubAccounts(fromAccount, toAccount);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00")));

        assertThat(response.fromBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(response.fromBalanceAfter()).isEqualByComparingTo("8000.00");
        assertThat(response.toBalanceBefore()).isEqualByComparingTo("5000.00");
        assertThat(response.toBalanceAfter()).isEqualByComparingTo("7000.00");
        assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.transactionType()).isEqualTo(TransactionType.INTERNAL);
        assertThat(response.referenceNumber()).startsWith("TXN-");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getFromBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(saved.getFromBalanceAfter()).isEqualByComparingTo("8000.00");
        assertThat(saved.getToBalanceBefore()).isEqualByComparingTo("5000.00");
        assertThat(saved.getToBalanceAfter()).isEqualByComparingTo("7000.00");
        assertThat(saved.getFraudStatus().name()).isEqualTo("CLEAR");
        assertThat(saved.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void transfer_currencyMismatch_throwsCurrencyMismatchException() {
        UUID userId = UUID.randomUUID();
        User user = newUser();
        CustomerProfile customerProfile = newCustomerProfile(user);
        user.assignProfile(customerProfile);

        Account fromAccount = newAccount(FROM_ACC, new BigDecimal("10000.00"), Currency.VND);
        Account toAccount = newAccount(TO_ACC, new BigDecimal("5000.00"), Currency.USD);
        customerProfile.addAccount(fromAccount);

        stubOtpAndProfile(userId, customerProfile);
        stubAccounts(fromAccount, toAccount);

        assertThatThrownBy(() -> transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00"))))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("Currency mismatch")
                .hasMessageContaining(FROM_ACC)
                .hasMessageContaining("VND")
                .hasMessageContaining(TO_ACC)
                .hasMessageContaining("USD");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_insufficientFunds_throwsInsufficientFundsException() {
        UUID userId = UUID.randomUUID();
        User user = newUser();
        CustomerProfile customerProfile = newCustomerProfile(user);
        user.assignProfile(customerProfile);

        Account fromAccount = newAccount(FROM_ACC, new BigDecimal("1000.00"), Currency.VND);
        Account toAccount = newAccount(TO_ACC, new BigDecimal("5000.00"), Currency.VND);
        customerProfile.addAccount(fromAccount);

        stubOtpAndProfile(userId, customerProfile);
        stubAccounts(fromAccount, toAccount);

        assertThatThrownBy(() -> transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00"))))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds")
                .hasMessageContaining(FROM_ACC);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_whenToAccountNumberSortsBeforeFromAccountNumber_reversesLockOrderAndStillTransfersCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = newUser();
        CustomerProfile customerProfile = newCustomerProfile(user);
        user.assignProfile(customerProfile);

        String fromAccountNumber = "987654321";
        String toAccountNumber = "123456789";
        Account fromAccount = newAccount(fromAccountNumber, new BigDecimal("10000.00"), Currency.VND);
        Account toAccount = newAccount(toAccountNumber, new BigDecimal("5000.00"), Currency.VND);
        customerProfile.addAccount(fromAccount);

        doNothing().when(otpService).verifyTransferOtp(userId, OTP_CODE);
        when(customerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(customerProfile));
        when(accountRepository.findByAccountNumberAndCustomerProfileId(fromAccountNumber, customerProfile.getId()))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate(toAccountNumber)).thenReturn(Optional.of(toAccount));
        when(accountRepository.findByAccountNumberForUpdate(fromAccountNumber)).thenReturn(Optional.of(fromAccount));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionRequest transactionRequest = new TransactionRequest(
                fromAccountNumber,
                toAccountNumber,
                new BigDecimal("2000.00"),
                "note",
                OTP_CODE
        );
        var response = transactionService.transfer(userId, transactionRequest);

        assertThat(response.fromBalanceBefore()).isEqualByComparingTo("10000.00");
        assertThat(response.fromBalanceAfter()).isEqualByComparingTo("8000.00");
        assertThat(response.toBalanceBefore()).isEqualByComparingTo("5000.00");
        assertThat(response.toBalanceAfter()).isEqualByComparingTo("7000.00");
        assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.SUCCESS);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction savedTransaction = captor.getValue();
        assertThat(savedTransaction.getFromAccountNumber()).isEqualTo(fromAccountNumber);
        assertThat(savedTransaction.getToAccountNumber()).isEqualTo(toAccountNumber);
        assertThat(savedTransaction.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(savedTransaction.getFromBalanceAfter()).isEqualByComparingTo("8000.00");
        assertThat(savedTransaction.getToBalanceAfter()).isEqualByComparingTo("7000.00");
    }

    @Test
    void transfer_invalidOtp_throwsOtpInvalidException_andSkipsProfileLookup() {
        UUID userId = UUID.randomUUID();
        OtpInvalidException otpInvalidException = new OtpInvalidException(userId);
        doThrow(otpInvalidException).when(otpService).verifyTransferOtp(userId, OTP_CODE);

        assertThatThrownBy(() -> transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00"))))
                .isInstanceOf(OtpInvalidException.class)
                .isSameAs(otpInvalidException);

        verify(customerProfileRepository, never()).findByUserId(any());
        verify(accountRepository, never()).findByAccountNumberAndCustomerProfileId(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_expiredOtp_throwsOtpExpiredException_andSkipsProfileLookup() {
        UUID userId = UUID.randomUUID();
        OtpExpiredException otpExpiredException = new OtpExpiredException(userId);
        doThrow(otpExpiredException).when(otpService).verifyTransferOtp(userId, OTP_CODE);

        assertThatThrownBy(() -> transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00"))))
                .isInstanceOf(OtpExpiredException.class)
                .isSameAs(otpExpiredException);
        verify(customerProfileRepository, never()).findByUserId(any());
        verify(accountRepository, never()).findByAccountNumberAndCustomerProfileId(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_profileMissing_throwsCustomerProfileMissingException() {
        UUID userId = UUID.randomUUID();
        doNothing().when(otpService).verifyTransferOtp(userId, OTP_CODE);
        when(customerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00"))))
                .isInstanceOf(CustomerProfileMissingException.class)
                .hasMessageContaining(userId.toString());
        verify(accountRepository, never()).findByAccountNumberAndCustomerProfileId(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_fromAccountNotOwnedByProfile_throwsAccountNotOwnedException() {
        UUID userId = UUID.randomUUID();
        User user = newUser();
        CustomerProfile customerProfile = newCustomerProfile(user);
        user.assignProfile(customerProfile);

        stubOtpAndProfile(userId, customerProfile);
        when(accountRepository.findByAccountNumberAndCustomerProfileId(FROM_ACC, customerProfile.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(userId, newTransactionRequest(new BigDecimal("2000.00"))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(FROM_ACC);
        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_sameFromAndToAccount_throwsSelfTransferException() {
        UUID userId = UUID.randomUUID();
        User user = newUser();
        CustomerProfile customerProfile = newCustomerProfile(user);
        user.assignProfile(customerProfile);

        Account fromAccount = newAccount(FROM_ACC, new BigDecimal("1000.00"), Currency.VND);
        customerProfile.addAccount(fromAccount);

        stubOtpAndProfile(userId, customerProfile);
        when(accountRepository.findByAccountNumberAndCustomerProfileId(fromAccount.getAccountNumber(), customerProfile.getId()))
                .thenReturn(Optional.of(fromAccount));
        TransactionRequest transactionRequest = new TransactionRequest(FROM_ACC, FROM_ACC, new BigDecimal("200.00"), "note", OTP_CODE);
        assertThatThrownBy(() -> transactionService.transfer(userId, transactionRequest))
                .isInstanceOf(SelfTransferException.class)
                .hasFieldOrPropertyWithValue("accountNumber", FROM_ACC)
                .hasMessageContaining(FROM_ACC);
        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }
}