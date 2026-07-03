package com.smartbanking.backend.controller.account;

import com.smartbanking.backend.dto.account.AccountResponse;
import com.smartbanking.backend.service.account.AccountService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> listAccounts(
            @AuthenticationPrincipal UUID userId
    ) {
        return accountService.listAccounts(userId);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("accountNumber") String accountNumber
    ) {
        return accountService.getAccount(userId, accountNumber);
    }
}