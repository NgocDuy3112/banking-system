package com.smartbanking.backend.service.kyc;

import com.smartbanking.backend.entity.profile.CustomerProfile;
import com.smartbanking.backend.entity.user.User;
import com.smartbanking.backend.exception.auth.CustomerProfileMissingException;
import com.smartbanking.backend.exception.auth.UserNotFoundException;
import com.smartbanking.backend.exception.kyc.EkycUploadException;
import com.smartbanking.backend.exception.kyc.InvalidEkycAssetException;
import com.smartbanking.backend.repository.auth.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;


@Service
public class EkycService {
    private final UserRepository userRepository;
    private final KYCAssetStore kycAssetStore;

    private static final long MAX_CCCD_BYTES = 5L *  1024 * 1024;
    private static final long MAX_SELFIE_BYTES = 2L *  1024 * 1024;
    private static final long MIN_ASSET_BYTES = 10L * 1024;
    private static final int MAX_ADDRESS_LENGTH = 500;
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};

    public EkycService(UserRepository userRepository, KYCAssetStore kycAssetStore) {
        this.userRepository = userRepository;
        this.kycAssetStore = kycAssetStore;
    }

    @Transactional
    public void submit(
            UUID userId,
            MultipartFile cccdFront,
            MultipartFile cccdBack,
            MultipartFile selfie,
            String address
    ) {
        validateAddress(address);
        validateAsset("cccdFront", cccdFront, MAX_CCCD_BYTES);
        validateAsset("cccdBack", cccdBack, MAX_CCCD_BYTES);
        validateAsset("selfie", selfie, MAX_SELFIE_BYTES);
        CustomerProfile customerProfile = getCustomerProfile(userId);
        UUID profileId = customerProfile.getId();
        String cccdFrontKey = uploadOrThrow(KYCAssetStore.AssetSlot.CCCD_FRONT, profileId, cccdFront);
        String cccdBackKey = uploadOrThrow(KYCAssetStore.AssetSlot.CCCD_BACK, profileId, cccdBack);
        String selfieKey = uploadOrThrow(KYCAssetStore.AssetSlot.SELFIE, profileId, selfie);
        customerProfile.submitEkyc(cccdFrontKey, cccdBackKey, selfieKey, address);

    }

    private String uploadOrThrow(KYCAssetStore.AssetSlot assetSlot, UUID profileId, MultipartFile file)  {
        try {
            return kycAssetStore.upload(assetSlot, profileId, file);
        } catch (IOException e) {
            throw new EkycUploadException(assetSlot, e);
        }
    }

    private CustomerProfile getCustomerProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException(userId)
        );
        CustomerProfile customerProfile = user.getProfile();
        if (customerProfile == null) {
            throw new CustomerProfileMissingException(userId);
        }
        return customerProfile;
    }

    private void validateAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new InvalidEkycAssetException("Address can not be blank");
        }
        if (address.length() > MAX_ADDRESS_LENGTH) {
            throw new InvalidEkycAssetException(
                    "Address must be at most " + MAX_ADDRESS_LENGTH + " characters, got: " + address.length()
            );
        }
    }

    private void validateAsset(String name, MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new InvalidEkycAssetException(name + " must not be empty");
        }

        String contentType = file.getContentType();
        if (!isAllowedContentType(contentType)) {
            throw new InvalidEkycAssetException(
                    name + " must be image/jpeg or image/png, got: " + contentType
            );
        }

        long size = file.getSize();
        if (size < MIN_ASSET_BYTES) {
            throw new InvalidEkycAssetException(
                    name + " is too small: " + size + " bytes (min " + MIN_ASSET_BYTES + ")"
            );
        }
        if (size > maxBytes) {
            throw new InvalidEkycAssetException(
                    name + " is too large: " + size + " bytes (max " + maxBytes + ")"
            );
        }

        if (!hasValidMagicNumber(file)) {
            throw new InvalidEkycAssetException(
                    name + " content does not match declared content-type: " + contentType
            );
        }
    }

    private boolean hasValidMagicNumber(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(8);
            return startsWith(head, JPEG_MAGIC) || startsWith(head, PNG_MAGIC);
        } catch (IOException e) {
            throw new InvalidEkycAssetException(
                    "failed to read " + file.getName() + " for validation: " + e.getMessage()
            );
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private boolean isAllowedContentType(String contentType) {
        return "image/jpeg".equals(contentType) || "image/png".equals(contentType);
    }
}