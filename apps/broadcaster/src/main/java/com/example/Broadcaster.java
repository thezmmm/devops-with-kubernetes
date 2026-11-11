package com.example;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Broadcaster {
    private static final String NATS_URL = System.getenv().getOrDefault("NATS_URL", "nats://nats:4222");
    private static final String NATS_SUBJECT = System.getenv().getOrDefault("NATS_SUBJECT", "todo.events");
    private static final String BROADCAST_URL = System.getenv().getOrDefault("BROADCAST_URL", System.getenv().getOrDefault("GENERIC_WEBHOOK_URL", ""));
    private static final String BROADCAST_MODE = System.getenv().getOrDefault("BROADCAST_MODE", "forward");

    public static void main(String[] args) throws Exception {
        if (BROADCAST_URL.isBlank()) {
            System.err.println("No broadcaster URL configured. Set BROADCAST_URL or GENERIC_WEBHOOK_URL before starting the broadcaster.");
            Thread.sleep(Duration.ofMinutes(1).toMillis());
            return;
        }

        while (true) {
            try (Connection connection = Nats.connect(NATS_URL)) {
                Dispatcher dispatcher = connection.createDispatcher(message -> {
                    String eventJson = new String(message.getData(), StandardCharsets.UTF_8);
                    try {
                        sendNotification(eventJson);
                    } catch (IOException e) {
                        System.err.printf("Failed to forward todo message: %s%n", e.getMessage());
                    }
                });
                dispatcher.subscribe(NATS_SUBJECT, "broadcaster");
                connection.flush(Duration.ofSeconds(10));
                System.out.printf("Broadcaster subscribed to %s and forwarding to %s%n", NATS_SUBJECT, BROADCAST_URL);

                while (true) {
                    Thread.sleep(1000L);
                }
            } catch (Exception e) {
                System.err.printf("NATS connection failed, retrying in 5s: %s%n", e.getMessage());
                Thread.sleep(5000L);
            }
        }
    }

    private static void sendNotification(String eventJson) throws IOException {
        String eventType = extractString(eventJson, "event");
        int todoId = extractInt(eventJson, "id");
        String todoText = extractString(eventJson, "text");

        String message = String.format("%s for todo #%d", eventType == null ? "todo event" : eventType, todoId);
        if (todoText != null && !todoText.isBlank()) {
            message = String.format("%s: %s", message, todoText);
        }

        if ("log-only".equalsIgnoreCase(BROADCAST_MODE) || BROADCAST_URL == null || BROADCAST_URL.isBlank()) {
            System.out.printf("LOG-ONLY broadcaster: %s%n", message);
            return;
        }

        String payload = "{\"user\":\"bot\",\"message\":\"" + escapeJson(message) + "\"}";
        URL url = new URL(BROADCAST_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Webhook returned status " + statusCode);
        }
        System.out.printf("Forwarded event to external webhook: %s%n", message);
    }

    private static int extractInt(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    private static String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return null;
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
