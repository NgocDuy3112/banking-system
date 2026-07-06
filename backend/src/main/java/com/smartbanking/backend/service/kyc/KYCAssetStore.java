package com.smartbanking.backend.service.kyc;

import com.smartbanking.backend.config.AppS3Properties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;


import java.io.IOException;
import java.util.UUID;


@Service
public class KYCAssetStore {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AppS3Properties minio;

    public enum AssetSlot {
        CCCD_FRONT("cccd-front"),
        CCCD_BACK("cccd-back"),
        SELFIE("selfie");

        private final String folder;

        AssetSlot(String folder) {
            this.folder = folder;
        }
    }

    public KYCAssetStore(
            S3Client s3Client,
            S3Presigner s3Presigner,
            AppS3Properties appS3Properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.minio = appS3Properties;
    }

    public String upload(
            AssetSlot slot,
            UUID customerProfileId,
            MultipartFile file
    ) throws IOException {
        String key = buildKey(slot, customerProfileId, file.getOriginalFilename());
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(minio.bucket())
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );
        return key;
    }

    public String presignedGetUrl(String key) {
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                request -> request
                        .signatureDuration(minio.presignedUrlTtl())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(minio.bucket())
                                .key(key)
                                .build()
                        )
        );
        return presigned.url().toString();
    }

    private String buildKey(AssetSlot slot, UUID customerProfileId, String fileName) {
        String ext = extractExtension(fileName);
        return "%s/%s/%s%s".formatted(
                customerProfileId, slot.folder, UUID.randomUUID(), ext
        );
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot);
    }
}