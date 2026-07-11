package com.smartbanking.backend.service.account;

import com.smartbanking.backend.dto.account.AccountResponse;

import com.smartbanking.backend.dto.transaction.TransactionHistoryResponse;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.account.AccountType;
import com.smartbanking.backend.entity.account.Currency;
import com.smartbanking.backend.entity.kyc.KYCStatus;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.transaction.Transaction;
import com.smartbanking.backend.entity.transaction.TransactionType;
import com.smartbanking.backend.entity.user.User;

import com.smartbanking.backend.exception.account.AccountAlreadyExistsException;
import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;
import com.smartbanking.backend.exception.kyc.EkycNotApprovedException;
import com.smartbanking.backend.exception.transaction.CurrencyMismatchException;
import com.smartbanking.backend.exception.transaction.SelfTransferException;

import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.auth.UserRepository;

import com.smartbanking.backend.repository.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    private record LockedAccountPair(Account fromAccount, Account toAccount) {}

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID userId) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        return accountRepository.findByCustomerProfileId(profile.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID userId, String accountNumber) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        Account account = accountRepository
                .findByCustomerProfileIdAndAccountNumber(profile.getId(), accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse lockAccount(UUID userId, String accountNumber) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        Account account = accountRepository
                .findByCustomerProfileIdAndAccountNumber(profile.getId(), accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        account.lock();
        return toResponse(account);
    }

    @Transactional
    public AccountResponse unlockAccount(UUID userId, String accountNumber) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        Account account = accountRepository
                .findByCustomerProfileIdAndAccountNumber(profile.getId(), accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        account.unlock();
        return toResponse(account);
    }

    @Transactional
    public BalanceUpdateResult updateBalance(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        LockedAccountPair lockedAccountPair = lockPairForUpdate(fromAccountNumber, toAccountNumber);
        BigDecimal fromBeforeBalance = lockedAccountPair.fromAccount().getBalance();
        BigDecimal toBeforeBalance = lockedAccountPair.toAccount().getBalance();
        lockedAccountPair.fromAccount().debit(amount);
        lockedAccountPair.toAccount().credit(amount);
        return new BalanceUpdateResult(
                lockedAccountPair.fromAccount().getAccountNumber(),
                lockedAccountPair.toAccount().getAccountNumber(),
                fromBeforeBalance,
                lockedAccountPair.fromAccount().getBalance(),
                toBeforeBalance,
                lockedAccountPair.toAccount().getBalance()
        );
    }

    @Transactional
    public AccountResponse closeAccount(UUID userId, String accountNumber) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        Account account = accountRepository
                .findByCustomerProfileIdAndAccountNumber(profile.getId(), accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        account.close();
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> listAccountTransactions(
            UUID userId,
            String accountNumber,
            Instant fromDate,
            Instant toDate,
            TransactionType transactionType,
            Pageable pageable
    ) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        accountRepository.findByCustomerProfileIdAndAccountNumber(profile.getId(), accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        Page<Transaction> transactions = transactionRepository.findHistory(
                accountNumber, fromDate, toDate, transactionType, pageable
        );
        return transactions.map(t -> toHistoryResponse(t, accountNumber));
    }

    @Transactional
    public AccountResponse openAccount(UUID userId, Currency requestedCurrency) {
        CustomerProfile profile = resolveCustomerProfile(userId);
        if (profile.getKycStatus() != KYCStatus.APPROVED) {
            throw new EkycNotApprovedException(profile.getKycStatus());
        }
        if (accountRepository.existsByCustomerProfileId(profile.getId())) {
            Account existing = accountRepository
                    .findByCustomerProfileId(profile.getId())
                    .get(0);
            throw new AccountAlreadyExistsException(existing.getAccountNumber());
        }
        String accountNumber = generateAccountNumber();
        Currency currency = requestedCurrency != null ? requestedCurrency : Currency.VND;
        Account account = new Account(accountNumber, BigDecimal.ZERO, currency, AccountType.DEBIT);
        account.assignTo(profile);
        Account savedAccount = accountRepository.save(account);
        return toResponse(savedAccount);
    }

    private String generateAccountNumber() {
        int maxAttempts = 5;
        for (int i = 0; i <= maxAttempts; i++) {
            String candidate = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 1_000_000_000_000_000L));
            if (!accountRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate account number after " + maxAttempts + " attempts"
        );
    }

    private CustomerProfile resolveCustomerProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        CustomerProfile customerProfile = user.getProfile();
        if (customerProfile == null) {
            throw new CustomerProfileMissingException(userId);
        }
        return customerProfile;
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getCurrency().name(),
                account.getBalance(),
                account.getAccountStatus().name(),
                account.getCreatedAt()
        );
    }

    private LockedAccountPair lockPairForUpdate(String fromAccountNumber, String toAccountNumber) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new SelfTransferException(fromAccountNumber);
        }
        var sorted = Stream.of(fromAccountNumber, toAccountNumber)
                .sorted(Comparator.naturalOrder())
                .toList();
        Account firstAccount = accountRepository.findByAccountNumberForUpdate(sorted.get(0))
                .orElseThrow(() -> new AccountNotFoundException(sorted.get(0)));
        Account secondAccount = accountRepository.findByAccountNumberForUpdate(sorted.get(1))
                .orElseThrow(() -> new AccountNotFoundException(sorted.get(1)));
        Account fromAccount = fromAccountNumber.equals(firstAccount.getAccountNumber()) ? firstAccount : secondAccount;
        Account toAccount = toAccountNumber.equals(firstAccount.getAccountNumber()) ? firstAccount : secondAccount;
        if (fromAccount.getCurrency() != toAccount.getCurrency()) {
            throw new CurrencyMismatchException(
                    fromAccount.getAccountNumber(), toAccount.getAccountNumber(),
                    fromAccount.getCurrency(), toAccount.getCurrency()
            );
        }
        return new LockedAccountPair(fromAccount, toAccount);
    }

    private TransactionHistoryResponse toHistoryResponse(Transaction transaction, String viewerAccountNumber) {
        boolean isDebit = viewerAccountNumber.equals(transaction.getFromAccountNumber());
        String counterparty = isDebit ? transaction.getToAccountNumber() : transaction.getFromAccountNumber();
        String defaultDesc = isDebit
                ? "Chuuyển tiền đến " + counterparty
                : "Chuyển tiền từ " + counterparty;
        String description = transaction.getDescription() != null ? transaction.getDescription() : defaultDesc;

        return new TransactionHistoryResponse(
                transaction.getId(),
                transaction.getReferenceNumber(),
                transaction.getTransactionType(),
                transaction.getTransactionStatus(),
                transaction.getFraudStatus(),
                isDebit ? TransactionHistoryResponse.Direction.DEBIT : TransactionHistoryResponse.Direction.CREDIT,
                transaction.getAmount(),
                counterparty,
                description,
                transaction.getCreatedAt()
        );
    }
}