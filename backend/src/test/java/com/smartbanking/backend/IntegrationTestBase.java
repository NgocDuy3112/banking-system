package com.smartbanking.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;


@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class IntegrationTestBase {
    @ServiceConnection(name = "postgresql")
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withExposedPorts(5432);

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    protected static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest")
            .withExposedPorts(9000);

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void minioDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.minio.endpoint", MINIO::getS3URL);
        registry.add("app.minio.access-key", MINIO::getUserName);
        registry.add("app.minio.secret-key", MINIO::getPassword);
        registry.add("app.minio.region", () -> "us-east-1");
    }
}