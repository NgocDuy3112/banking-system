package com.smartbanking.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {
    @Bean
    public S3Client s3Client(AppS3Properties appS3Properties) {
        S3ClientBuilder s3ClientBuilder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(appS3Properties.accessKey(), appS3Properties.secretKey())
                ));
        if (appS3Properties.provider() == S3Provider.AWS) {
            s3ClientBuilder.region(Region.of(appS3Properties.region()));
        } else {
            s3ClientBuilder.endpointOverride(URI.create(appS3Properties.endpoint()))
                    .region(Region.of(appS3Properties.region()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return s3ClientBuilder.build();
    }

    @Bean
    public S3Presigner s3Presigner(AppS3Properties appS3Properties) {
        S3Presigner.Builder s3PresignerBuilder = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(appS3Properties.accessKey(), appS3Properties.secretKey())
                ));
        if (appS3Properties.provider() == S3Provider.AWS) {
            s3PresignerBuilder.region(Region.of(appS3Properties.region()));
        } else {
            s3PresignerBuilder.endpointOverride(URI.create(appS3Properties.endpoint()))
                    .region(Region.of(appS3Properties.region()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return s3PresignerBuilder.build();
    }
}