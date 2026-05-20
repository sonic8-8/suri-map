package com.mock112;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Mock112Application {

    public static void main(String[] args) {
        SpringApplication.run(Mock112Application.class, args);
    }
}
