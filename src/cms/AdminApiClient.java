package cms;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The admin API client. Static auth routes (login/logout/me) drive the server's
 * login flow; an instance bound to a session token carries the bearer on every
 * entity call. A 401 on a bound call means the session is invalid or expired
 * upstream — it surfaces as a SessionExpiredException so the server clears the
 * cookie and redirects to the login page.
 */
public final class AdminApiClient {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final String token;

    private AdminApiClient(String token) {
        this.token = token;
    }

    public static AdminApiClient forToken(String token) {
        return new AdminApiClient(token);
    }

    public static String baseUrl() {
        String env = System.getenv("API_BASE_URL");
        String url = (env == null || env.isEmpty()) ? "http://localhost:3006" : env;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
            default: throw new IllegalArgumentException("Unknown entity for plural lookup: " + entity);
        }
    }

    /** Raised when a bound request gets 401 from the API — the session is gone. */
    public static final class SessionExpiredException extends RuntimeException {
        public SessionExpiredException() { super("Session expired."); }
    }

    public static final class Response {
        public final int status;
        public final Object body;
        public final String etag;

        public Response(int status, Object body, String etag) {
            this.status = status;
            this.body = body;
            this.etag = etag;
        }
    }

    private static Response request(String method, String path, String bearer, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
            if (bearer != null) b.header("Authorization", "Bearer " + bearer);
            HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.stringify(body));
            if (body != null) b.header("Content-Type", "application/json");
            b.method(method, publisher);
            HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String etag = r.headers().firstValue("etag").orElse(null);
            Object parsed = null;
            String raw = r.body();
            if (raw != null && !raw.isEmpty()) {
                try { parsed = Json.parse(raw); } catch (Json.JsonException e) { parsed = Map.of("raw", raw); }
            }
            return new Response(r.statusCode(), parsed, etag);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", "AdminApiClient request failed: " + e.getMessage());
            return new Response(0, err, null);
        }
    }

    // Auth routes — driven by the server's login/logout flow, not by the views.
    // They return the raw status so the server can map credentials to cookies.
    public static Response login(String username, String password) {
        Map<String, Object> creds = new LinkedHashMap<>();
        creds.put("username", username);
        creds.put("password", password);
        return request("POST", "/auth/login", null, creds);
    }

    public static Response logout(String token) {
        return request("POST", "/auth/logout", token, null);
    }

    public static Response me(String token) {
        return request("GET", "/auth/me", token, null);
    }

    // A session-bound call: carries the bearer, and a 401 becomes a
    // SessionExpiredException.
    private Response authed(String method, String path, Object body) {
        Response r = request(method, path, token, body);
        if (r.status == 401) throw new SessionExpiredException();
        return r;
    }

    public Response list(String entity, Map<String, Object> query) {
        StringBuilder qs = new StringBuilder();
        if (query != null) {
            for (Map.Entry<String, Object> e : query.entrySet()) {
                Object v = e.getValue();
                if (v == null || v.toString().isEmpty()) continue;
                if (qs.length() > 0) qs.append('&');
                qs.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
                qs.append('=');
                qs.append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
            }
        }
        String path = "/" + pluralOf(entity) + (qs.length() > 0 ? "?" + qs : "");
        return authed("GET", path, null);
    }

    public Response get(String entity, String id) {
        return authed("GET", "/" + pluralOf(entity) + "/" + URLEncoder.encode(id, StandardCharsets.UTF_8), null);
    }

    public Response create(String entity, Map<String, Object> payload) {
        return authed("POST", "/" + pluralOf(entity), payload);
    }

    public Response update(String entity, String id, Map<String, Object> payload) {
        return authed("PUT", "/" + pluralOf(entity) + "/" + URLEncoder.encode(id, StandardCharsets.UTF_8), payload);
    }

    public Response remove(String entity, String id) {
        return authed("DELETE", "/" + pluralOf(entity) + "/" + URLEncoder.encode(id, StandardCharsets.UTF_8), null);
    }
}
