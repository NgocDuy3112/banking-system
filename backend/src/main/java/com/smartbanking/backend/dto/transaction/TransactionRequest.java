package com.smartbanking.backend.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank
        @Size(min = 9, max = 15, message = "Account number must have 9-15 digits")
        String fromAccountNumber,

        @NotBlank
        @Size(min = 9, max = 15, message = "Account number must have 9-15 digits")
        String toAccountNumber,

        @NotNull
        @DecimalMin(value = "0.0001", message = "Amount must be greater than 0")
        @Digits(integer = 20, fraction = 4, message = "Amount must have at most 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @NotBlank
        @Size(min = 6, max = 6, message = "OTP code must be exactly 6 digits")
        String otpCode
) {}