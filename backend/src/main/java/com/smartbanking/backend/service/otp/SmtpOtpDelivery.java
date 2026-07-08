package com.smartbanking.backend.service.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpOtpDelivery implements OtpDelivery {
    private final JavaMailSender mailSender;

    @Override
    public void send(String toEmail, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Smart banking -Mã OTP Xác thực");
        message.setText("""
                Vui lòng không tiết lộ mã xác thực này.
                
                Mã xác thực của giao dịch là: %s
                """.formatted(otpCode));
        mailSender.send(message);
        log.info("OTP email sent to {} (code prefix: {})", toEmail, otpCode.substring(0, 2));
    }
}