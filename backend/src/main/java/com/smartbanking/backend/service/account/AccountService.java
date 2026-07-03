package com.smartbanking.backend.service.account;

import com.smartbanking.backend.dto.account.AccountResponse;

import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.user.User;

import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;

import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.auth.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

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


}