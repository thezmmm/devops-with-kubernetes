package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TodoBackend {
    private static final List<String> TODOS = new ArrayList<>();
    private static final int MAX_TODO_LENGTH = 140;
    private static final Pattern TEXT_FIELD_PATTERN = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*?)\\\"");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/todos", exchange -> {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
                return;
            }
            sendResponse(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"Method not allowed\"}");
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Todo backend started on port %d%n", port);
    }

    private static void handleGet(HttpExchange exchange) throws IOException {
        String json = buildTodosJson();
        sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
    }

    private static void handlePost(HttpExchange exchange) throws IOException {
        String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String text = extractText(rawBody);

        if (text == null || text.trim().isEmpty()) {
            sendResponse(exchange, 400, "application/json; charset=UTF-8", "{\"error\":\"Todo text is required\"}");
            return;
        }

        String trimmed = text.trim();
        if (trimmed.length() > MAX_TODO_LENGTH) {
            sendResponse(exchange, 400, "application/json; charset=UTF-8", "{\"error\":\"Todo is too long\"}");
            return;
        }

        synchronized (TODOS) {
            TODOS.add(trimmed);
        }

        sendResponse(exchange, 200, "application/json; charset=UTF-8", buildTodosJson());
    }

    private static String extractText(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }

        String normalized = rawBody.trim();
        if (normalized.startsWith("{") && normalized.contains("text")) {
            Matcher matcher = TEXT_FIELD_PATTERN.matcher(normalized);
            if (matcher.find()) {
                return unescapeJson(matcher.group(1));
            }
        }

        String[] pairs = normalized.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "text".equals(parts[0])) {
                return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private static String buildTodosJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"todos\":[");
        synchronized (TODOS) {
            for (int i = 0; i < TODOS.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append('"').append(escapeJson(TODOS.get(i))).append('"');
            }
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String responseBody) throws IOException {
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
