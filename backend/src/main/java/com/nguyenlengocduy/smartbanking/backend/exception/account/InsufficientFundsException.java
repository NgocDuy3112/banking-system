package com.nguyenlengocduy.smartbanking.backend.exception.account;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    private final String accountNumber;
    private final BigDecimal balance;
    private final BigDecimal amount;

    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal amount) {
        super("Insufficient funds for account " + accountNumber + ": balance=" + balance + ", requested=" + amount);
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.amount = amount;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
