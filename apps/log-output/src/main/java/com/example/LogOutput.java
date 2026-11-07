package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

public class LogOutput {
    private static final long LOG_INTERVAL_MS = 5000L;
    private static final String APP_ID = UUID.randomUUID().toString();

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String html = "<html><body>"
                    + "<h1>Log output</h1>"
                    + "<p><a href=\"/status\">Current status</a></p>"
                    + "</body></html>";
            writeResponse(exchange, 200, "text/html; charset=UTF-8", html);
        });

        server.createContext("/status", exchange -> {
            String json = "{\"timestamp\":\"" + Instant.now() + "\",\"id\":\"" + APP_ID + "\"}";
            writeResponse(exchange, 200, "application/json; charset=UTF-8", json);
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Server started in port %d%n", port);

        while (true) {
            Instant now = Instant.now();
            System.out.printf("%s: %s%n", now, APP_ID);
            Thread.sleep(LOG_INTERVAL_MS);
        }
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
