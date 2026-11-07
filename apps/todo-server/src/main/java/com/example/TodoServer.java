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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

public class TodoServer {
    private static final String PICSUM_URL = "https://picsum.photos/1200";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path cacheDir = Paths.get(System.getenv().getOrDefault("IMAGE_CACHE_DIR", "/data/images"));
        Path imageFile = cacheDir.resolve("current.jpg");
        Path stateFile = cacheDir.resolve("cache-state.properties");
        Files.createDirectories(cacheDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String html = buildHtml(imageFile, stateFile);
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/image", exchange -> {
            byte[] image = getCachedImage(imageFile, stateFile);
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, image.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(image);
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.printf("Server started in port %d with cache at %s%n", port, cacheDir);
    }

    private static String buildHtml(Path imageFile, Path stateFile) throws IOException {
        String imageUrl = "/image";
        return "<html><body style='font-family:Arial,sans-serif;padding:20px;'>"
                + "<h1>Todo app</h1>"
                + "<p>Server started in port 8080</p>"
                + "<p><img src='" + imageUrl + "' alt='Project image' style='max-width:1200px;width:100%;display:block;border-radius:8px;' /></p>"
                + "<p>Cached image: " + imageFile.getFileName() + "</p>"
                + "</body></html>";
    }

    private static byte[] getCachedImage(Path imageFile, Path stateFile) throws IOException {
        CacheState state = CacheState.load(stateFile);
        Instant now = Instant.now();

        if (state == null || !Files.exists(imageFile)) {
            return refreshImage(imageFile, stateFile);
        }

        if (now.isBefore(state.expiresAt)) {
            return Files.readAllBytes(imageFile);
        }

        if (!state.graceUsed) {
            state.graceUsed = true;
            state.save(stateFile);
            return Files.readAllBytes(imageFile);
        }

        return refreshImage(imageFile, stateFile);
    }

    private static byte[] refreshImage(Path imageFile, Path stateFile) throws IOException {
        byte[] bytes = downloadImage();
        Files.write(imageFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        CacheState state = new CacheState(Instant.now().plus(CACHE_TTL), false);
        state.save(stateFile);
        return bytes;
    }

    private static byte[] downloadImage() throws IOException {
        URL url = new URL(PICSUM_URL + "?random=" + Instant.now().toEpochMilli());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Failed to fetch image: " + status);
        }

        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static class CacheState {
        private final Instant expiresAt;
        private boolean graceUsed;

        private CacheState(Instant expiresAt, boolean graceUsed) {
            this.expiresAt = expiresAt;
            this.graceUsed = graceUsed;
        }

        private static CacheState load(Path file) throws IOException {
            if (!Files.exists(file)) {
                return null;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                return null;
            }
            String[] parts = content.split(";");
            Instant expiresAt = null;
            boolean graceUsed = false;
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                if ("expiresAt".equals(kv[0])) {
                    expiresAt = Instant.parse(kv[1]);
                } else if ("graceUsed".equals(kv[0])) {
                    graceUsed = Boolean.parseBoolean(kv[1]);
                }
            }
            if (expiresAt == null) {
                return null;
            }
            return new CacheState(expiresAt, graceUsed);
        }

        private void save(Path file) throws IOException {
            String content = "expiresAt=" + expiresAt + ";graceUsed=" + graceUsed;
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }
}
