package com.kalkitech.scheduled;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScheduledApiDbSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScheduledApiDbSyncApplication.class, args);
    }
}
