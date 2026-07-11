package com.smartbanking.backend.dto.account;

import com.smartbanking.backend.entity.account.Currency;

public record OpenAccountRequest(Currency currency) {
}