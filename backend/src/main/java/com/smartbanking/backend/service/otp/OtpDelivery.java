package com.smartbanking.backend.service.otp;

public interface OtpDelivery {
    void send(String toEmail, String otpCode);
}