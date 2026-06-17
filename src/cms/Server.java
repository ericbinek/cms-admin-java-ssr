package cms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import cms.views.Layout;
import cms.views.LoginView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class Server {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE);
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    @FunctionalInterface
    public interface RenderFn { Map<String, Object> apply(Map<String, Object> opts); }

    public static final class EntityRoute {
        public final String entity;
        public final String plural;
        public final RenderFn list;
        public final RenderFn detail;
        public final RenderFn createForm;
        public final RenderFn createSubmit;
        public final RenderFn editForm;
        public final RenderFn editSubmit;
        public final RenderFn deleteForm;
        public final RenderFn deleteSubmit;

        public EntityRoute(String entity, String plural,
                           RenderFn list, RenderFn detail,
                           RenderFn createForm, RenderFn createSubmit,
                           RenderFn editForm, RenderFn editSubmit,
                           RenderFn deleteForm, RenderFn deleteSubmit) {
            this.entity = entity; this.plural = plural;
            this.list = list; this.detail = detail;
            this.createForm = createForm; this.createSubmit = createSubmit;
            this.editForm = editForm; this.editSubmit = editSubmit;
            this.deleteForm = deleteForm; this.deleteSubmit = deleteSubmit;
        }
    }

    public static final List<EntityRoute> ROUTES = new ArrayList<>();
    static {
        ROUTES.add(new EntityRoute("BlogPosting", "blog-postings", cms.views.BlogPosting.ListView::render, cms.views.BlogPosting.DetailView::render, cms.views.BlogPosting.CreateView::renderForm, cms.views.BlogPosting.CreateView::handleSubmit, cms.views.BlogPosting.EditView::renderForm, cms.views.BlogPosting.EditView::handleSubmit, cms.views.BlogPosting.DeleteView::renderForm, cms.views.BlogPosting.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("Person", "persons", cms.views.Person.ListView::render, cms.views.Person.DetailView::render, cms.views.Person.CreateView::renderForm, cms.views.Person.CreateView::handleSubmit, cms.views.Person.EditView::renderForm, cms.views.Person.EditView::handleSubmit, cms.views.Person.DeleteView::renderForm, cms.views.Person.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("Organization", "organizations", cms.views.Organization.ListView::render, cms.views.Organization.DetailView::render, cms.views.Organization.CreateView::renderForm, cms.views.Organization.CreateView::handleSubmit, cms.views.Organization.EditView::renderForm, cms.views.Organization.EditView::handleSubmit, cms.views.Organization.DeleteView::renderForm, cms.views.Organization.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("WebPage", "web-pages", cms.views.WebPage.ListView::render, cms.views.WebPage.DetailView::render, cms.views.WebPage.CreateView::renderForm, cms.views.WebPage.CreateView::handleSubmit, cms.views.WebPage.EditView::renderForm, cms.views.WebPage.EditView::handleSubmit, cms.views.WebPage.DeleteView::renderForm, cms.views.WebPage.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("ImageObject", "image-objects", cms.views.ImageObject.ListView::render, cms.views.ImageObject.DetailView::render, cms.views.ImageObject.CreateView::renderForm, cms.views.ImageObject.CreateView::handleSubmit, cms.views.ImageObject.EditView::renderForm, cms.views.ImageObject.EditView::handleSubmit, cms.views.ImageObject.DeleteView::renderForm, cms.views.ImageObject.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("VideoObject", "video-objects", cms.views.VideoObject.ListView::render, cms.views.VideoObject.DetailView::render, cms.views.VideoObject.CreateView::renderForm, cms.views.VideoObject.CreateView::handleSubmit, cms.views.VideoObject.EditView::renderForm, cms.views.VideoObject.EditView::handleSubmit, cms.views.VideoObject.DeleteView::renderForm, cms.views.VideoObject.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("AudioObject", "audio-objects", cms.views.AudioObject.ListView::render, cms.views.AudioObject.DetailView::render, cms.views.AudioObject.CreateView::renderForm, cms.views.AudioObject.CreateView::handleSubmit, cms.views.AudioObject.EditView::renderForm, cms.views.AudioObject.EditView::handleSubmit, cms.views.AudioObject.DeleteView::renderForm, cms.views.AudioObject.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("CategoryCode", "category-codes", cms.views.CategoryCode.ListView::render, cms.views.CategoryCode.DetailView::render, cms.views.CategoryCode.CreateView::renderForm, cms.views.CategoryCode.CreateView::handleSubmit, cms.views.CategoryCode.EditView::renderForm, cms.views.CategoryCode.EditView::handleSubmit, cms.views.CategoryCode.DeleteView::renderForm, cms.views.CategoryCode.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("CategoryCodeSet", "category-code-sets", cms.views.CategoryCodeSet.ListView::render, cms.views.CategoryCodeSet.DetailView::render, cms.views.CategoryCodeSet.CreateView::renderForm, cms.views.CategoryCodeSet.CreateView::handleSubmit, cms.views.CategoryCodeSet.EditView::renderForm, cms.views.CategoryCodeSet.EditView::handleSubmit, cms.views.CategoryCodeSet.DeleteView::renderForm, cms.views.CategoryCodeSet.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("DefinedTerm", "defined-terms", cms.views.DefinedTerm.ListView::render, cms.views.DefinedTerm.DetailView::render, cms.views.DefinedTerm.CreateView::renderForm, cms.views.DefinedTerm.CreateView::handleSubmit, cms.views.DefinedTerm.EditView::renderForm, cms.views.DefinedTerm.EditView::handleSubmit, cms.views.DefinedTerm.DeleteView::renderForm, cms.views.DefinedTerm.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("DefinedTermSet", "defined-term-sets", cms.views.DefinedTermSet.ListView::render, cms.views.DefinedTermSet.DetailView::render, cms.views.DefinedTermSet.CreateView::renderForm, cms.views.DefinedTermSet.CreateView::handleSubmit, cms.views.DefinedTermSet.EditView::renderForm, cms.views.DefinedTermSet.EditView::handleSubmit, cms.views.DefinedTermSet.DeleteView::renderForm, cms.views.DefinedTermSet.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("Comment", "comments", cms.views.Comment.ListView::render, cms.views.Comment.DetailView::render, cms.views.Comment.CreateView::renderForm, cms.views.Comment.CreateView::handleSubmit, cms.views.Comment.EditView::renderForm, cms.views.Comment.EditView::handleSubmit, cms.views.Comment.DeleteView::renderForm, cms.views.Comment.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("WebSite", "web-sites", cms.views.WebSite.ListView::render, cms.views.WebSite.DetailView::render, cms.views.WebSite.CreateView::renderForm, cms.views.WebSite.CreateView::handleSubmit, cms.views.WebSite.EditView::renderForm, cms.views.WebSite.EditView::handleSubmit, cms.views.WebSite.DeleteView::renderForm, cms.views.WebSite.DeleteView::handleSubmit));
        ROUTES.add(new EntityRoute("SiteNavigationElement", "site-navigation-elements", cms.views.SiteNavigationElement.ListView::render, cms.views.SiteNavigationElement.DetailView::render, cms.views.SiteNavigationElement.CreateView::renderForm, cms.views.SiteNavigationElement.CreateView::handleSubmit, cms.views.SiteNavigationElement.EditView::renderForm, cms.views.SiteNavigationElement.EditView::handleSubmit, cms.views.SiteNavigationElement.DeleteView::renderForm, cms.views.SiteNavigationElement.DeleteView::handleSubmit));
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "5006"));
        String host = System.getenv().getOrDefault("HOST", "0.0.0.0");
        HttpServer server = create(host, port);
        server.start();
        System.err.println("CMS admin running at http://" + host + ":" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(2)));
    }

    public static HttpServer create(String host, int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", new MainHandler());
        return server;
    }

    // Per-request mutable context: the cookies to set on the response, and the
    // session token and csrf token resolved from the request cookies.
    private static final class RequestCtx {
        final List<String> setCookies = new ArrayList<>();
        String sessionToken;
        String csrf;
    }

    private static class MainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            Map<String, String> cookies = AdminAuth.parseCookies(exchange.getRequestHeaders().getFirst("Cookie"));
            RequestCtx ctx = new RequestCtx();
            ctx.sessionToken = cookies.getOrDefault(AdminAuth.SESSION_COOKIE, null);
            if (ctx.sessionToken != null && ctx.sessionToken.isEmpty()) ctx.sessionToken = null;
            // Issue a CSRF token if the browser has none yet; never rotate an
            // existing one (it would invalidate a form open in another tab).
            ctx.csrf = cookies.get(AdminAuth.CSRF_COOKIE);
            if (ctx.csrf == null || ctx.csrf.isEmpty()) {
                ctx.csrf = AdminAuth.randomToken();
                ctx.setCookies.add(AdminAuth.setCsrfCookie(ctx.csrf));
            }

            try {
                if ("GET".equals(method) && "/health".equals(path)) {
                    byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                    return;
                }
                if ("GET".equals(method) && "/style.css".equals(path)) {
                    serveStatic(exchange, "public/style.css", "text/css; charset=utf-8", ctx);
                    return;
                }

                if ("POST".equals(method)) {
                    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
                        sendHtml(exchange, 415, Layout.layout(Map.of("title", "Unsupported", "body", "<p role=\"alert\">Form encoding required.</p>")), ctx);
                        return;
                    }
                    ReadResult read = readFormBody(exchange);
                    if (read.oversized) {
                        sendHtml(exchange, 413, Layout.layout(Map.of("title", "Too Large", "body", "<p role=\"alert\">Request body too large.</p>")), ctx);
                        return;
                    }
                    String form = read.body;
                    // CSRF: the submitted token must match the cookie set on a prior GET.
                    if (!AdminAuth.csrfValid(cookies.get(AdminAuth.CSRF_COOKIE), formField(form, "_csrf"))) {
                        sendHtml(exchange, 403, Layout.layout(Map.of("title", "Forbidden", "body", "<p role=\"alert\">Invalid or missing CSRF token. Reload the form and try again.</p>")), ctx);
                        return;
                    }
                    handlePost(exchange, path, form, ctx);
                    return;
                }

                if ("GET".equals(method)) {
                    handleGet(exchange, path, ctx);
                    return;
                }

                sendHtml(exchange, 404, Layout.layout(Map.of("title", "Not Found", "body", "<p role=\"alert\">Page not found.</p>")), ctx);
            } catch (AdminApiClient.SessionExpiredException e) {
                ctx.setCookies.add(AdminAuth.clearSessionCookie());
                sendRedirect(exchange, "/login", 303, ctx);
            } catch (Exception e) {
                System.err.println("[" + method + " " + path + "] " + e.getMessage());
                try {
                    sendHtml(exchange, 500, Layout.layout(Map.of("title", "Error", "body", "<p role=\"alert\">Internal server error.</p>")), ctx);
                } catch (IOException ignored) {}
            } finally {
                exchange.close();
            }
        }
    }

    // Resolves and validates the session by asking the API who we are. A 401 means
    // the session is gone — surfaced as SessionExpiredException so the caller
    // redirects to login. Doubles as the per-request principal lookup for the
    // header.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireUser(String token) {
        AdminApiClient.Response r = AdminApiClient.me(token);
        if (r.status == 401 || !(r.body instanceof Map) || !((Map<?, ?>) r.body).containsKey("account")) {
            throw new AdminApiClient.SessionExpiredException();
        }
        return (Map<String, Object>) ((Map<String, Object>) r.body).get("account");
    }

    private static void handleGet(HttpExchange exchange, String path, RequestCtx ctx) throws IOException {
        if ("/login".equals(path)) {
            // Already carrying a session: go to the dashboard. A stale cookie
            // bounces back here (cleared) on the first failing API call.
            if (ctx.sessionToken != null) { sendRedirect(exchange, "/", 303, ctx); return; }
            sendResponse(exchange, LoginView.render(Map.of("csrf", ctx.csrf)), ctx);
            return;
        }

        if (ctx.sessionToken == null) { sendRedirect(exchange, "/login", 303, ctx); return; }
        Map<String, Object> user = requireUser(ctx.sessionToken);
        AdminApiClient api = AdminApiClient.forToken(ctx.sessionToken);

        if ("/".equals(path)) { sendResponse(exchange, indexPage(user, ctx.csrf), ctx); return; }

        Match m = matchEntityRoute(path);
        if (m == null) { sendHtml(exchange, 404, notFoundHtml(user, ctx.csrf), ctx); return; }
        EntityRoute r = m.route;
        String kind = m.kind;
        String id = m.id;
        boolean idValid = id == null || UUID_PATTERN.matcher(id).matches();

        if ("list".equals(kind)) {
            sendResponse(exchange, r.list.apply(opts(api, ctx.csrf, user, Map.of("url", exchange.getRequestURI().toString()))), ctx);
            return;
        }
        if ("new".equals(kind)) {
            sendResponse(exchange, r.createForm.apply(opts(api, ctx.csrf, user, Map.of())), ctx);
            return;
        }
        if (!idValid) { sendHtml(exchange, 400, invalidIdHtml(user, ctx.csrf), ctx); return; }
        if ("detail".equals(kind)) {
            sendResponse(exchange, r.detail.apply(opts(api, ctx.csrf, user, Map.of("id", id))), ctx);
            return;
        }
        if ("edit".equals(kind)) {
            sendResponse(exchange, r.editForm.apply(opts(api, ctx.csrf, user, Map.of("id", id))), ctx);
            return;
        }
        if ("delete".equals(kind)) {
            sendResponse(exchange, r.deleteForm.apply(opts(api, ctx.csrf, user, Map.of("id", id))), ctx);
            return;
        }
        sendHtml(exchange, 404, notFoundHtml(user, ctx.csrf), ctx);
    }

    private static void handlePost(HttpExchange exchange, String path, String form, RequestCtx ctx) throws IOException {
        if ("/login".equals(path)) {
            String username = formField(form, "username");
            username = username == null ? "" : username.trim();
            String password = formField(form, "password");
            password = password == null ? "" : password;
            if (username.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, LoginView.render(loginOpts(ctx.csrf, "Username and password are required.", username)), ctx);
                return;
            }
            AdminApiClient.Response r = AdminApiClient.login(username, password);
            if (r.status == 200 && r.body instanceof Map && ((Map<?, ?>) r.body).get("token") instanceof String) {
                ctx.setCookies.add(AdminAuth.setSessionCookie((String) ((Map<?, ?>) r.body).get("token")));
                sendRedirect(exchange, "/", 303, ctx);
                return;
            }
            sendResponse(exchange, LoginView.render(loginOpts(ctx.csrf, "Invalid username or password.", username)), ctx);
            return;
        }

        if ("/logout".equals(path)) {
            if (ctx.sessionToken != null) {
                try { AdminApiClient.logout(ctx.sessionToken); } catch (Exception ignored) { /* best effort, cookie is cleared anyway */ }
            }
            ctx.setCookies.add(AdminAuth.clearSessionCookie());
            sendRedirect(exchange, "/login", 303, ctx);
            return;
        }

        if (ctx.sessionToken == null) { sendRedirect(exchange, "/login", 303, ctx); return; }
        Map<String, Object> user = requireUser(ctx.sessionToken);
        AdminApiClient api = AdminApiClient.forToken(ctx.sessionToken);

        Match m = matchEntityRoute(path);
        if (m == null) { sendHtml(exchange, 404, notFoundHtml(user, ctx.csrf), ctx); return; }
        EntityRoute r = m.route;
        String kind = m.kind;
        String id = m.id;
        boolean idValid = id == null || UUID_PATTERN.matcher(id).matches();

        if ("new".equals(kind)) {
            Map<String, Object> result = r.createSubmit.apply(opts(api, ctx.csrf, user, Map.of("form", form)));
            if (result.containsKey("redirect")) { sendRedirect(exchange, (String) result.get("redirect"), (int) result.getOrDefault("status", 303), ctx); return; }
            if (result.containsKey("html")) { sendHtml(exchange, (int) result.getOrDefault("status", 400), (String) result.get("html"), ctx); return; }
            Map<String, Object> retry = new LinkedHashMap<>();
            retry.put("errors", result.getOrDefault("errors", List.of()));
            retry.put("values", result.getOrDefault("values", Map.of()));
            sendResponse(exchange, r.createForm.apply(opts(api, ctx.csrf, user, retry)), ctx);
            return;
        }
        if (!idValid) { sendHtml(exchange, 400, invalidIdHtml(user, ctx.csrf), ctx); return; }
        if ("edit".equals(kind)) {
            Map<String, Object> editOpts = new LinkedHashMap<>();
            editOpts.put("id", id);
            editOpts.put("form", form);
            Map<String, Object> result = r.editSubmit.apply(opts(api, ctx.csrf, user, editOpts));
            if (result.containsKey("redirect")) { sendRedirect(exchange, (String) result.get("redirect"), (int) result.getOrDefault("status", 303), ctx); return; }
            if (result.containsKey("html")) { sendHtml(exchange, (int) result.getOrDefault("status", 400), (String) result.get("html"), ctx); return; }
            Map<String, Object> retry = new LinkedHashMap<>();
            retry.put("id", id);
            retry.put("errors", result.getOrDefault("errors", List.of()));
            retry.put("values", result.getOrDefault("values", Map.of()));
            sendResponse(exchange, r.editForm.apply(opts(api, ctx.csrf, user, retry)), ctx);
            return;
        }
        if ("delete".equals(kind)) {
            Map<String, Object> result = r.deleteSubmit.apply(opts(api, ctx.csrf, user, Map.of("id", id)));
            sendResponse(exchange, result, ctx);
            return;
        }
        sendHtml(exchange, 404, notFoundHtml(user, ctx.csrf), ctx);
    }

    // Merge the shared admin context (bound api client, csrf token, signed-in
    // user) with the per-call options the view needs.
    private static Map<String, Object> opts(AdminApiClient api, String csrf, Map<String, Object> user, Map<String, Object> extra) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("api", api);
        out.put("csrf", csrf);
        out.put("user", user);
        out.putAll(extra);
        return out;
    }

    private static Map<String, Object> loginOpts(String csrf, String error, String username) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("csrf", csrf);
        out.put("error", error);
        out.put("username", username);
        return out;
    }

    private static Map<String, Object> indexPage(Map<String, Object> user, String csrf) {
        StringBuilder items = new StringBuilder();
        for (EntityRoute r : ROUTES) {
            items.append("<li><a href=\"/").append(r.plural).append("\">").append(Layout.escapeHtml(r.entity)).append("</a></li>");
        }
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("title", "Dashboard");
        opts.put("user", user);
        opts.put("csrf", csrf);
        opts.put("body", "<p>Manage content for " + ROUTES.size() + " entity types.</p><ul>" + items + "</ul>");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        out.put("html", Layout.layout(opts));
        return out;
    }

    private static String notFoundHtml(Map<String, Object> user, String csrf) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("title", "Not Found");
        if (user != null) opts.put("user", user);
        opts.put("csrf", csrf);
        opts.put("body", "<p role=\"alert\">Page not found.</p>");
        return Layout.layout(opts);
    }

    private static String invalidIdHtml(Map<String, Object> user, String csrf) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("title", "Invalid ID");
        if (user != null) opts.put("user", user);
        opts.put("csrf", csrf);
        opts.put("body", "<p role=\"alert\">ID must be a valid UUID.</p>");
        return Layout.layout(opts);
    }

    private static final class Match {
        EntityRoute route; String kind; String id;
        Match(EntityRoute route, String kind, String id) { this.route = route; this.kind = kind; this.id = id; }
    }

    private static Match matchEntityRoute(String path) {
        for (EntityRoute r : ROUTES) {
            String base = "/" + r.plural;
            if (path.equals(base)) return new Match(r, "list", null);
            if (path.equals(base + "/new")) return new Match(r, "new", null);
            if (path.startsWith(base + "/")) {
                String rest = path.substring(base.length() + 1);
                int slash = rest.indexOf('/');
                if (slash < 0) return new Match(r, "detail", rest);
                String head = rest.substring(0, slash);
                String action = rest.substring(slash + 1);
                if (!action.equals("edit") && !action.equals("delete")) continue;
                return new Match(r, action, head);
            }
        }
        return null;
    }

    private static void applyCookies(HttpExchange exchange, RequestCtx ctx) {
        for (String c : ctx.setCookies) exchange.getResponseHeaders().add("Set-Cookie", c);
    }

    private static void sendHtml(HttpExchange exchange, int status, String html, RequestCtx ctx) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        applyCookies(exchange, ctx);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
    }

    private static void sendRedirect(HttpExchange exchange, String location, int status, RequestCtx ctx) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        applyCookies(exchange, ctx);
        exchange.sendResponseHeaders(status, -1);
    }

    private static void sendResponse(HttpExchange exchange, Map<String, Object> response, RequestCtx ctx) throws IOException {
        if (response.containsKey("redirect")) {
            int status = (int) response.getOrDefault("status", 303);
            sendRedirect(exchange, (String) response.get("redirect"), status, ctx);
            return;
        }
        int status = (int) response.getOrDefault("status", 200);
        sendHtml(exchange, status, (String) response.get("html"), ctx);
    }

    private static void serveStatic(HttpExchange exchange, String relPath, String contentType, RequestCtx ctx) throws IOException {
        Path path = Path.of(relPath);
        if (!Files.isRegularFile(path)) {
            sendHtml(exchange, 404, notFoundHtml(null, ctx.csrf), ctx);
            return;
        }
        byte[] body = Files.readAllBytes(path);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
        applyCookies(exchange, ctx);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
    }

    private static final class ReadResult {
        final String body; final boolean oversized;
        ReadResult(String body, boolean oversized) { this.body = body; this.oversized = oversized; }
    }

    private static ReadResult readFormBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        int total = 0;
        boolean oversized = false;
        while ((n = is.read(chunk)) > 0) {
            total += n;
            if (total > MAX_BODY_SIZE) { oversized = true; continue; }
            buf.write(chunk, 0, n);
        }
        if (oversized) return new ReadResult("", true);
        return new ReadResult(buf.toString(StandardCharsets.UTF_8), false);
    }

    // Pull a single decoded field value out of an x-www-form-urlencoded body.
    // Returns null when the key is absent.
    private static String formField(String form, String key) {
        if (form == null || form.isEmpty()) return null;
        for (String pair : form.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            if (!decodeFormPart(k).equals(key)) continue;
            return eq < 0 ? "" : decodeFormPart(pair.substring(eq + 1));
        }
        return null;
    }

    private static String decodeFormPart(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace('+', ' '), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
