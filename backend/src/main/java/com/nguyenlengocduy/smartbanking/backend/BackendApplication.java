package com.nguyenlengocduy.smartbanking.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplica

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
    public class Account {
        private long AccountNumber;
        private String AccountName;
        private double Balance;
        Scanner sc = new Scanner(System.in);
    }
}
