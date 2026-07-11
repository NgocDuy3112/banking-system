package com.smartbanking.backend.service.otp;

import com.smartbanking.backend.entity.user.Role;
import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.exception.otp.OtpExpiredException;
import com.smartbanking.backend.exception.otp.OtpInvalidException;
import com.smartbanking.backend.exception.otp.OtpLockedException;
import com.smartbanking.backend.repository.auth.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class OtpServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserRepository userRepository;
    @Mock OtpDelivery otpDelivery;

    @InjectMocks OtpService otpService;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    private UUID userId;
    private User user;
    private String otpKey;
    private String attemptsKey;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User(
                "test@example.com",
                "0975097207",
                "Password123!",
                Role.CUSTOMER
        );
        ReflectionTestUtils.setField(otpService, "otpTtl", OTP_TTL);
        ReflectionTestUtils.setField(otpService, "maxAttempts", MAX_ATTEMPTS);

        otpKey = "otp:" + userId + ":TRANSFER";
        attemptsKey = "otp:" + userId + ":TRANSFER:ATTEMPTS";
    }

    @Test
    void requestTransferOtp_storesHashInRedisWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hashedOtp");

        int expiresIn = otpService.requestTransferOtp(userId);

        assertThat(expiresIn).isEqualTo(300);
        verify(valueOps).set(eq(otpKey), eq("hashedOtp"), eq(OTP_TTL));
        verify(otpDelivery).send(eq("test@example.com"), anyString());
    }

    @Test
    void requestTransferOtp_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.requestTransferOtp(userId))
                .isInstanceOf(UserNotFoundException.class);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void verifyTransferOtp_correctCode_consumesOtpAndClearsAttempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(attemptsKey)).thenReturn(null);
        when(valueOps.get(otpKey)).thenReturn("hashedOtp");
        when(passwordEncoder.matches("123456", "hashedOtp")).thenReturn(true);

        otpService.verifyTransferOtp(userId, "123456");

        verify(redis).delete(otpKey);
        verify(redis).delete(attemptsKey);
    }

    @Test
    void verifyTransferOtp_wrongCode_incrementsAttemptsCounter() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(otpKey)).thenReturn("hashedOtp");
        when(valueOps.get(attemptsKey)).thenReturn(null);  // not locked
        when(passwordEncoder.matches("wrong", "hashedOtp")).thenReturn(false);
        when(valueOps.increment(attemptsKey)).thenReturn(1L);

        assertThatThrownBy(() -> otpService.verifyTransferOtp(userId, "wrong"))
                .isInstanceOf(OtpInvalidException.class);

        verify(valueOps).increment(attemptsKey);
        verify(redis).expire(eq(attemptsKey), eq(OTP_TTL));
        verify(redis, never()).delete(otpKey);  // OTP not yet invalidated
    }

    @Test
    void verifyTransferOtp_wrongCodeMultipleTimes_locksAfter5Attempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(otpKey)).thenReturn("hashedOtp");
        when(valueOps.get(attemptsKey)).thenReturn("4");
        when(passwordEncoder.matches("wrong", "hashedOtp")).thenReturn(false);
        when(valueOps.increment(attemptsKey)).thenReturn(5L);

        assertThatThrownBy(() -> otpService.verifyTransferOtp(userId, "wrong"))
                .isInstanceOf(OtpLockedException.class)
                .hasFieldOrPropertyWithValue("userId", userId)
                .hasFieldOrPropertyWithValue("maxAttempts", MAX_ATTEMPTS);

        verify(redis).delete(otpKey);
    }

    @Test
    void verifyTransferOtp_alreadyLocked_throwsOtpLockedException() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(attemptsKey)).thenReturn("5");

        assertThatThrownBy(() -> otpService.verifyTransferOtp(userId, "123456"))
                .isInstanceOf(OtpLockedException.class);

        verify(valueOps, never()).get(otpKey);  // short-circuit, don't even check OTP
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void verifyTransferOtp_expiredOtp_throwsOtpExpiredException() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(attemptsKey)).thenReturn(null);  // not locked
        when(valueOps.get(otpKey)).thenReturn(null);  // expired

        assertThatThrownBy(() -> otpService.verifyTransferOtp(userId, "123456"))
                .isInstanceOf(OtpExpiredException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void verifyTransferOtp_wrongThenCorrect_succeedsAndClearsCounter() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(otpKey)).thenReturn("hashedOtp");
        when(valueOps.get(attemptsKey)).thenReturn(null);
        when(passwordEncoder.matches("wrong", "hashedOtp")).thenReturn(false);
        when(valueOps.increment(attemptsKey)).thenReturn(1L);

        assertThatThrownBy(() -> otpService.verifyTransferOtp(userId, "wrong"))
                .isInstanceOf(OtpInvalidException.class);

        when(valueOps.get(attemptsKey)).thenReturn("1");
        when(passwordEncoder.matches("123456", "hashedOtp")).thenReturn(true);

        otpService.verifyTransferOtp(userId, "123456");

        verify(redis).delete(otpKey);
        verify(redis).delete(attemptsKey);
    }
}