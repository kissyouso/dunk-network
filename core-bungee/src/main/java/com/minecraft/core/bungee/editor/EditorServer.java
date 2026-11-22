package com.minecraft.core.bungee.editor;

import com.minecraft.core.Core;
import com.minecraft.core.editor.EditorSessions;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Servidor HTTP do editor de permissões: serve o site (resources/editor) e a API (/api).
 * Baseado no editor do projeto Muxe (MIT, veja editor/NOTICE.txt).
 */
public class EditorServer {

    private static final String JSON = "application/json; charset=utf-8";

    private static final List<String> FILES = Arrays.asList(
            "index.html", "style.css", "app.js", "api.js", "state.js", "ui.js", "colors.js", "nodes.js",
            "ranks.js", "tags.js", "medals.js", "users.js", "history.js", "access.js", "favicon.png");

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' https://fonts.googleapis.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "font-src 'self' https://fonts.gstatic.com",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'none'",
            "frame-ancestors 'none'");

    private final EditorConfig config;
    private final EditorApi api;

    /* Endereço publicado no Redis; pode mudar quando o jogador entra por outro domínio */
    private volatile String publicUrl;

    private final Map<String, byte[]> assets = new HashMap<>();

    private final RateLimiter requests, failures;

    private HttpServer server;
    private ExecutorService executor;

    public EditorServer(EditorConfig config) {
        this.config = config;
        this.api = new EditorApi();
        this.publicUrl = config.getPublicUrl();

        this.requests = new RateLimiter(config.getRequestsPerMinute(), 60_000L);
        this.failures = new RateLimiter(config.getFailedLoginsPerTenMinutes(), 600_000L);
    }

    public void start() throws IOException {
        for (String file : FILES) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("editor/" + file)) {
                if (in == null)
                    throw new IOException("O arquivo do editor " + file + " não está no jar");

                assets.put("/" + file, readAll(in, Integer.MAX_VALUE));
            }
        }

        assets.put("/", assets.get("/index.html"));

        server = HttpServer.create(new InetSocketAddress(config.getBind(), config.getPort()), 64);
        executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "dunk-editor-http");
            thread.setDaemon(true);
            return thread;
        });

        server.createContext("/", this::dispatch);
        server.setExecutor(executor);
        server.start();

        /* Os servidores de jogo montam o link do /editor com este endereço */
        publishPublicUrl(publicUrl);
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    /**
     * Quando o public-url está em "auto", usa o host pelo qual o jogador entrou.
     * Isso evita publicar um IP interno/errado quando o proxy roda em painel ou atrás de domínio.
     */
    public synchronized void updatePublicUrl(String host) {
        if (!config.isAutomaticPublicUrl() || host == null || host.trim().isEmpty())
            return;

        String clean = host.trim();

        if (clean.equalsIgnoreCase("localhost") || clean.equals("127.0.0.1") || clean.equals("0.0.0.0"))
            return;

        if (clean.contains(":") && !clean.startsWith("["))
            clean = "[" + clean + "]";

        String url = "http://" + clean + ":" + config.getPort();

        if (url.equals(publicUrl))
            return;

        publicUrl = url;

        try {
            publishPublicUrl(url);
            Core.getLogger().info("[Editor] Endereço público atualizado para " + url);
        } catch (Exception e) {
            Core.getLogger().warning("[Editor] Não foi possível publicar o novo endereço: " + e.getMessage());
        }
    }

    private void publishPublicUrl(String url) {
        try {
            Core.getRedis().delete(EditorSessions.URL_KEY);
            Core.getRedis().saveDefault(EditorSessions.URL_KEY, url);
        } catch (Exception e) {
            Core.getLogger().warning("[Editor] Não foi possível publicar o endereço público: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null)
            server.stop(1);

        if (executor != null)
            executor.shutdownNow();

        try {
            Core.getRedis().delete(EditorSessions.URL_KEY);
        } catch (Exception ignored) {
        }
    }

    private void dispatch(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getRawPath();
            String prefix = config.getPathPrefix();

            /* Aceita tanto /editor/... (proxy preservando o caminho) quanto /... (proxy removendo) */
            if (!prefix.isEmpty()) {
                if (path.equals(prefix)) {
                    exchange.getResponseHeaders().set("Location", prefix + "/");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }

                if (path.startsWith(prefix + "/"))
                    path = path.substring(prefix.length());
            }

            if (path.startsWith("/api/"))
                handleApi(exchange, path);
            else
                handleStatic(exchange, path);
        } catch (Exception e) {
            Core.getLogger().log(Level.FINE, "[Editor] Falha na requisição", e);
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        securityHeaders(exchange.getResponseHeaders(), true);

        String client = client(exchange);

        if (failures.isBlocked(client) || !requests.tryAcquire(client)) {
            send(exchange, EditorApi.Response.error(429, "editor.too-many-requests", "Too many requests, wait a minute"));
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");

        String body = "";

        if (method.equals("POST")) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");

            if (origin != null && !originAllowed(origin, exchange.getRequestHeaders().getFirst("Host"))) {
                send(exchange, EditorApi.Response.error(403, "editor.bad-origin", "Requests must come from the editor page"));
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
                send(exchange, EditorApi.Response.error(415, "editor.unsupported-media-type", "Send application/json"));
                return;
            }

            byte[] bytes = readAll(exchange.getRequestBody(), config.getMaxBodyBytes() + 1);

            if (bytes.length > config.getMaxBodyBytes()) {
                send(exchange, EditorApi.Response.error(413, "editor.too-large", "The request is too large"));
                return;
            }

            body = new String(bytes, StandardCharsets.UTF_8);
        } else if (!method.equals("GET")) {
            send(exchange, EditorApi.Response.error(405, "editor.method-not-allowed", "Method not allowed"));
            return;
        }

        EditorApi.Response response = api.handle(method, path, exchange.getRequestURI().getRawQuery(), authorization, body);

        if (response.getStatus() == 401 || response.getStatus() == 403)
            failures.tryAcquire(client);

        send(exchange, response);
    }

    private void handleStatic(HttpExchange exchange, String path) throws IOException {
        securityHeaders(exchange.getResponseHeaders(), false);

        if (!requests.tryAcquire(client(exchange))) {
            sendText(exchange, 429, "Too many requests");
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);

        if (!method.equals("GET") && !method.equals("HEAD")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        byte[] bytes = assets.get(path);

        if (bytes == null) {
            sendText(exchange, 404, "Not found");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", contentType(path));

        if (method.equals("HEAD")) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private boolean originAllowed(String origin, String host) {
        String normalized = origin.trim().toLowerCase(Locale.ROOT);

        if (sameOrigin(normalized, publicUrl))
            return true;

        if (host == null || host.trim().isEmpty())
            return false;

        String self = host.trim().toLowerCase(Locale.ROOT);

        return normalized.equals("http://" + self) || normalized.equals("https://" + self);
    }

    /* Compara apenas scheme + host + porta (o public-url pode ter caminho, ex.: /editor) */
    private static boolean sameOrigin(String origin, String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);

            if (uri.getAuthority() == null)
                return false;

            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();

            return origin.equals((scheme + "://" + uri.getAuthority()).toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void securityHeaders(Headers headers, boolean api) {
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        headers.set("Cache-Control", api ? "no-store" : "no-cache");

        if (api)
            headers.set("Pragma", "no-cache");
    }

    private static String contentType(String path) {
        if (path.equals("/") || path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static void send(HttpExchange exchange, EditorApi.Response response) throws IOException {
        byte[] bytes = Core.GSON.toJson(response.getBody()).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", JSON);
        exchange.sendResponseHeaders(response.getStatus(), bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);

            if (out.size() >= max) break;
        }

        return out.toByteArray();
    }

    private static String client(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();

        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
