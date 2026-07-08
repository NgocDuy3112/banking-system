package com.smartbanking.backend.service.otp;

import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.exception.otp.OtpExpiredException;
import com.smartbanking.backend.exception.otp.OtpInvalidException;
import com.smartbanking.backend.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {
    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_KEY_SUFFIX = ":TRANSFER";
    private static final int OTP_CODE_LENGTH = 6;

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OtpDelivery otpDelivery;

    @Value("${app.otp.ttl:PT5M}")
    private Duration otpTtl;

    public int requestTransferOtp(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        String otpCode = generateOtpCode();
        String hash = passwordEncoder.encode(otpCode);
        String key = buildKey(userId);

        redis.opsForValue().set(key, hash, otpTtl);
        log.info("OTP generated for user {} (TTL: {})", userId, otpTtl);

        otpDelivery.send(user.getEmail(), otpCode);
        return (int) otpTtl.toSeconds();
    }

    public void verifyTransferOtp(UUID userId, String otpCode) {
        String key = buildKey(userId);
        String storedHash = redis.opsForValue().get(key);
        if (storedHash == null) {
            log.warn("OTP code not found for user {}", userId);
            throw new OtpExpiredException(userId);
        }
        if (!passwordEncoder.matches(otpCode, storedHash)) {
            log.warn("OTP code mismatch for user {}", userId);
            throw new OtpInvalidException(userId);
        }
        redis.delete(key);
        log.info("OTP verified and consumed for user {}", userId);
    }

    private String buildKey(UUID userId) {
        return OTP_KEY_PREFIX + userId + OTP_KEY_SUFFIX;
    }

    private String generateOtpCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder stringBuilder = new StringBuilder(OTP_CODE_LENGTH);
        for (int i = 0; i < OTP_CODE_LENGTH; i++) {
            stringBuilder.append(random.nextInt(10));
        }
        return stringBuilder.toString();
    }
}