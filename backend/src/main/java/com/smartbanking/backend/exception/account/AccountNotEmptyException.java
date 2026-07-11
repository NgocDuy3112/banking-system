package com.smartbanking.backend.exception.account;

import java.math.BigDecimal;

public class AccountNotEmptyException extends RuntimeException {
    private final String accountNumber;
    private final BigDecimal balance;

    public AccountNotEmptyException(String accountNumber, BigDecimal balance) {
        super("Account " + accountNumber + " has non-zero balance " + balance + ". Must empty the balance before closing");
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    public String getAccountNumber() {
        return this.accountNumber;
    }

    public BigDecimal getBalance() {
        return this.balance;
    }
}