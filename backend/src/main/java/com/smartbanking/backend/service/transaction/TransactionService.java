package com.smartbanking.backend.service.transaction;

import com.smartbanking.backend.dto.transaction.TransactionRequest;
import com.smartbanking.backend.dto.transaction.TransactionResponse;
import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.transaction.FraudStatus;
import com.smartbanking.backend.entity.transaction.Transaction;
import com.smartbanking.backend.entity.transaction.TransactionStatus;
import com.smartbanking.backend.entity.transaction.TransactionType;
import com.smartbanking.backend.exception.account.AccountNotFoundException;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;
import com.smartbanking.backend.repository.account.AccountRepository;
import com.smartbanking.backend.repository.profile.CustomerProfileRepository;
import com.smartbanking.backend.repository.transaction.TransactionRepository;
import com.smartbanking.backend.service.account.AccountService;
import com.smartbanking.backend.service.account.BalanceUpdateResult;
import com.smartbanking.backend.service.otp.OtpService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private static final BigDecimal FEE = BigDecimal.ZERO;

    private final AccountRepository accountRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final TransactionRepository transactionRepository;
    private final OtpService otpService;
    private final AccountService accountService;

    @Transactional
    public TransactionResponse transfer(UUID userId, TransactionRequest request) {
        otpService.verifyTransferOtp(userId, request.otpCode());
        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerProfileMissingException(userId));
        accountRepository.findByCustomerProfileIdAndAccountNumber(
                profile.getId(), request.fromAccountNumber()
        ).orElseThrow(() -> new AccountNotFoundException(request.fromAccountNumber()));

        BalanceUpdateResult result = accountService.updateBalance(
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount()
        );
        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                generateReferenceNumber(),
                result.fromAccountNumber(),
                result.toAccountNumber(),
                request.amount(),
                FEE,
                TransactionType.INTERNAL,
                TransactionStatus.SUCCESS,
                null, // We don't integrate the ML service yet
                FraudStatus.CLEAR,
                result.fromBalanceBefore(),
                result.fromBalanceAfter(),
                result.toBalanceBefore(),
                result.toBalanceAfter(),
                request.description()
        );
        transactionRepository.save(transaction);
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceNumber(),
                transaction.getTransactionType(),
                transaction.getTransactionStatus(),
                result.fromBalanceBefore(),
                result.fromBalanceAfter(),
                result.toBalanceBefore(),
                result.toBalanceAfter(),
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