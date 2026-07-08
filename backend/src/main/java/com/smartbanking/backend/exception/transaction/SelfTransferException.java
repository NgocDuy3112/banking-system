package com.smartbanking.backend.exception.transaction;

public class SelfTransferException extends RuntimeException {
    private final String accountNumber;

    public SelfTransferException(String accountNumber) {
        super("Cannot transfer to the same account: " + accountNumber);
        this.accountNumber = accountNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}