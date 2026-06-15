package cms.test;

import cms.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test helpers for the admin conformance suite: a cookie jar (a plain name->value
 * map), a browser-like login dance (GET /login for the csrf cookie, then POST the
 * credentials), and seeding that goes straight to the mock API with the admin
 * bearer token. Cookie names are kept in sync with AdminAuth.
 */
public final class Helpers {

    public static final String SESSION_COOKIE = "cms_session";
    public static final String CSRF_COOKIE = "cms_csrf";

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin-password";

    private static String apiBase = "";
    private static String adminBase = "";
    private static final Map<String, String> SEEDED = new LinkedHashMap<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private Helpers() {}

    public static void setApiBase(String url) { apiBase = trimSlash(url); }
    public static void setAdminBase(String url) { adminBase = trimSlash(url); }
    public static String getApiBase() { return apiBase; }
    public static String getAdminBase() { return adminBase; }
    public static void resetSeedCache() { SEEDED.clear(); }

    private static String trimSlash(String s) { return s.endsWith("/") ? s.substring(0, s.length() - 1) : s; }

    public static boolean waitForHealth(String baseUrl, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                Response r = httpRequest("GET", baseUrl + "/health", null, Map.of());
                if (r.status == 200) return true;
            } catch (Exception ignored) {}
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    public static String pluralOf(String entity) {
        switch (entity) {
            case "BlogPosting": return "blog-postings";
            case "Person": return "persons";
            case "WebPage": return "web-pages";
            case "ImageObject": return "image-objects";
            case "CategoryCode": return "category-codes";
            case "CategoryCodeSet": return "category-code-sets";
            case "DefinedTerm": return "defined-terms";
            case "DefinedTermSet": return "defined-term-sets";
            case "Comment": return "comments";
            case "WebSite": return "web-sites";
            default: throw new RuntimeException("Unknown entity: " + entity);
        }
    }

    public static Map<String, Object> sampleFor(String entity) {
        switch (entity) {
        case "BlogPosting": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("headline", "sample");
            sample.put("articleBody", "sample");
            sample.put("author", Map.of("__ref", "Person"));
            return sample;
        }
        case "Person": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("name", "sample");
            return sample;
        }
        case "WebPage": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("headline", "sample");
            return sample;
        }
        case "ImageObject": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("contentUrl", "https://example.com/x");
            return sample;
        }
        case "CategoryCode": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("name", "sample");
            sample.put("codeValue", "sample");
            sample.put("inCodeSet", Map.of("__ref", "CategoryCodeSet"));
            return sample;
        }
        case "CategoryCodeSet": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("name", "sample");
            return sample;
        }
        case "DefinedTerm": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("name", "sample");
            sample.put("termCode", "sample");
            sample.put("inDefinedTermSet", Map.of("__ref", "DefinedTermSet"));
            return sample;
        }
        case "DefinedTermSet": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("name", "sample");
            return sample;
        }
        case "Comment": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("text", "sample");
            sample.put("author", Map.of("__ref", "Person"));
            sample.put("about", Map.of("__ref", "BlogPosting"));
            return sample;
        }
        case "WebSite": {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("name", "sample");
            sample.put("url", "https://example.com/x");
            return sample;
        }
            default: throw new RuntimeException("Unknown entity: " + entity);
        }
    }

    public static class Response {
        public final int status;
        public final Map<String, String> headers;
        public final List<String> setCookies;
        public final String body;

        public Response(int status, Map<String, String> headers, List<String> setCookies, String body) {
            this.status = status;
            this.headers = headers;
            this.setCookies = setCookies;
            this.body = body;
        }
    }

    public static Response httpRequest(String method, String url, String body, Map<String, String> headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10));
            HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
            for (Map.Entry<String, String> e : headers.entrySet()) b.header(e.getKey(), e.getValue());
            b.method(method, publisher);
            HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, String> hdrs = new LinkedHashMap<>();
            r.headers().map().forEach((k, v) -> { if (!v.isEmpty()) hdrs.put(k.toLowerCase(), v.get(0)); });
            List<String> setCookies = r.headers().allValues("Set-Cookie");
            return new Response(r.statusCode(), hdrs, setCookies, r.body());
        } catch (Exception e) {
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    // --- Cookie jar (a plain name -> value map) -------------------------------

    private static void applySetCookies(Map<String, String> jar, Response r) {
        for (String sc : r.setCookies) {
            String pair = sc.split(";", 2)[0];
            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            String name = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            if (value.isEmpty()) jar.remove(name); // Max-Age=0 clears with an empty value
            else jar.put(name, value);
        }
    }

    private static String cookieHeader(Map<String, String> jar) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static String apiToken(Map<String, String> jar) {
        return jar.get(SESSION_COOKIE);
    }

    public static Response adminGet(String path, Map<String, String> jar) {
        Map<String, String> headers = jar != null ? Map.of("Cookie", cookieHeader(jar)) : Map.of();
        Response r = httpRequest("GET", adminBase + path, null, headers);
        if (jar != null) applySetCookies(jar, r);
        return r;
    }

    public static Response adminGet(String path) {
        return adminGet(path, null);
    }

    public static Response adminPostForm(String path, String body, Map<String, String> jar, boolean withCsrf) {
        String finalBody = body == null ? "" : body;
        if (withCsrf && jar != null && jar.containsKey(CSRF_COOKIE) && !hasField(finalBody, "_csrf")) {
            finalBody = (finalBody.isEmpty() ? "" : finalBody + "&") + "_csrf=" + URLEncoder.encode(jar.get(CSRF_COOKIE), StandardCharsets.UTF_8);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        if (jar != null) headers.put("Cookie", cookieHeader(jar));
        Response r = httpRequest("POST", adminBase + path, finalBody, headers);
        if (jar != null) applySetCookies(jar, r);
        return r;
    }

    public static Response adminPostForm(String path, String body, Map<String, String> jar) {
        return adminPostForm(path, body, jar, true);
    }

    private static boolean hasField(String body, String key) {
        if (body == null || body.isEmpty()) return false;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            if (k.equals(key)) return true;
        }
        return false;
    }

    // Full browser-like login: GET /login to obtain the csrf cookie, then POST the
    // credentials. Returns a cookie jar carrying the session and csrf cookies.
    public static Map<String, String> loginAdmin() {
        Map<String, String> jar = new LinkedHashMap<>();
        adminGet("/login", jar);
        Response r = adminPostForm("/login",
            "username=" + URLEncoder.encode(ADMIN_USERNAME, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(ADMIN_PASSWORD, StandardCharsets.UTF_8),
            jar);
        if (r.status != 303) throw new RuntimeException("loginAdmin failed: expected 303, got " + r.status);
        return jar;
    }

    // --- Seeding goes straight to the mock API with the admin bearer token -----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveRefs(Map<String, Object> sample, Map<String, String> jar) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : sample.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List) {
                List<Object> list = new ArrayList<>();
                for (Object item : (List<?>) v) {
                    if (item instanceof Map && ((Map<?, ?>) item).containsKey("__ref")) {
                        list.add(ensureEntity((String) ((Map<?, ?>) item).get("__ref"), jar));
                    } else {
                        list.add(item);
                    }
                }
                out.put(e.getKey(), list);
            } else if (v instanceof Map && ((Map<?, ?>) v).containsKey("__ref")) {
                out.put(e.getKey(), ensureEntity((String) ((Map<?, ?>) v).get("__ref"), jar));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String seedToMock(String entity, Map<String, Object> payload, Map<String, String> jar) {
        Response r = httpRequest("POST", apiBase + "/" + pluralOf(entity),
            Json.stringify(payload),
            Map.of("Content-Type", "application/json", "Authorization", "Bearer " + apiToken(jar)));
        if (r.status != 201) throw new RuntimeException("seed(" + entity + ") failed: " + r.status + " " + r.body);
        Map<String, Object> body = (Map<String, Object>) Json.parse(r.body);
        return (String) body.get("id");
    }

    public static String ensureEntity(String entity, Map<String, String> jar) {
        if (SEEDED.containsKey(entity)) return SEEDED.get(entity);
        Map<String, Object> sample = resolveRefs(sampleFor(entity), jar);
        String id = seedToMock(entity, sample, jar);
        SEEDED.put(entity, id);
        return id;
    }

    // Seed one fresh entity with chosen field overrides, bypassing the seed cache.
    public static String seedWith(String entity, Map<String, Object> overrides, Map<String, String> jar) {
        Map<String, Object> payload = resolveRefs(sampleFor(entity), jar);
        payload.putAll(overrides);
        return seedToMock(entity, payload, jar);
    }

    private static String encodeOne(Object v) {
        if (v == null) return "";
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if ("Language".equals(m.get("@type"))) {
                Object alt = m.get("alternateName");
                return alt == null ? "" : alt.toString();
            }
            return Json.stringify(v);
        }
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        return v.toString();
    }

    public static String formBodyFor(String entity, Map<String, String> jar) {
        Map<String, Object> sample = resolveRefs(sampleFor(entity), jar);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sample.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List) {
                for (Object item : (List<?>) v) appendPair(sb, e.getKey(), encodeOne(item));
            } else {
                appendPair(sb, e.getKey(), encodeOne(v));
            }
        }
        return sb.toString();
    }

    private static void appendPair(StringBuilder sb, String k, String v) {
        if (sb.length() > 0) sb.append('&');
        sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8));
        sb.append('=');
        sb.append(URLEncoder.encode(v, StandardCharsets.UTF_8));
    }
}
