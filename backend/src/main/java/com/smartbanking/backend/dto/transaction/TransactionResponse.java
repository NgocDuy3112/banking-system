package com.smartbanking.backend.dto.transaction;

import com.smartbanking.backend.entity.transaction.TransactionStatus;
import com.smartbanking.backend.entity.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String referenceNumber,
        TransactionStatus transactionStatus,
        TransactionType transactionType,
        BigDecimal fromBalanceBefore,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceBefore,
        BigDecimal toBalanceAfter,
        Instant createdAt
) {
}