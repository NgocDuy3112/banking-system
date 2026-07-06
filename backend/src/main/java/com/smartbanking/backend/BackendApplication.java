package com.smartbanking.backend;

import com.smartbanking.backend.config.AppAuthProperties;
import com.smartbanking.backend.config.AppS3Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;


@SpringBootApplication
@EnableConfigurationProperties({
        AppAuthProperties.class,
        AppS3Properties.class
})
public class BackendApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BackendApplication.class, args);
    }

}