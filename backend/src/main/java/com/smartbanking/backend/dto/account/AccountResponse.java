package com.smartbanking.backend.dto.account;

import com.smartbanking.backend.entity.account.*;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
    String accountNumber,
    String accountType,
    String currency,
    BigDecimal balance,
    String status,
    Instant createdAt
) {}