package com.smartbanking.backend.exception.transaction;

import com.smartbanking.backend.entity.account.Currency;

public class CurrencyMismatchException extends RuntimeException {
    private final String fromAccountNumber;
    private final String toAccountNumber;
    private final Currency fromCurrency;
    private final Currency toCurrency;

    public CurrencyMismatchException(
            String fromAccountNumber,
            String toAccountNumber,
            Currency fromCurrency,
            Currency toCurrency
    ) {
      super(String.format(
              "Currency mismatch: from=%s (%s) to=%s (%s)",
              fromAccountNumber, fromCurrency.name(),
              toAccountNumber, toCurrency.name()
      ));
        this.fromAccountNumber = fromAccountNumber;
        this.toAccountNumber = toAccountNumber;
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
    }

    public String getFromAccountNumber() {
        return this.fromAccountNumber;
    }
    public String getToAccountNumber() {
        return this.toAccountNumber;
    }

    public Currency getFromCurrency() {
        return this.fromCurrency;
    }

    public Currency getToCurrency() {
        return this.toCurrency;
    }
}