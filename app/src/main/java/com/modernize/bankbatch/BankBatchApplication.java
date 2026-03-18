package com.modernize.bankbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankBatchApplication.class, args);
    }
}
