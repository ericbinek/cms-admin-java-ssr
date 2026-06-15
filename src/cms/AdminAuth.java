package cms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cookie and CSRF helper for the admin frontend. The session cookie carries the
 * API bearer token; the csrf cookie carries the synchronizer token rendered into
 * every state-changing form. Both cookies are HttpOnly and SameSite=Strict — the
 * server renders the csrf token into forms itself, so no client script needs to
 * read either cookie. Crypto is the JDK standard library: SecureRandom for tokens
 * and MessageDigest.isEqual for a constant-time CSRF compare.
 */
public final class AdminAuth {

    // Cookie names are admin-frontend internal; the API never reads them.
    public static final String SESSION_COOKIE = "cms_session";
    public static final String CSRF_COOKIE = "cms_csrf";

    // Both cookies live at most as long as the API session cap (8h). Secure is on
    // only behind HTTPS; set COOKIE_SECURE=true in production. SameSite=Strict and
    // HttpOnly are always on.
    private static final int MAX_AGE = 60 * 60 * 8;
    private static final boolean COOKIE_SECURE =
        "true".equalsIgnoreCase(System.getenv().getOrDefault("COOKIE_SECURE", ""));

    private static final SecureRandom RNG = new SecureRandom();

    private AdminAuth() {}

    public static Map<String, String> parseCookies(String header) {
        Map<String, String> out = new LinkedHashMap<>();
        if (header == null || header.isEmpty()) return out;
        for (String part : header.split(";")) {
            int idx = part.indexOf('=');
            if (idx < 0) continue;
            String name = part.substring(0, idx).trim();
            if (name.isEmpty()) continue;
            out.put(name, decode(part.substring(idx + 1).trim()));
        }
        return out;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String serialize(String name, String value, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(encode(value));
        sb.append("; Path=/");
        sb.append("; HttpOnly");
        sb.append("; SameSite=Strict");
        sb.append("; Max-Age=").append(maxAge);
        if (COOKIE_SECURE) sb.append("; Secure");
        return sb.toString();
    }

    public static String setSessionCookie(String token) {
        return serialize(SESSION_COOKIE, token, MAX_AGE);
    }

    public static String clearSessionCookie() {
        return serialize(SESSION_COOKIE, "", 0);
    }

    public static String setCsrfCookie(String token) {
        return serialize(CSRF_COOKIE, token, MAX_AGE);
    }

    public static String randomToken() {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    // Constant-time comparison of the cookie token against the submitted form
    // token. Null values or unequal lengths fail closed without leaking timing.
    public static boolean csrfValid(String cookieToken, String formToken) {
        if (cookieToken == null || formToken == null) return false;
        if (cookieToken.isEmpty() || cookieToken.length() != formToken.length()) return false;
        return MessageDigest.isEqual(
            cookieToken.getBytes(StandardCharsets.UTF_8),
            formToken.getBytes(StandardCharsets.UTF_8));
    }
}
