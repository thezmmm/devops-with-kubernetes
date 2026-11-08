package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;

public class PingPong {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(requireEnv("PORT"));
        Path countFile = Paths.get(requireEnv("PING_COUNT_FILE_PATH"));
        Files.createDirectories(countFile.getParent());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handleRequest(exchange, countFile));
        server.createContext("/pingpong", exchange -> handleRequest(exchange, countFile));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Server started in port %d, using %s%n", port, countFile);
    }

    private static void handleRequest(HttpExchange exchange, Path countFile) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendResponse(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            return;
        }

        int current = readCount(countFile);
        int next = current + 1;
        writeCount(countFile, next);

        String body = "pong " + next;
        System.out.printf("Handled request on %s: %s%n", path, body);
        sendResponse(exchange, 200, "text/plain; charset=UTF-8", body);
    }

    private static synchronized int readCount(Path file) throws IOException {
        if (Files.exists(file)) {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                return Integer.parseInt(content);
            }
        }
        return 0;
    }

    private static synchronized void writeCount(Path file, int count) throws IOException {
        Files.writeString(
                file,
                Integer.toString(count),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
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
