package com.smartbanking.backend.exception.account;

public class AccountNotFoundException extends RuntimeException {
    private final String accountNumber;

    public AccountNotFoundException(String accountNumber) {
        super("Account " + accountNumber + " not found");
        this.accountNumber = accountNumber;
    }

    public String getAccountNumber() {return this.accountNumber;}
}