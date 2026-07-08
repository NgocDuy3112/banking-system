package com.smartbanking.backend.service.transaction;

import com.smartbanking.backend.dto.transaction.TransactionRequest;
import com.smartbanking.backend.dto.transaction.TransactionResponse;
import com.smartbanking.backend.entity.account.Account;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.transaction.FraudStatus;
import com.smartbanking.backend.entity.transaction.Transaction;
import com.smartbanking.backend.entity.transaction.TransactionStatus;
import com.smartbanking.backend.entity.transaction.TransactionType;
import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;
import com.smartbanking.backend.exception.transaction.CurrencyMismatchException;
import com.smartbanking.backend.exception.transaction.SelfTransferException;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import com.smartbanking.backend.repository.transaction.TransactionRepository;
import com.smartbanking.backend.service.otp.OtpService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private static final BigDecimal FEE = BigDecimal.ZERO;

    private final AccountRepository accountRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final TransactionRepository transactionRepository;
    private final OtpService otpService;

    @Transactional
    public TransactionResponse transfer(UUID userId, TransactionRequest request) {
        otpService.verifyTransferOtp(userId, request.otpCode());
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerProfileMissingException(userId));
        Account fromAccount = accountRepository
                .findByAccountNumberAndCustomerProfileId(request.fromAccountNumber(), profile.getId())
                .orElseThrow(() -> new AccountNotFoundException(request.fromAccountNumber()));

        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new SelfTransferException(request.fromAccountNumber());
        }
        List<String> sortedAccountNumbers = Stream.of(request.fromAccountNumber(), request.toAccountNumber())
                .sorted(Comparator.naturalOrder())
                .toList();
        Account firstAccount = accountRepository.findByAccountNumberForUpdate(sortedAccountNumbers.get(0))
                .orElseThrow(() -> new AccountNotFoundException(sortedAccountNumbers.get(0)));
        Account secondAccount = accountRepository.findByAccountNumberForUpdate(sortedAccountNumbers.get(1))
                .orElseThrow(() -> new AccountNotFoundException(sortedAccountNumbers.get(1)));

        Account fromLocked = fromAccount.getAccountNumber().equals(firstAccount.getAccountNumber()) ? firstAccount : secondAccount;
        Account toLocked = request.toAccountNumber().equals(firstAccount.getAccountNumber()) ? firstAccount : secondAccount;

        if (fromLocked.getCurrency() != toLocked.getCurrency()) {
            throw new CurrencyMismatchException(
                    fromLocked.getAccountNumber(),
                    toLocked.getAccountNumber(),
                    fromLocked.getCurrency(),
                    toLocked.getCurrency()
            );
        }

        BigDecimal fromBeforeBalance = fromLocked.getBalance();
        BigDecimal toBeforeBalance = toLocked.getBalance();

        fromLocked.debit(request.amount());
        toLocked.credit(request.amount());

        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                generateReferenceNumber(),
                fromLocked.getAccountNumber(),
                toLocked.getAccountNumber(),
                request.amount(),
                FEE,
                TransactionType.INTERNAL,
                TransactionStatus.SUCCESS,
                null, // We don't integrate the fraud score service yet
                FraudStatus.CLEAR,
                fromBeforeBalance,
                fromLocked.getBalance(),
                toBeforeBalance,
                toLocked.getBalance(),
                request.description()
        );
        transactionRepository.save(transaction);
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceNumber(),
                transaction.getTransactionStatus(),
                transaction.getTransactionType(),
                fromBeforeBalance,
                fromLocked.getBalance(),
                toBeforeBalance,
                toLocked.getBalance(),
                transaction.getCreatedAt()
        );
    }

    private String generateReferenceNumber() {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = String.format("%06X", ThreadLocalRandom.current().nextInt(0xFFFFFF + 1));
        return "TXN-" + timestamp + "-" + suffix;
    }
}