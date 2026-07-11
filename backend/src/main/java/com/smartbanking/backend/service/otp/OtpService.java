package com.smartbanking.backend.service.otp;

import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.exception.otp.OtpExpiredException;
import com.smartbanking.backend.exception.otp.OtpInvalidException;
import com.smartbanking.backend.exception.otp.OtpLockedException;
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
    private static final String OTP_ATTEMPTS_SUFFIX = ":ATTEMPTS";
    private static final int OTP_CODE_LENGTH = 6;

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final OtpDelivery otpDelivery;

    @Value("${app.otp.ttl:PT5M}")
    private Duration otpTtl;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

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
        String attemptsKey = buildAttemptsKey(userId);

        String attemptsStr = redis.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        if (attempts >= maxAttempts) {
            log.warn("OTP locked for user {} after {} failed attempts", userId, attempts);
            throw new OtpLockedException(userId, maxAttempts);
        }

        String storedHash = redis.opsForValue().get(key);
        if (storedHash == null) {
            log.warn("OTP code not found for user {}", userId);
            throw new OtpExpiredException(userId);
        }

        if (!passwordEncoder.matches(otpCode, storedHash)) {
            Long newCount = redis.opsForValue().increment(attemptsKey);
            if (newCount != null) {
                redis.expire(attemptsKey, otpTtl);
            }
            if (newCount != null && newCount >= maxAttempts) {
                redis.delete(key);
                log.warn("OTP locked for user {} after {} failed attempts", userId, newCount);
                throw new OtpLockedException(userId, maxAttempts);
            }
            log.warn("OTP code mismatch for user {} (attempt {}/{})", userId, newCount, maxAttempts);
            throw new OtpInvalidException(userId);
        }

        redis.delete(key);
        redis.delete(attemptsKey);
        log.info("OTP verified and consumed for user {}", userId);
    }

    private String buildAttemptsKey(UUID userId) {
        return OTP_KEY_PREFIX + userId + OTP_KEY_SUFFIX + OTP_ATTEMPTS_SUFFIX;
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