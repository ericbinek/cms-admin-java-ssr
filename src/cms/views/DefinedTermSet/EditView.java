package cms.views.DefinedTermSet;

import cms.AdminApiClient;
import cms.views.Layout;
import cms.views.PropertySpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EditView {

    public static final String ENTITY = "DefinedTermSet";
    public static final String BASE = "/defined-term-sets";
    public static final List<PropertySpec> PROPERTIES = new ArrayList<>();
    static {
        PROPERTIES.add(new PropertySpec.Scalar("name", "Text", PropertySpec.Cardinality.ONE, true));
        PROPERTIES.add(new PropertySpec.Scalar("description", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("url", "URL", PropertySpec.Cardinality.ONE, false));
    }

    private EditView() {}

    private static Map<String, List<Map<String, String>>> loadRefOptions(AdminApiClient api) { return Map.of(); }

    @SuppressWarnings("unchecked")
    private static List<String> extractErrorList(Object body) {
        if (body instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) body;
            Object details = m.get("details");
            if (details instanceof List && !((List<?>) details).isEmpty()) {
                List<String> out = new ArrayList<>();
                for (Object d : (List<?>) details) out.add(d.toString());
                return out;
            }
            Object message = m.get("message");
            if (message instanceof String) return List.of((String) message);
        }
        return List.of("Request failed.");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> renderForm(Map<String, Object> opts) {
        AdminApiClient api = (AdminApiClient) opts.get("api");
        Object user = opts.get("user");
        Object csrf = opts.get("csrf");
        String id = (String) opts.get("id");
        Map<String, Object> values = (Map<String, Object>) opts.get("values");
        List<String> errors = (List<String>) opts.getOrDefault("errors", List.of());
        Map<String, List<String>> fieldErrors = (Map<String, List<String>>) opts.getOrDefault("fieldErrors", Map.of());
        if (values == null) {
            AdminApiClient.Response r = api.get(ENTITY, id);
            if (r.status == 404) return Layout.errorPage(404, ENTITY + " not found.", user);
            if (r.status != 200) {
                String msg = "Failed to load.";
                if (r.body instanceof Map && ((Map<?, ?>) r.body).get("message") instanceof String) msg = (String) ((Map<?, ?>) r.body).get("message");
                return Layout.errorPage(r.status, msg, user);
            }
            values = Layout.formValuesFromItem((Map<String, Object>) r.body, PROPERTIES);
        }
        Map<String, List<Map<String, String>>> refOptions = loadRefOptions(api);
        StringBuilder fields = new StringBuilder();
        for (PropertySpec p : PROPERTIES) {
            fields.append(Layout.renderField(p, values.get(p.name()), refOptions, fieldErrors.getOrDefault(p.name(), List.of()))).append("\n");
        }
        StringBuilder errorBlock = new StringBuilder();
        if (!errors.isEmpty()) {
            errorBlock.append("<div role=\"alert\"><p>Could not save:</p><ul>");
            for (String e : errors) errorBlock.append("<li>").append(Layout.escapeHtml(e)).append("</li>");
            errorBlock.append("</ul></div>");
        }
        String idEsc = Layout.escapeHtml(id);
        String body = errorBlock + "\n" +
            "<form method=\"POST\" action=\"" + BASE + "/" + idEsc + "/edit\">\n" +
            Layout.csrfField(csrf) + "\n" +
            fields +
            "<p><button type=\"submit\">Save</button> · <a href=\"" + BASE + "/" + idEsc + "\">Cancel</a></p>\n" +
            "</form>";
        Map<String, Object> opts2 = new LinkedHashMap<>();
        opts2.put("title", "Edit " + ENTITY);
        opts2.put("currentEntity", ENTITY);
        if (user != null) opts2.put("user", user);
        opts2.put("csrf", csrf);
        opts2.put("body", body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", errors.isEmpty() ? 200 : 400);
        out.put("html", Layout.layout(opts2));
        return out;
    }

    public static Map<String, Object> handleSubmit(Map<String, Object> opts) {
        AdminApiClient api = (AdminApiClient) opts.get("api");
        Object user = opts.get("user");
        String id = (String) opts.get("id");
        String form = (String) opts.getOrDefault("form", "");
        Map<String, Object> payload = Layout.parseFormBody(form, PROPERTIES);
        AdminApiClient.Response r = api.update(ENTITY, id, payload);
        if (r.status == 200) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", 303);
            out.put("redirect", BASE + "/" + id);
            return out;
        }
        if (r.status == 404) return Layout.errorPage(404, ENTITY + " not found.", user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 400);
        out.put("errors", extractErrorList(r.body));
        out.put("values", payload);
        return out;
    }
}
