package com.ledgersaas.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LedgerSaaSApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerSaaSApplication.class, args);
    }
}
