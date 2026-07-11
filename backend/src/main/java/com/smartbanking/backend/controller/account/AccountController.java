package com.smartbanking.backend.controller.account;

import com.smartbanking.backend.dto.account.AccountResponse;
import com.smartbanking.backend.dto.account.OpenAccountRequest;
import com.smartbanking.backend.dto.transaction.TransactionHistoryResponse;
import com.smartbanking.backend.entity.account.Currency;
import com.smartbanking.backend.entity.transaction.TransactionType;
import com.smartbanking.backend.service.account.AccountService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
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

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(
            @AuthenticationPrincipal UUID userId,
            @RequestBody(required = false) OpenAccountRequest openAccountRequest
    ) {
        Currency currency = openAccountRequest != null ? openAccountRequest.currency() : null;
        AccountResponse response = accountService.openAccount(userId, currency);
        URI location = URI.create("/api/v1/accounts/" + response.accountNumber());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("accountNumber") String accountNumber
    ) {
        return accountService.getAccount(userId, accountNumber);
    }

    @PatchMapping("/{accountNumber}/lock")
    public AccountResponse lockAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("accountNumber") String accountNumber
    ) {
        return accountService.lockAccount(userId, accountNumber);
    }

    @PatchMapping("/{accountNumber}/unlock")
    public AccountResponse unlockAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("accountNumber") String accountNumber
    ) {
        return accountService.unlockAccount(userId, accountNumber);
    }

    @PatchMapping("/{accountNumber}/close")
    public AccountResponse closeAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("accountNumber") String accountNumber
    ) {
        return accountService.closeAccount(userId, accountNumber);
    }

    @GetMapping("/{accountNumber}/transactions")
    public Page<TransactionHistoryResponse> getAccountTransactions(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(required = false) TransactionType transactionType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return accountService.listAccountTransactions(userId, accountNumber, fromDate, toDate, transactionType, pageable);
    }
}