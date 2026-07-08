package com.smartbanking.backend.exception.transaction;

public class TransactionNotFoundException extends RuntimeException {
    private final String referenceNumber;

    public TransactionNotFoundException(String referenceNumber) {
        super("Transaction not found: " + referenceNumber);
        this.referenceNumber = referenceNumber;
    }

    public String getReferenceNumber() {
        return this.referenceNumber;
    }
}