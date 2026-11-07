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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;

public class LogOutput {
    private static final long LOG_INTERVAL_MS = 5000L;

    public static void main(String[] args) throws Exception {
        String role = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "reader";

        if ("writer".equals(role)) {
            runWriter();
            return;
        }

        runReader();
    }

    private static void runWriter() throws IOException, InterruptedException {
        String appId = UUID.randomUUID().toString();
        Path logFile = Paths.get(System.getenv().getOrDefault("LOG_FILE_PATH", "/tmp/log-output.txt"));
        Files.createDirectories(logFile.getParent());

        while (true) {
            Instant now = Instant.now();
            String line = now + " " + appId;
            Files.writeString(
                    logFile,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            System.out.println(line);
            Thread.sleep(LOG_INTERVAL_MS);
        }
    }

    private static void runReader() throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path logFile = Paths.get(System.getenv().getOrDefault("LOG_FILE_PATH", "/tmp/log-output.txt"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String body = readLog(logFile);
            writeResponse(exchange, 200, "text/plain; charset=UTF-8", body);
        });

        server.createContext("/status", exchange -> {
            String body = readLog(logFile);
            String json = "{\"content\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            writeResponse(exchange, 200, "application/json; charset=UTF-8", json);
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Reader started in port %d, reading %s%n", port, logFile);
    }

    private static String readLog(Path logFile) throws IOException {
        if (Files.exists(logFile)) {
            return Files.readString(logFile, StandardCharsets.UTF_8);
        }
        return "No log data yet.";
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String contentType, String body) {
        try {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error serving request", e);
        }
    }
}
