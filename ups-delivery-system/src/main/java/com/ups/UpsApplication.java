package com.ups;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Enable scheduling for background tasks
public class UpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(UpsApplication.class, args);
    }
}