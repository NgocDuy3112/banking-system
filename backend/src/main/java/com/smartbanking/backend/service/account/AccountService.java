package com.smartbanking.backend.service.account;

import com.smartbanking.backend.dto.account.AccountResponse;

import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.user.User;

import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;
import com.smartbanking.backend.exception.transaction.CurrencyMismatchException;
import com.smartbanking.backend.exception.transaction.SelfTransferException;

import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.auth.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;


@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    private record LockedAccountPair(Account fromAccount, Account toAccount) {}

    public AccountService(
            UserRepository userRepository,
            AccountRepository accountRepository
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

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
                .findByAccountNumberAndCustomerProfileId(accountNumber, profile.getId())
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
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
        return  new LockedAccountPair(fromAccount, toAccount);
    }
}