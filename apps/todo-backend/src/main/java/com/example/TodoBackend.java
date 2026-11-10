package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TodoBackend {
    private static final String DB_URL = requireEnv("TODO_DB_URL");
    private static final String DB_USER = requireEnv("TODO_DB_USER");
    private static final String DB_PASSWORD = requireEnv("TODO_DB_PASSWORD");
    private static final int MAX_TODO_LENGTH = 140;
    private static final Pattern TEXT_FIELD_PATTERN = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(requireEnv("PORT"));
        initializeDatabase();

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
        System.out.printf("Todo backend started on port %d using database %s%n", port, DB_URL);
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static void initializeDatabase() {
        for (int attempt = 1; attempt <= 30; attempt++) {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS todos (id SERIAL PRIMARY KEY, text VARCHAR(140) NOT NULL)");
                return;
            } catch (SQLException e) {
                if (attempt == 30) {
                    throw new IllegalStateException("Failed to initialize the todo database", e);
                }
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for the database", interruptedException);
                }
            }
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static void handleGet(HttpExchange exchange) throws IOException {
        System.out.printf("Received GET /todos from %s%n", exchange.getRemoteAddress());
        try {
            String json = buildTodosJson(loadTodos());
            sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
        } catch (SQLException e) {
            sendResponse(exchange, 500, "application/json; charset=UTF-8", "{\"error\":\"Database error\"}");
        }
    }

    private static void handlePost(HttpExchange exchange) throws IOException {
        String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String text = extractText(rawBody);
        System.out.printf("Received POST /todos from %s with payload length=%d%n", exchange.getRemoteAddress(), rawBody.length());

        if (text == null || text.trim().isEmpty()) {
            System.out.printf("Rejected empty todo from %s%n", exchange.getRemoteAddress());
            sendResponse(exchange, 400, "application/json; charset=UTF-8", "{\"error\":\"Todo text is required\"}");
            return;
        }

        String trimmed = text.trim();
        if (trimmed.length() > MAX_TODO_LENGTH) {
            System.out.printf("Rejected too-long todo from %s: length=%d, max=%d%n", exchange.getRemoteAddress(), trimmed.length(), MAX_TODO_LENGTH);
            sendResponse(exchange, 400, "application/json; charset=UTF-8", "{\"error\":\"Todo is too long\"}");
            return;
        }

        System.out.printf("Accepted todo from %s: %s%n", exchange.getRemoteAddress(), trimmed);
        try {
            insertTodo(trimmed);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", buildTodosJson(loadTodos()));
        } catch (SQLException e) {
            sendResponse(exchange, 500, "application/json; charset=UTF-8", "{\"error\":\"Database error\"}");
        }
    }

    private static void insertTodo(String text) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO todos (text) VALUES (?)")) {
            statement.setString(1, text);
            statement.executeUpdate();
        }
    }

    private static List<String> loadTodos() throws SQLException {
        List<String> todos = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT text FROM todos ORDER BY id ASC");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                todos.add(resultSet.getString("text"));
            }
        }
        return todos;
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
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private static String buildTodosJson(List<String> todos) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"todos\":[");
        for (int i = 0; i < todos.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(todos.get(i))).append('"');
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