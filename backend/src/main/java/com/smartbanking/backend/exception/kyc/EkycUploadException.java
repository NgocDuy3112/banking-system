package com.smartbanking.backend.exception.kyc;

import com.smartbanking.backend.service.kyc.KYCAssetStore.AssetSlot;


public class EkycUploadException extends RuntimeException {
    private final AssetSlot assetSlot;

    public EkycUploadException(AssetSlot assetSlot, Throwable cause) {
        super("Failed to upload KYC asset " + assetSlot.toString(), cause);
        this.assetSlot = assetSlot;
    }

    public AssetSlot getAssetSlot() {
        return assetSlot;
    }
}