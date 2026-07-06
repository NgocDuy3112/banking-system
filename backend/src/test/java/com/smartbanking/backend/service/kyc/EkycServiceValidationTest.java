package com.smartbanking.backend.service.ekyc;

import com.smartbanking.backend.exception.kyc.InvalidEkycAssetException;
import com.smartbanking.backend.repository.auth.UserRepository;
import com.smartbanking.backend.service.kyc.EkycService;
import com.smartbanking.backend.service.kyc.KYCAssetStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;


@ExtendWith(MockitoExtension.class)
public class EkycServiceValidationTest {
    @Mock UserRepository userRepository;
    @Mock KYCAssetStore kycAssetStore;
    @InjectMocks EkycService ekycService;
}