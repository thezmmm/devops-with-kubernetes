package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
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
        Path logFile = Paths.get(System.getenv().getOrDefault("LOG_FILE_PATH", "/data/log-output.txt"));
        Files.createDirectories(logFile.getParent());

        while (true) {
            Instant now = Instant.now();
            String line = now + ": " + appId + ".";
            Files.writeString(
                    logFile,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            System.out.println(buildConfigInfo());
            System.out.println(line);
            Thread.sleep(LOG_INTERVAL_MS);
        }
    }

    private static void runReader() throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path logFile = Paths.get(System.getenv().getOrDefault("LOG_FILE_PATH", "/data/log-output.txt"));
        String pingPongUrl = System.getenv().getOrDefault("PING_PONG_URL", "http://ping-pong/pingpong");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String body = buildStatus(logFile, pingPongUrl);
            writeResponse(exchange, 200, "text/plain; charset=UTF-8", body);
        });

        server.createContext("/status", exchange -> {
            String body = buildStatus(logFile, pingPongUrl);
            String json = "{\"message\":\"" + body.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            writeResponse(exchange, 200, "application/json; charset=UTF-8", json);
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Reader started in port %d, reading %s and ping-pong at %s%n", port, logFile, pingPongUrl);
    }

    private static String buildStatus(Path logFile, String pingPongUrl) throws IOException {
        String timestampAndId = Files.exists(logFile)
                ? Files.readString(logFile, StandardCharsets.UTF_8).trim()
                : "No log data yet.";

        int pingPongs = fetchPingCount(pingPongUrl);
        return buildConfigInfo() + "\n" + timestampAndId + "\nPing / Pongs: " + pingPongs;
    }

    private static String buildConfigInfo() {
        String configFilePath = System.getenv().getOrDefault("LOG_INFO_FILE_PATH", "/etc/config/information.txt");
        String fileContent = "not found";
        try {
            Path file = Paths.get(configFilePath);
            if (Files.exists(file)) {
                fileContent = Files.readString(file, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            fileContent = "error reading config file";
        }

        String message = System.getenv().getOrDefault("MESSAGE", "MESSAGE not set");
        return "file content: " + fileContent + "\nenv variable: MESSAGE=" + message;
    }

    private static int fetchPingCount(String pingPongUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(pingPongUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return 0;
            }

            try (InputStream in = connection.getInputStream()) {
                String response = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (response.startsWith("pong ")) {
                    return Integer.parseInt(response.substring("pong ".length()));
                }
            }
        } catch (IOException | NumberFormatException e) {
            return 0;
        }

        return 0;
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
