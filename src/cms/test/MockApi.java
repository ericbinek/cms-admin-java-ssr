package cms.test;

import cms.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Auth-aware in-memory mock of the CMS API for admin conformance tests. It mirrors
 * the real API's wire envelope ({ items, total }, { id, ...item }, { status, error,
 * message, details, path }) AND its auth contract: /auth/login issues an opaque
 * bearer token, /auth/me and /auth/logout validate it, and every entity route
 * requires a live session (401 without). RBAC is the real API's job; here the
 * seeded admin has full access, which is enough to prove the admin frontend's
 * cookie-to-bearer proxy and CSRF handling.
 */
public final class MockApi {

    public static final Map<String, Map<String, Object>> SCHEMAS = new LinkedHashMap<>();
    static {
        SCHEMAS.put("BlogPosting", Map.of("plural", "blog-postings", "required", List.of("headline", "articleBody", "author")));
        SCHEMAS.put("Person", Map.of("plural", "persons", "required", List.of("name")));
        SCHEMAS.put("WebPage", Map.of("plural", "web-pages", "required", List.of("headline")));
        SCHEMAS.put("ImageObject", Map.of("plural", "image-objects", "required", List.of("contentUrl")));
        SCHEMAS.put("CategoryCode", Map.of("plural", "category-codes", "required", List.of("name", "codeValue", "inCodeSet")));
        SCHEMAS.put("CategoryCodeSet", Map.of("plural", "category-code-sets", "required", List.of("name")));
        SCHEMAS.put("DefinedTerm", Map.of("plural", "defined-terms", "required", List.of("name", "termCode", "inDefinedTermSet")));
        SCHEMAS.put("DefinedTermSet", Map.of("plural", "defined-term-sets", "required", List.of("name")));
        SCHEMAS.put("Comment", Map.of("plural", "comments", "required", List.of("text", "author", "about")));
        SCHEMAS.put("WebSite", Map.of("plural", "web-sites", "required", List.of("name", "url")));
    }

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin-password";

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Map<String, Map<String, Map<String, Object>>> STORE = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> SESSIONS = new ConcurrentHashMap<>();
    private static Map<String, Object> admin;

    private MockApi() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "0"));
        String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
        for (String name : SCHEMAS.keySet()) STORE.put(name, new LinkedHashMap<>());
        admin = new LinkedHashMap<>();
        admin.put("id", UUID.randomUUID().toString());
        admin.put("username", ADMIN_USERNAME);
        admin.put("role", "admin");
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", new Handler());
        server.start();
        System.err.println("mock api ready on " + server.getAddress().getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
    }

    private static String entityForPlural(String plural) {
        for (Map.Entry<String, Map<String, Object>> e : SCHEMAS.entrySet()) {
            if (plural.equals(e.getValue().get("plural"))) return e.getKey();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> validateRequired(String entity, Map<String, Object> data, boolean partial) {
        if (partial) return List.of();
        List<String> missing = new ArrayList<>();
        for (String f : (List<String>) SCHEMAS.get(entity).get("required")) {
            Object v = data.get(f);
            if (v == null || "".equals(v) || (v instanceof List && ((List<?>) v).isEmpty())) {
                missing.add("Field \"" + f + "\" is required.");
            }
        }
        return missing;
    }

    private static Map<String, Object> error(int status, String code, String message, List<String> details, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("error", code);
        m.put("message", message);
        m.put("details", details);
        m.put("path", path);
        return m;
    }

    private static Map<String, Object> unauthorized(String path) {
        return error(401, "UNAUTHORIZED", "Authentication is required, or the session is invalid or expired.", List.of(), path);
    }

    private static void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        if (status == 204 || data == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] body = Json.stringify(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) > 0) buf.write(chunk, 0, n);
        byte[] all = buf.toByteArray();
        if (all.length == 0) return new LinkedHashMap<>();
        Object parsed = Json.parse(new String(all, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map)) return new LinkedHashMap<>();
        return (Map<String, Object>) parsed;
    }

    private static String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) return null;
        String trimmed = header.trim();
        if (!trimmed.startsWith("Bearer ")) return null;
        return trimmed.substring("Bearer ".length());
    }

    private static Map<String, Object> accountFor(HttpExchange exchange) {
        String token = bearerToken(exchange);
        if (token == null) return null;
        return SESSIONS.get(token);
    }

    private static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String requestPath = method + " " + path;
            try {
                if ("GET".equals(method) && "/health".equals(path)) {
                    sendJson(exchange, 200, Map.of("status", "ok"));
                    return;
                }

                if ("/auth/login".equals(path)) {
                    if (!"POST".equals(method)) { sendJson(exchange, 405, error(405, "METHOD_NOT_ALLOWED", "Method not allowed.", List.of(), requestPath)); return; }
                    Map<String, Object> data = readJsonBody(exchange);
                    if (!(data.get("username") instanceof String) || !(data.get("password") instanceof String)) {
                        sendJson(exchange, 400, error(400, "VALIDATION_ERROR", "Invalid request data.", List.of("Fields \"username\" and \"password\" are required."), requestPath));
                        return;
                    }
                    if (!ADMIN_USERNAME.equals(data.get("username")) || !ADMIN_PASSWORD.equals(data.get("password"))) {
                        sendJson(exchange, 401, unauthorized(requestPath));
                        return;
                    }
                    String token = UUID.randomUUID().toString();
                    SESSIONS.put(token, admin);
                    Map<String, Object> account = new LinkedHashMap<>();
                    account.put("id", admin.get("id"));
                    account.put("username", admin.get("username"));
                    account.put("role", admin.get("role"));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("token", token);
                    out.put("account", account);
                    out.put("expiresAt", Instant.now().plusSeconds(8 * 3600).toString());
                    sendJson(exchange, 200, out);
                    return;
                }

                if ("/auth/logout".equals(path)) {
                    if (!"POST".equals(method)) { sendJson(exchange, 405, error(405, "METHOD_NOT_ALLOWED", "Method not allowed.", List.of(), requestPath)); return; }
                    String token = bearerToken(exchange);
                    if (token == null || !SESSIONS.containsKey(token)) { sendJson(exchange, 401, unauthorized(requestPath)); return; }
                    SESSIONS.remove(token);
                    sendJson(exchange, 204, null);
                    return;
                }

                if ("/auth/me".equals(path)) {
                    if (!"GET".equals(method)) { sendJson(exchange, 405, error(405, "METHOD_NOT_ALLOWED", "Method not allowed.", List.of(), requestPath)); return; }
                    Map<String, Object> account = accountFor(exchange);
                    if (account == null) { sendJson(exchange, 401, unauthorized(requestPath)); return; }
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("id", account.get("id"));
                    a.put("username", account.get("username"));
                    a.put("role", account.get("role"));
                    sendJson(exchange, 200, Map.of("account", a));
                    return;
                }

                // Every entity route requires a live session.
                if (accountFor(exchange) == null) { sendJson(exchange, 401, unauthorized(requestPath)); return; }

                String[] seg = path.split("/");
                List<String> segments = new ArrayList<>();
                for (String s : seg) if (!s.isEmpty()) segments.add(s);
                if (segments.size() < 1 || segments.size() > 2) {
                    sendJson(exchange, 404, error(404, "ROUTE_NOT_FOUND", "No route matches this request.", List.of(), requestPath));
                    return;
                }
                String entity = entityForPlural(segments.get(0));
                if (entity == null) {
                    sendJson(exchange, 404, error(404, "ROUTE_NOT_FOUND", "No route matches this request.", List.of(), requestPath));
                    return;
                }
                LOCK.lock();
                try {
                    if (segments.size() == 1) {
                        handleCollection(exchange, method, entity, requestPath);
                        return;
                    }
                    handleItem(exchange, method, entity, segments.get(1).toLowerCase(), requestPath);
                } finally {
                    LOCK.unlock();
                }
            } catch (Json.JsonException e) {
                sendJson(exchange, 400, error(400, "INVALID_JSON", "Request body is not valid JSON.", List.of(), requestPath));
            } catch (Exception e) {
                sendJson(exchange, 500, error(500, "INTERNAL_ERROR", "Internal server error: " + e.getMessage(), List.of(), requestPath));
            } finally {
                exchange.close();
            }
        }

        private void handleCollection(HttpExchange exchange, String method, String entity, String requestPath) throws IOException {
            Map<String, Map<String, Object>> collection = STORE.get(entity);
            if ("GET".equals(method)) {
                List<Map<String, Object>> items = new ArrayList<>(collection.values());
                String q = exchange.getRequestURI().getRawQuery();
                Map<String, String> qs = new LinkedHashMap<>();
                if (q != null) for (String pair : q.split("&")) {
                    if (pair.isEmpty()) continue;
                    int eq = pair.indexOf('=');
                    qs.put(eq < 0 ? pair : pair.substring(0, eq), eq < 0 ? "" : pair.substring(eq + 1));
                }
                String sort = qs.getOrDefault("sort", "dateCreated");
                int direction = "asc".equals(qs.getOrDefault("order", "desc")) ? 1 : -1;
                items.sort((a, b) -> {
                    Object va = a.get(sort);
                    Object vb = b.get(sort);
                    String sa = va == null ? "" : va.toString();
                    String sb = vb == null ? "" : vb.toString();
                    return direction * sa.compareTo(sb);
                });
                int total = items.size();
                int limit = Math.min(Integer.parseInt(qs.getOrDefault("limit", "20")), 100);
                int offset = Integer.parseInt(qs.getOrDefault("offset", "0"));
                int from = Math.min(offset, total);
                int to = Math.min(from + limit, total);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("items", items.subList(from, to));
                body.put("total", total);
                sendJson(exchange, 200, body);
                return;
            }
            if ("POST".equals(method)) {
                Map<String, Object> data = readJsonBody(exchange);
                List<String> errs = validateRequired(entity, data, false);
                if (!errs.isEmpty()) { sendJson(exchange, 400, error(400, "VALIDATION_ERROR", "Invalid request data.", errs, requestPath)); return; }
                String now = Instant.now().toString();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("@context", "https://schema.org");
                item.put("@type", entity);
                item.putAll(data);
                item.put("id", UUID.randomUUID().toString());
                item.put("dateCreated", now);
                item.put("dateModified", now);
                collection.put((String) item.get("id"), item);
                sendJson(exchange, 201, item);
                return;
            }
            sendJson(exchange, 405, error(405, "METHOD_NOT_ALLOWED", "Method not allowed.", List.of(), requestPath));
        }

        private void handleItem(HttpExchange exchange, String method, String entity, String id, String requestPath) throws IOException {
            Map<String, Map<String, Object>> collection = STORE.get(entity);
            Map<String, Object> current = collection.get(id);

            if ("GET".equals(method)) {
                if (current == null) { sendJson(exchange, 404, error(404, "NOT_FOUND", entity + " not found.", List.of(), requestPath)); return; }
                sendJson(exchange, 200, current);
                return;
            }
            if ("PUT".equals(method)) {
                if (current == null) { sendJson(exchange, 404, error(404, "NOT_FOUND", entity + " not found.", List.of(), requestPath)); return; }
                Map<String, Object> data = readJsonBody(exchange);
                List<String> errs = validateRequired(entity, data, true);
                if (!errs.isEmpty()) { sendJson(exchange, 400, error(400, "VALIDATION_ERROR", "Invalid request data.", errs, requestPath)); return; }
                Map<String, Object> updated = new LinkedHashMap<>(current);
                updated.putAll(data);
                updated.put("id", current.get("id"));
                updated.put("dateCreated", current.get("dateCreated"));
                updated.put("dateModified", Instant.now().toString());
                updated.put("@context", current.getOrDefault("@context", "https://schema.org"));
                updated.put("@type", current.getOrDefault("@type", entity));
                collection.put(id, updated);
                sendJson(exchange, 200, updated);
                return;
            }
            if ("DELETE".equals(method)) {
                if (current == null) { sendJson(exchange, 404, error(404, "NOT_FOUND", entity + " not found.", List.of(), requestPath)); return; }
                collection.remove(id);
                sendJson(exchange, 204, null);
                return;
            }
            sendJson(exchange, 405, error(405, "METHOD_NOT_ALLOWED", "Method not allowed.", List.of(), requestPath));
        }
    }
}
