package com.smartbanking.backend.controller.transaction;

import com.smartbanking.backend.dto.otp.OtpRequestResponse;
import com.smartbanking.backend.dto.transaction.TransactionRequest;
import com.smartbanking.backend.dto.transaction.TransactionResponse;
import com.smartbanking.backend.service.otp.OtpService;
import com.smartbanking.backend.service.transaction.TransactionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final OtpService otpService;
    private final TransactionService transactionService;

    @PostMapping("/otp")
    @ResponseStatus(HttpStatus.OK)
    public OtpRequestResponse requestOtp(@AuthenticationPrincipal UUID userId) {
        int expiresInSeconds = otpService.requestTransferOtp(userId);
        return new OtpRequestResponse(expiresInSeconds);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> requestTransfer(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.transfer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}