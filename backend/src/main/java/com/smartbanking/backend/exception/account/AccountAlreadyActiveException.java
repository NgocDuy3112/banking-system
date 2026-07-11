package com.smartbanking.backend.exception.account;

import com.smartbanking.backend.entity.account.AccountStatus;


public class AccountAlreadyActiveException extends RuntimeException {
    private final String accountNumber;
    private final AccountStatus accountStatus;

    public AccountAlreadyActiveException(String accountNumber, AccountStatus accountStatus) {
        super("Account " + accountNumber + " is already active");
        this.accountNumber = accountNumber;
        this.accountStatus = accountStatus;
    }

    public String getAccountNumber() {return this.accountNumber;}

    public AccountStatus getAccountStatus() {return this.accountStatus;}
}
