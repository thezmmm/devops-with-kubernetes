package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nats.client.Nats;

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
    private static final String NATS_URL = System.getenv().getOrDefault("NATS_URL", "nats://nats:4222");
    private static final String NATS_SUBJECT = System.getenv().getOrDefault("NATS_SUBJECT", "todo.events");
    private static final int MAX_TODO_LENGTH = 140;
    private static final Pattern TEXT_FIELD_PATTERN = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static volatile boolean appHealthy = true;
    private static final Pattern DONE_FIELD_PATTERN = Pattern.compile("\\\"done\\\"\\s*:\\s*(true|false)");
    private static volatile io.nats.client.Connection natsConnection;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(requireEnv("PORT"));
        initializeDatabase();
        natsConnection = connectNats();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/healthz", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/break".equals(exchange.getRequestURI().getPath())) {
                appHealthy = false;
                sendResponse(exchange, 200, "application/json; charset=UTF-8", "{\"status\":\"broken\"}");
                return;
            }
            if (!appHealthy) {
                sendResponse(exchange, 500, "application/json; charset=UTF-8", "{\"status\":\"unhealthy\"}");
                return;
            }
            sendResponse(exchange, 200, "application/json; charset=UTF-8", "{\"status\":\"ok\"}");
        });

        server.createContext("/readyz", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                if (!appHealthy || !checkDatabaseConnectivity()) {
                    sendResponse(exchange, 503, "application/json; charset=UTF-8", "{\"status\":\"not-ready\"}");
                    return;
                }
                sendResponse(exchange, 200, "application/json; charset=UTF-8", "{\"status\":\"ready\"}");
            } catch (SQLException e) {
                sendResponse(exchange, 503, "application/json; charset=UTF-8", "{\"status\":\"not-ready\"}");
            }
        });

        server.createContext("/break", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"Method not allowed\"}");
                return;
            }
            appHealthy = false;
            sendResponse(exchange, 200, "application/json; charset=UTF-8", "{\"status\":\"broken\"}");
        });

        server.createContext("/recover", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"Method not allowed\"}");
                return;
            }
            appHealthy = true;
            sendResponse(exchange, 200, "application/json; charset=UTF-8", "{\"status\":\"recovered\"}");
        });

        server.createContext("/todos", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path != null && path.startsWith("/todos/")) {
                handlePut(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
                return;
            }
            if ("PUT".equalsIgnoreCase(method)) {
                handlePut(exchange);
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
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS todos (id SERIAL PRIMARY KEY, text VARCHAR(140) NOT NULL, done BOOLEAN NOT NULL DEFAULT FALSE)");
                statement.executeUpdate("ALTER TABLE todos ADD COLUMN IF NOT EXISTS done BOOLEAN NOT NULL DEFAULT FALSE");
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

    private static boolean checkDatabaseConnectivity() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next() && resultSet.getInt(1) == 1;
        }
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

    private static void handlePut(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null || !path.startsWith("/todos/")) {
            sendResponse(exchange, 404, "application/json; charset=UTF-8", "{\"error\":\"Not found\"}");
            return;
        }

        String idText = path.substring("/todos/".length());
        if (idText.isBlank()) {
            sendResponse(exchange, 404, "application/json; charset=UTF-8", "{\"error\":\"Not found\"}");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idText);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "application/json; charset=UTF-8", "{\"error\":\"Invalid todo id\"}");
            return;
        }

        String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean done = extractDone(rawBody);
        try {
            markTodoDone(id, done);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", buildTodosJson(loadTodos()));
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

    private static io.nats.client.Connection connectNats() {
        if (NATS_URL == null || NATS_URL.isBlank()) {
            return null;
        }

        try {
            io.nats.client.Connection connection = Nats.connect(NATS_URL);
            System.out.printf("Connected to NATS at %s%n", NATS_URL);
            return connection;
        } catch (Exception e) {
            System.err.printf("NATS is unavailable, continuing without notifications: %s%n", e.getMessage());
            return null;
        }
    }

    private static void publishTodoEvent(String eventType, int id, String text, boolean done) {
        if (natsConnection == null) {
            return;
        }

        StringBuilder payload = new StringBuilder();
        payload.append("{\"event\":\"").append(eventType).append("\",\"id\":").append(id);
        if (text != null && !text.isBlank()) {
            payload.append(",\"text\":\"").append(escapeJson(text)).append("\"");
        }
        payload.append(",\"done\":").append(done).append(",\"user\":\"bot\"}");

        try {
            natsConnection.publish(NATS_SUBJECT, payload.toString().getBytes(StandardCharsets.UTF_8));
            natsConnection.flush(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            System.err.printf("Could not publish %s event for todo %d: %s%n", eventType, id, e.getMessage());
        }
    }

    private static int insertTodo(String text) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO todos (text, done) VALUES (?, false) RETURNING id")) {
            statement.setString(1, text);
            try (ResultSet resultSet = statement.executeQuery()) {
               if (resultSet.next()) {
                   int id = resultSet.getInt(1);
                   publishTodoEvent("todo.created", id, text, false);
                   return id;
               }
            }
        }
        throw new SQLException("Todo was not created");
    }

    private static void markTodoDone(int id, boolean done) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE todos SET done = ? WHERE id = ?")) {
            statement.setBoolean(1, done);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
        publishTodoEvent("todo.updated", id, null, done);
    }

    private static List<TodoItem> loadTodos() throws SQLException {
        List<TodoItem> todos = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, text, done FROM todos ORDER BY id ASC");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                todos.add(new TodoItem(resultSet.getInt("id"), resultSet.getString("text"), resultSet.getBoolean("done")));
            }
        }
        return todos;
    }

    private static boolean extractDone(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return false;
        }

        Matcher matcher = DONE_FIELD_PATTERN.matcher(rawBody);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }

        String[] pairs = rawBody.trim().split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "done".equals(parts[0])) {
                return Boolean.parseBoolean(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }

        return false;
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

    private static String buildTodosJson(List<TodoItem> todos) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"todos\":[");
        for (int i = 0; i < todos.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            TodoItem todo = todos.get(i);
            builder.append("{\"id\":").append(todo.id)
                    .append(",\"text\":\"").append(escapeJson(todo.text)).append("\",\"done\":")
                    .append(todo.done).append('}');
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

    private static class TodoItem {
        private final int id;
        private final String text;
        private final boolean done;

        private TodoItem(int id, String text, boolean done) {
            this.id = id;
            this.text = text;
            this.done = done;
        }
    }
}