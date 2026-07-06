package com.smartbanking.backend.service.kyc;

import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.exception.kyc.InvalidEkycAssetException;
import com.smartbanking.backend.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class EkycServiceValidationTest {
    @Mock UserRepository userRepository;
    @Mock KYCAssetStore kycAssetStore;
    @InjectMocks EkycService ekycService;

    private static final String VALID_ADDRESS = "123 Le Loi, Quan 1, TP.HCM";
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};

    @Test
    void submit_validJpegAndPng_advancesToUserLookup() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                createJpegOfSize(20_000),
                createJpegOfSize(20_000),
                createPngOfSize(20_000),
                VALID_ADDRESS
        )).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void submit_webpContentType_rejectedAsInvalidAsset() {
        MultipartFile webp = new MockMultipartFile(
                "cccdFront",
                "front.webp",
                "image/webp",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        );
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                webp,
                createJpegOfSize(20_000),
                createPngOfSize(20_000),
                VALID_ADDRESS
        )).isInstanceOf(InvalidEkycAssetException.class)
                .hasMessageContaining("image/jpeg or image/png");
    }

    @Test
    void submit_jpedClaimButPlainText_rejectedAsInvalidAsset() {
        byte[] body = new byte[20_000];
        byte[] text = "THIS IS NOT A JPEG FILE".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(text, 0, body, 0, text.length);
        MultipartFile plainText = new MockMultipartFile(
                "cccdFront",
                "front.jpg",
                "image/jpeg",
                body
        );
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                plainText,
                createJpegOfSize(20_000),
                createPngOfSize(20_000),
                VALID_ADDRESS
        )).isInstanceOf(InvalidEkycAssetException.class)
                .hasMessageContaining("does not match declared content-type");
    }

    @Test
    void submit_cccdFrontTooLarge_rejectedAsInvalidAsset() {
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                createJpegOfSize(20_000),
                createJpegOfSize(6_000_000),
                createPngOfSize(20_000),
                VALID_ADDRESS
        )).isInstanceOf(InvalidEkycAssetException.class)
                .hasMessageContaining("is too large");
    }

    @Test
    void submit_selfieTooLarge_rejectedAsInvalidAsset() {
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                createJpegOfSize(20_000),
                createJpegOfSize(20_000),
                createPngOfSize(6_000_000),
                VALID_ADDRESS
        )).isInstanceOf(InvalidEkycAssetException.class)
                .hasMessageContaining("is too large");
    }

    @Test
    void submit_blankAddress_rejectedAsInvalidAsset() {
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                createJpegOfSize(20_000),
                createJpegOfSize(20_000),
                createPngOfSize(20_000),
                ""
        )).isInstanceOf(InvalidEkycAssetException.class)
                .hasMessageContaining("can not be blank");
    }

    @Test
    void submit_addressTooLong_rejectedAsInvalidAsset() {
        String longAddress = "a".repeat(501);
        assertThatThrownBy(() -> ekycService.submit(
                UUID.randomUUID(),
                createJpegOfSize(20_000),
                createJpegOfSize(20_000),
                createPngOfSize(20_000),
                longAddress
        )).isInstanceOf(InvalidEkycAssetException.class)
                .hasMessageContaining("at most 500 characters");
    }

    private static MultipartFile createJpegOfSize(int bytes) {
        byte[] body = new byte[bytes];
        if (bytes >= 3) {
            body[0] = (byte) 0xFF;
            body[1] = (byte) 0xD8;
            body[2] = (byte) 0xFF;
        }
        return new MockMultipartFile(
                "cccdFront", "front.jpg", "image/jpeg", body
        );
    }

    private static MultipartFile createPngOfSize(int bytes) {
        byte[] body = new byte[bytes];
        if (bytes >= 4) {
            body[0] = (byte) 0x89;
            body[1] = (byte) 0x50;
            body[2] = (byte) 0x4E;
            body[3] = (byte) 0x47;
        }
        return new MockMultipartFile(
                "cccdFront", "front.png", "image/png", body
        );
    }
}