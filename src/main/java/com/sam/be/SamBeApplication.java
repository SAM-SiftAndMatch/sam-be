package com.sam.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SamBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SamBeApplication.class, args);
    }
}
