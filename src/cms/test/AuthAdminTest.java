package cms.test;

import java.util.Map;
import java.util.regex.Pattern;

public final class AuthAdminTest {

    public static final String ENTITY = "BlogPosting";
    public static final String BASE = "/blog-postings";

    private AuthAdminTest() {}

    public static void run(TestRunner.TestContext ctx) {
        ctx.scenario("unauthenticated dashboard redirects to login", () -> {
            Helpers.Response r = Helpers.adminGet("/");
            Assert.equal(303, r.status);
            Assert.equal("/login", r.headers.get("location"));
        });

        ctx.scenario("unauthenticated entity route redirects to login", () -> {
            Helpers.Response r = Helpers.adminGet(BASE);
            Assert.equal(303, r.status);
            Assert.equal("/login", r.headers.get("location"));
        });

        ctx.scenario("GET /login renders a sign-in form", () -> {
            Helpers.Response r = Helpers.adminGet("/login");
            Assert.equal(200, r.status);
            Assert.match(Pattern.compile("<form[^>]+method=\"POST\"[^>]+action=\"/login\""), r.body, "login form");
            Assert.match(Pattern.compile("type=\"password\""), r.body, "password field");
            Assert.isTrue(r.body.contains("name=\"_csrf\""), "login form carries a CSRF field");
        });

        ctx.scenario("login with wrong credentials returns 401 with an alert", () -> {
            Map<String, String> jar = new java.util.LinkedHashMap<>();
            Helpers.adminGet("/login", jar);
            Helpers.Response r = Helpers.adminPostForm("/login", "username=admin&password=wrong", jar);
            Assert.equal(401, r.status);
            Assert.match(Pattern.compile("role=\"alert\""), r.body, "alert");
        });

        ctx.scenario("login sets an HttpOnly, SameSite=Strict session cookie and redirects to dashboard", () -> {
            Map<String, String> jar = new java.util.LinkedHashMap<>();
            Helpers.adminGet("/login", jar);
            Helpers.Response r = Helpers.adminPostForm("/login", "username=admin&password=admin-password", jar);
            Assert.equal(303, r.status);
            Assert.equal("/", r.headers.get("location"));
            String setCookies = String.join("\n", r.setCookies);
            Assert.isTrue(setCookies.contains(Helpers.SESSION_COOKIE + "="), "session cookie is set");
            Assert.match(Pattern.compile("(?i)HttpOnly"), setCookies, "session cookie is HttpOnly");
            Assert.match(Pattern.compile("(?i)SameSite=Strict"), setCookies, "session cookie is SameSite=Strict");
        });

        ctx.scenario("authenticated dashboard renders after login", () -> {
            Map<String, String> jar = Helpers.loginAdmin();
            Helpers.Response r = Helpers.adminGet("/", jar);
            Assert.equal(200, r.status);
            Assert.isTrue(r.body.contains("Dashboard"), "body shows the dashboard");
            Assert.isTrue(r.body.contains("Sign out"), "header offers sign out");
        });

        ctx.scenario("state-changing POST without a CSRF token is rejected with 403", () -> {
            Map<String, String> jar = Helpers.loginAdmin();
            String body = Helpers.formBodyFor(ENTITY, jar);
            Helpers.Response r = Helpers.adminPostForm(BASE + "/new", body, jar, false);
            Assert.equal(403, r.status);
        });

        ctx.scenario("state-changing POST with a wrong CSRF token is rejected with 403", () -> {
            Map<String, String> jar = Helpers.loginAdmin();
            String body = Helpers.formBodyFor(ENTITY, jar) + "&_csrf=not-the-real-token";
            Helpers.Response r = Helpers.adminPostForm(BASE + "/new", body, jar, false);
            Assert.equal(403, r.status);
        });

        ctx.scenario("logout clears the session and protected routes redirect to login again", () -> {
            Map<String, String> jar = Helpers.loginAdmin();
            Helpers.Response out = Helpers.adminPostForm("/logout", "", jar);
            Assert.equal(303, out.status);
            Assert.equal("/login", out.headers.get("location"));
            Helpers.Response after = Helpers.adminGet("/", jar);
            Assert.equal(303, after.status);
            Assert.equal("/login", after.headers.get("location"));
        });
    }
}
