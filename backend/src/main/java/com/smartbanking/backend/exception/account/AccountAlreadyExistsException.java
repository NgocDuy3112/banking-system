package com.smartbanking.backend.exception.account;

public class AccountAlreadyExistsException extends RuntimeException {
    private final String accountNumber;

    public AccountAlreadyExistsException(String accountNumber) {
        super("Customer already has an account: " + accountNumber);
        this.accountNumber = accountNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}