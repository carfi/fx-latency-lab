package com.fx.lab.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FxLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(FxLabApplication.class, args);
    }
}
