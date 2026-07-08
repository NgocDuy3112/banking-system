package com.smartbanking.backend.service.account;

import java.math.BigDecimal;

public record BalanceUpdateResult(
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal fromBalanceBefore,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceBefore,
        BigDecimal toBalanceAfter
) {
}