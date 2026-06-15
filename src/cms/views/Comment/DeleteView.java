package cms.views.Comment;

import cms.AdminApiClient;
import cms.views.Layout;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeleteView {

    public static final String ENTITY = "Comment";
    public static final String BASE = "/comments";

    private DeleteView() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> renderForm(Map<String, Object> opts) {
        AdminApiClient api = (AdminApiClient) opts.get("api");
        Object user = opts.get("user");
        Object csrf = opts.get("csrf");
        String id = (String) opts.get("id");
        AdminApiClient.Response r = api.get(ENTITY, id);
        if (r.status == 404) return Layout.errorPage(404, ENTITY + " not found.", user);
        if (r.status != 200) {
            String msg = "Failed to load.";
            if (r.body instanceof Map && ((Map<?, ?>) r.body).get("message") instanceof String) msg = (String) ((Map<?, ?>) r.body).get("message");
            return Layout.errorPage(r.status, msg, user);
        }
        String idEsc = Layout.escapeHtml(id);
        String body =
            "<form method=\"POST\" action=\"" + BASE + "/" + idEsc + "/delete\">\n" +
            Layout.csrfField(csrf) + "\n" +
            "<p>Delete <strong>" + Layout.escapeHtml(Layout.displayName((Map<String, Object>) r.body, ENTITY)) + "</strong>? This cannot be undone.</p>\n" +
            "<p><button type=\"submit\">Confirm Delete</button> · <a href=\"" + BASE + "/" + idEsc + "\">Cancel</a></p>\n" +
            "</form>";
        Map<String, Object> opts2 = new LinkedHashMap<>();
        opts2.put("title", "Delete " + ENTITY);
        opts2.put("currentEntity", ENTITY);
        if (user != null) opts2.put("user", user);
        opts2.put("csrf", csrf);
        opts2.put("body", body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        out.put("html", Layout.layout(opts2));
        return out;
    }

    public static Map<String, Object> handleSubmit(Map<String, Object> opts) {
        AdminApiClient api = (AdminApiClient) opts.get("api");
        Object user = opts.get("user");
        String id = (String) opts.get("id");
        AdminApiClient.Response r = api.remove(ENTITY, id);
        if (r.status == 204 || r.status == 404) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", 303);
            out.put("redirect", BASE);
            return out;
        }
        return Layout.errorPage(r.status, "Delete failed.", user);
    }
}
