package com.smartbanking.backend.exception.kyc;

public class InvalidEkycAssetExcpetion extends RuntimeException {
  public InvalidEkycAssetExcpetion(String message) {
    super(message);
  }
}