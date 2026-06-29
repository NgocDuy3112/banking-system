package com.smartbanking.backend.exception.account;

import com.smartbanking.backend.entity.account.AccountStatus;


public class AccountNotActiveException extends RuntimeException {
    private final String accountNumber;
    private final AccountStatus accountStatus;

    public AccountNotActiveException(String accountNumber, AccountStatus accountStatus) {
        super("Account number " + accountNumber + " is not active. (status: " + accountStatus + ")");
        this.accountNumber = accountNumber;
        this.accountStatus = accountStatus;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }
}
