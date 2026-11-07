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
import java.util.List;
import java.util.concurrent.Executors;

public class TodoServer {
    private static final String PICSUM_URL = "https://picsum.photos/1200";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_TODO_LENGTH = 140;
    private static final List<String> TODO_ITEMS = new java.util.ArrayList<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path cacheDir = Paths.get(System.getenv().getOrDefault("IMAGE_CACHE_DIR", "/data/images"));
        Path imageFile = cacheDir.resolve("current.jpg");
        Path stateFile = cacheDir.resolve("cache-state.properties");
        Files.createDirectories(cacheDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                handlePost(exchange, imageFile, stateFile);
                return;
            }
            String html = buildHtml(imageFile, stateFile, null);
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

    private static void handlePost(HttpExchange exchange, Path imageFile, Path stateFile) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String text = new String(body, StandardCharsets.UTF_8);
        String todoValue = parseFormValue(text, "todo");

        if (todoValue == null) {
            String html = buildHtml(imageFile, stateFile, "Please enter a todo before submitting.");
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(400, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
            return;
        }

        String trimmedTodo = todoValue.trim();
        if (trimmedTodo.isEmpty()) {
            String html = buildHtml(imageFile, stateFile, "Please enter a todo before submitting.");
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(400, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
            return;
        }

        if (trimmedTodo.length() > MAX_TODO_LENGTH) {
            String html = buildHtml(imageFile, stateFile, "Todo exceeds 140 characters. Please shorten it and try again.");
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(400, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
            return;
        }

        TODO_ITEMS.add(trimmedTodo);
        String html = buildHtml(imageFile, stateFile, "Added: " + trimmedTodo);
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static String parseFormValue(String formData, String key) {
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] entry = pair.split("=", 2);
            if (entry.length != 2) {
                continue;
            }
            if (key.equals(entry[0])) {
                return java.net.URLDecoder.decode(entry[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String buildHtml(Path imageFile, Path stateFile, String statusMessage) throws IOException {
        String successHtml = (statusMessage == null || statusMessage.isBlank())
                ? ""
                : "<div class='status-banner'>" + statusMessage + "</div>";

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Todo app</title>"
                + "<style>"
                + "body{font-family:Arial,sans-serif;background:linear-gradient(135deg,#eef6ff,#f8fafc);margin:0;padding:40px;color:#0f172a;}"
                + ".container{max-width:1150px;margin:0 auto;background:#ffffff;border-radius:16px;box-shadow:0 18px 40px rgba(15,23,42,0.08);padding:24px 28px;}"
                + "h1{margin:0 0 18px;font-size:2rem;}"
                + ".hero{display:grid;grid-template-columns:1.3fr 0.7fr;gap:24px;align-items:start;}"
                + ".image-card{background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:12px;}"
                + ".image-card img{display:block;width:100%;max-height:420px;object-fit:cover;border-radius:8px;}"
                + ".card{background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:18px;}"
                + ".todo-form{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:18px;}"
                + "input[type='text']{flex:1 1 280px;padding:12px 14px;border:1px solid #cbd5e1;border-radius:10px;font-size:1rem;min-width:220px;box-sizing:border-box;}"
                + "input[type='text']:focus{outline:none;border-color:#2563eb;box-shadow:0 0 0 3px rgba(37,99,235,0.15);}"
                + "button{background:#2563eb;color:#fff;border:none;border-radius:10px;padding:12px 18px;font-size:1rem;font-weight:600;cursor:pointer;transition:background 0.2s ease;}"
                + "button:hover{background:#1d4ed8;}"
                + ".status-banner{margin-bottom:16px;padding:10px 12px;border-radius:10px;background:#dcfce7;color:#166534;font-weight:600;border:1px solid #bbf7d0;}"
                + ".error-banner{margin-bottom:16px;padding:10px 12px;border-radius:10px;background:#fee2e2;color:#991b1b;font-weight:600;border:1px solid #fecaca;}"
                + "ul{padding-left:20px;margin:0;line-height:1.8;}"
                + "li{padding:4px 0;color:#334155;}"
                + "@media (max-width: 760px){.hero{grid-template-columns:1fr;}}"
                + "</style></head><body><div class='container'><h1>Todo app</h1>"
                + (statusMessage != null && statusMessage.startsWith("Added:") ? successHtml : statusMessage != null ? "<div class='error-banner'>" + statusMessage + "</div>" : "")
                + "<div class='hero'><div class='image-card'><img src='/image' alt='Project image' /></div><div class='card'><form class='todo-form' method='POST' action='/'><input type='text' name='todo' maxlength='140' placeholder='Write a new todo...' aria-label='Todo input' /><button type='submit'>Send</button></form><ul>" + renderTodos() + "</ul></div></div></div></body></html>";
    }

    private static String renderTodos() {
        StringBuilder builder = new StringBuilder();
        if (TODO_ITEMS.isEmpty()) {
            return "<li>No todos yet.</li>";
        }
        for (String todo : TODO_ITEMS) {
            builder.append("<li>").append(todo).append("</li>");
        }
        return builder.toString();
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
