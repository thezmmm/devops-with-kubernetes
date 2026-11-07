package com.example;

import java.time.Instant;
import java.util.UUID;

public class LogOutput {
    private static final long LOG_INTERVAL_MS = 5000L;

    public static void main(String[] args) throws InterruptedException {
        String appId = UUID.randomUUID().toString();

        while (true) {
            Instant now = Instant.now();
            System.out.printf("%s: %s%n", now, appId);
            Thread.sleep(LOG_INTERVAL_MS);
        }
    }
}
