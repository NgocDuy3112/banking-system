package com.smartbanking.backend.exception.kyc;

import com.smartbanking.backend.entity.kyc.KYCStatus;

public class EkycNotApprovedException extends RuntimeException {
  private final KYCStatus currentStatus;

  public EkycNotApprovedException(KYCStatus currentStatus) {
    super("KYC status must be APPROVED to open account, was: " + currentStatus);
    this.currentStatus = currentStatus;
  }

  public KYCStatus getCurrentStatus() {
    return currentStatus;
  }
}