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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TodoServer {
    private static final String PICSUM_URL = requireEnv("PICSUM_URL");
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_TODO_LENGTH = 140;
    private static final String BACKEND_URL = requireEnv("TODO_BACKEND_URL");
    private static final Pattern TODO_LIST_PATTERN = Pattern.compile("\"todos\"\\s*:\\s*(\\[[\\s\\S]*?\\])");
    private static final Pattern TODO_OBJECT_PATTERN = Pattern.compile("\\{\\s*\\\"id\\\"\\s*:\\s*(\\d+)\\s*,\\s*\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"\\s*,\\s*\\\"done\\\"\\s*:\\s*(true|false)\\s*\\}");
    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static volatile boolean appHealthy = true;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(requireEnv("PORT"));
        Path cacheDir = Paths.get(requireEnv("IMAGE_CACHE_DIR"));
        Path imageFile = cacheDir.resolve("current.jpg");
        Path stateFile = cacheDir.resolve("cache-state.properties");
        Files.createDirectories(cacheDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if ("/break".equals(path)) {
                    appHealthy = false;
                    renderHtml(exchange, imageFile, stateFile, fetchTodos(), "App broken. Pod will restart.", 500);
                    return;
                }
                if ("/recover".equals(path)) {
                    appHealthy = true;
                    renderHtml(exchange, imageFile, stateFile, fetchTodos(), "App recovered.", 200);
                    return;
                }
                handlePost(exchange, imageFile, stateFile);
                return;
            }
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod()) && path != null && path.startsWith("/todos/")) {
                handlePut(exchange, imageFile, stateFile);
                return;
            }

            List<TodoItem> todos = fetchTodos();
            String html = buildHtml(imageFile, stateFile, todos, null, 200);
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.createContext("/healthz", exchange -> {
            if (!appHealthy) {
                sendJson(exchange, 500, "{\"status\":\"unhealthy\"}");
                return;
            }
            if (backendIsReachable()) {
                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } else {
                sendJson(exchange, 503, "{\"status\":\"backend-unavailable\"}");
            }
        });

        server.createContext("/readyz", exchange -> {
            if (!appHealthy) {
                sendJson(exchange, 503, "{\"status\":\"not-ready\"}");
                return;
            }
            if (backendIsReachable()) {
                sendJson(exchange, 200, "{\"status\":\"ready\"}");
            } else {
                sendJson(exchange, 503, "{\"status\":\"not-ready\"}");
            }
        });

        server.createContext("/break", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            appHealthy = false;
            sendJson(exchange, 200, "{\"status\":\"broken\"}");
        });

        server.createContext("/recover", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            appHealthy = true;
            sendJson(exchange, 200, "{\"status\":\"recovered\"}");
        });

        server.createContext("/todos/", exchange -> {
            if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            handlePut(exchange, imageFile, stateFile);
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
        System.out.printf("Todo app started in port %d with backend %s and cache at %s%n", port, BACKEND_URL, cacheDir);
    }

    private static void handlePost(HttpExchange exchange, Path imageFile, Path stateFile) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String todoValue = parseFormValue(body, "todo");

        if (todoValue == null) {
            renderHtml(exchange, imageFile, stateFile, fetchTodos(), "Please enter a todo before submitting.", 400);
            return;
        }

        String trimmedTodo = todoValue.trim();
        if (trimmedTodo.isEmpty()) {
            renderHtml(exchange, imageFile, stateFile, fetchTodos(), "Please enter a todo before submitting.", 400);
            return;
        }

        if (trimmedTodo.length() > MAX_TODO_LENGTH) {
            renderHtml(exchange, imageFile, stateFile, fetchTodos(), "Todo exceeds 140 characters. Please shorten it and try again.", 400);
            return;
        }

        String backendResponse = createTodo(trimmedTodo);
        if (backendResponse == null) {
            renderHtml(exchange, imageFile, stateFile, fetchTodos(), "Could not reach the todo backend.", 500);
            return;
        }

        List<TodoItem> todos = fetchTodos();
        renderHtml(exchange, imageFile, stateFile, todos, "Added: " + trimmedTodo, 200);
    }

    private static void handlePut(HttpExchange exchange, Path imageFile, Path stateFile) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String idText = path == null ? "" : path.substring("/todos/".length());
        if (idText.isBlank()) {
            renderHtml(exchange, imageFile, stateFile, fetchTodos(), "Invalid todo id.", 400);
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean done = parseDone(body);
        if (!markTodoDone(idText, done)) {
            renderHtml(exchange, imageFile, stateFile, fetchTodos(), "Could not update todo.", 500);
            return;
        }

        renderHtml(exchange, imageFile, stateFile, fetchTodos(), done ? "Marked as done." : "Marked as not done.", 200);
    }

    private static void renderHtml(HttpExchange exchange, Path imageFile, Path stateFile, List<TodoItem> todos, String statusMessage, int statusCode) throws IOException {
        String html = buildHtml(imageFile, stateFile, todos, statusMessage, statusCode);
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static String createTodo(String text) throws IOException {
        URL url = new URL(BACKEND_URL + "/todos");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        String payload = "{\"text\":\"" + escapeJson(text) + "\"}";
        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            return null;
        }

        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean backendIsReachable() {
        try {
            URL url = new URL(BACKEND_URL + "/readyz");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static boolean markTodoDone(String idText, boolean done) {
        try {
            URL url = new URL(BACKEND_URL + "/todos/" + idText);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            String body = "{\"done\":" + done + "}";
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            return connection.getResponseCode() >= 200 && connection.getResponseCode() < 300;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<TodoItem> fetchTodos() throws IOException {
        try {
            URL url = new URL(BACKEND_URL + "/todos");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Collections.emptyList();
            }

            try (InputStream in = connection.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return parseTodoList(json);
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static List<TodoItem> parseTodoList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        Matcher matcher = TODO_LIST_PATTERN.matcher(json);
        if (!matcher.find()) {
            return Collections.emptyList();
        }

        String items = matcher.group(1);
        List<TodoItem> result = new ArrayList<>();
        Matcher itemMatcher = TODO_OBJECT_PATTERN.matcher(items);
        while (itemMatcher.find()) {
            int id = Integer.parseInt(itemMatcher.group(1));
            String value = itemMatcher.group(2).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            boolean done = Boolean.parseBoolean(itemMatcher.group(3));
            result.add(new TodoItem(id, value, done));
        }

        if (result.isEmpty()) {
            Matcher legacyMatcher = JSON_STRING_PATTERN.matcher(items);
            while (legacyMatcher.find()) {
                String value = legacyMatcher.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                result.add(new TodoItem(0, value, false));
            }
        }
        return result;
    }

    private static boolean parseDone(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        Matcher matcher = Pattern.compile("\\\"done\\\"\\s*:\\s*(true|false)").matcher(body);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return false;
    }

    private static String parseFormValue(String formData, String key) {
        if (formData == null || formData.isBlank()) return null;
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] entry = pair.split("=", 2);
            if (entry.length == 2 && key.equals(entry[0])) {
                return java.net.URLDecoder.decode(entry[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String buildHtml(Path imageFile, Path stateFile, List<TodoItem> todoItems, String statusMessage, int statusCode) throws IOException {
        String bannerClass = statusCode >= 400 ? "error-banner" : "status-banner";
        String safeStatus = statusMessage == null || statusMessage.isBlank() ? "" : "<div class='" + bannerClass + "'>" + escapeHtml(statusMessage) + "</div>";
        String listMarkup = renderTodos(todoItems);

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
                + ".break-btn{background:#dc2626;margin-top:12px;}"
                + ".break-btn:hover{background:#b91c1c;}"
                + ".todo-item{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:8px 0;border-bottom:1px solid #e2e8f0;}"
                + ".todo-item.done .todo-text{text-decoration:line-through;color:#64748b;}"
                + ".todo-text{flex:1;word-break:break-word;}"
                + ".done-button{padding:8px 12px;font-size:0.9rem;background:#16a34a;}"
                + ".done-button.done{background:#e2e8f0;color:#0f172a;cursor:default;}"
                + ".status-banner{margin-bottom:16px;padding:10px 12px;border-radius:10px;background:#dcfce7;color:#166534;font-weight:600;border:1px solid #bbf7d0;}"
                + ".error-banner{margin-bottom:16px;padding:10px 12px;border-radius:10px;background:#fee2e2;color:#991b1b;font-weight:600;border:1px solid #fecaca;}"
                + "ul{padding-left:0;margin:0;list-style:none;line-height:1.8;}"
                + "@media (max-width: 760px){.hero{grid-template-columns:1fr;}}"
                + "</style></head><body><div class='container'><h1>Todo app</h1>"
                + safeStatus
                + "<div class='hero'><div class='image-card'><img src='/image' alt='Project image' /></div><div class='card'><form class='todo-form' method='POST' action='/'><input type='text' name='todo' maxlength='140' placeholder='Write a new todo...' aria-label='Todo input' /><button type='submit'>Send</button></form><form method='POST' action='/break'><button class='break-btn' type='submit'>Break app</button></form><ul>" + listMarkup + "</ul></div></div></div><script>document.querySelectorAll('.done-button:not(.done)').forEach(button => { button.addEventListener('click', () => { fetch('/todos/' + button.dataset.id, { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ done: true }) }).then(() => window.location.reload()); }); });</script></body></html>";
    }

    private static String renderTodos(List<TodoItem> todoItems) {
        if (todoItems == null || todoItems.isEmpty()) {
            return "<li>No todos yet.</li>";
        }

        StringBuilder builder = new StringBuilder();
        for (TodoItem todo : todoItems) {
            boolean done = todo.done;
            String buttonText = done ? "Done" : "Mark done";
            String buttonClass = done ? "done-button done" : "done-button";
            builder.append("<li class='todo-item ").append(done ? "done" : "").append("'><span class='todo-text'>").append(escapeHtml(todo.text)).append("</span><button type='button' class='")
                    .append(buttonClass).append("' data-id='").append(todo.id).append("'>").append(buttonText).append("</button></li>");
        }
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
