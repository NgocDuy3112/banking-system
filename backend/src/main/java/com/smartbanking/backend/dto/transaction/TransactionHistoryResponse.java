package com.smartbanking.backend.dto.transaction;

import com.smartbanking.backend.entity.transaction.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID transactionId,
        String referenceNumber,
        TransactionType transactionType,
        TransactionStatus transactionStatus,
        FraudStatus fraudStatus,
        Direction direction,
        BigDecimal amount,
        String counterpartyAccountNumber,
        String description,
        Instant createdAt
) {
    public enum Direction {DEBIT, CREDIT}
}