package cms.views;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LoginView {

    private LoginView() {}

    public static Map<String, Object> render(Map<String, Object> opts) {
        Object csrf = opts.get("csrf");
        String error = (String) opts.get("error");
        String username = (String) opts.getOrDefault("username", "");

        String errorBlock = error != null && !error.isEmpty()
            ? "<div role=\"alert\"><p>" + Layout.escapeHtml(error) + "</p></div>"
            : "";
        String body =
            "\n" + errorBlock + "\n" +
            "<form method=\"POST\" action=\"/login\">\n" +
            Layout.csrfField(csrf) + "\n" +
            "<p>\n" +
            "<label for=\"field-username\">Username</label><br>\n" +
            "<input id=\"field-username\" name=\"username\" type=\"text\" value=\"" + Layout.escapeHtml(username) + "\" required autocomplete=\"username\">\n" +
            "</p>\n" +
            "<p>\n" +
            "<label for=\"field-password\">Password</label><br>\n" +
            "<input id=\"field-password\" name=\"password\" type=\"password\" required autocomplete=\"current-password\">\n" +
            "</p>\n" +
            "<p><button type=\"submit\">Sign in</button></p>\n" +
            "</form>";

        Map<String, Object> layoutOpts = new LinkedHashMap<>();
        layoutOpts.put("title", "Sign in");
        layoutOpts.put("csrf", csrf);
        layoutOpts.put("body", body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", error != null && !error.isEmpty() ? 401 : 200);
        out.put("html", Layout.layout(layoutOpts));
        return out;
    }
}
