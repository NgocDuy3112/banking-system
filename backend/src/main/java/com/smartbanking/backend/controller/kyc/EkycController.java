package com.smartbanking.backend.controller.kyc;

import com.smartbanking.backend.service.kyc.EkycService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/customers/me")
public class EkycController {
    private final EkycService ekycService;

    public EkycController(EkycService ekycService) {
        this.ekycService = ekycService;
    }

    @PutMapping(path="/ekyc", consumes="multipart/form-data")
    public ResponseEntity<Void> submitEkyc(
            @AuthenticationPrincipal UUID userId,
            @RequestPart("cccdFront") MultipartFile cccdFront,
            @RequestPart("cccdBack") MultipartFile cccdBack,
            @RequestPart("selfie") MultipartFile selfie,
            @RequestParam("address") String address
    ) {
        ekycService.submit(userId, cccdFront, cccdBack, selfie, address);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}