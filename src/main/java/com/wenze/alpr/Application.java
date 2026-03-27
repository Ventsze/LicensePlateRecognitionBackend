package com.wenze.alpr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.wenze.alpr")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}