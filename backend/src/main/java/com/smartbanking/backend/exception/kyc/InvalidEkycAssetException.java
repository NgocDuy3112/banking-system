package com.smartbanking.backend.exception.kyc;

public class InvalidEkycAssetException extends RuntimeException {
    public InvalidEkycAssetException(String message) {
        super("Invalid eKYC Asset: " + message);
    }
}