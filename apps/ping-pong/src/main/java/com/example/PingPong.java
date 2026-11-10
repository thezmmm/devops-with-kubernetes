package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;

public class PingPong {
    private static final String DB_URL = requireEnv("PING_PONG_DB_URL");
    private static final String DB_USER = requireEnv("PING_PONG_DB_USER");
    private static final String DB_PASSWORD = requireEnv("PING_PONG_DB_PASSWORD");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(requireEnv("PORT"));
        initializeDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", PingPong::handleRequest);
        server.createContext("/pingpong", PingPong::handleRequest);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Server started in port %d, using database %s%n", port, DB_URL);
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendResponse(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            return;
        }

        int next;
        try {
            next = incrementCounter();
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "text/plain; charset=UTF-8", "Database error");
            return;
        }

        String body = "pong " + next;
        System.out.printf("Handled request on %s: %s%n", path, body);
        sendResponse(exchange, 200, "text/plain; charset=UTF-8", body);
    }

    private static synchronized int incrementCounter() throws SQLException {
        try (Connection connection = getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS ping_pong (id INTEGER PRIMARY KEY, count INTEGER NOT NULL)"
                );
                statement.executeUpdate(
                        "INSERT INTO ping_pong (id, count) VALUES (1, 0) ON CONFLICT (id) DO NOTHING"
                );
            }

            int current;
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT count FROM ping_pong WHERE id = 1")) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        current = resultSet.getInt("count");
                    } else {
                        current = 0;
                    }
                }
            }

            int next = current + 1;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ping_pong SET count = ? WHERE id = 1")) {
                update.setInt(1, next);
                update.executeUpdate();
            }
            return next;
        }
    }

    private static void initializeDatabase() {
        for (int attempt = 1; attempt <= 30; attempt++) {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS ping_pong (id INTEGER PRIMARY KEY, count INTEGER NOT NULL)"
                );
                statement.executeUpdate(
                        "INSERT INTO ping_pong (id, count) VALUES (1, 0) ON CONFLICT (id) DO NOTHING"
                );
                return;
            } catch (SQLException e) {
                if (attempt == 30) {
                    throw new IllegalStateException("Could not connect to the PostgreSQL database", e);
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

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
