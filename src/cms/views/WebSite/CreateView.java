package cms.views.WebSite;

import cms.AdminApiClient;
import cms.views.Layout;
import cms.views.PropertySpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CreateView {

    public static final String ENTITY = "WebSite";
    public static final String BASE = "/web-sites";
    public static final List<PropertySpec> PROPERTIES = new ArrayList<>();
    static {
        PROPERTIES.add(new PropertySpec.Scalar("name", "Text", PropertySpec.Cardinality.ONE, true));
        PROPERTIES.add(new PropertySpec.Scalar("description", "Text", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Scalar("url", "URL", PropertySpec.Cardinality.ONE, true));
        PROPERTIES.add(new PropertySpec.Embed("inLanguage", "Language", PropertySpec.Cardinality.ONE, false));
        PROPERTIES.add(new PropertySpec.Ref("image", List.of("ImageObject"), PropertySpec.Cardinality.ONE, false));
    }

    private CreateView() {}

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, String>>> loadRefOptions(AdminApiClient api) {
        Map<String, List<Map<String, String>>> out = new LinkedHashMap<>();
        for (PropertySpec prop : PROPERTIES) {
            if (!(prop instanceof PropertySpec.Ref ref)) continue;
            List<Map<String, String>> collected = new ArrayList<>();
            for (String target : ref.targets()) {
                AdminApiClient.Response r = api.list(target, Map.of("limit", 100));
                if (r.status == 200 && r.body instanceof Map) {
                    Object items = ((Map<?, ?>) r.body).get("items");
                    if (items instanceof List) {
                        for (Object item : (List<?>) items) {
                            Map<String, Object> m = (Map<String, Object>) item;
                            Map<String, String> opt = new LinkedHashMap<>();
                            opt.put("value", m.get("id").toString());
                            opt.put("label", target + ": " + Layout.displayName(m, target));
                            collected.add(opt);
                        }
                    }
                }
            }
            out.put(ref.name(), collected);
        }
        return out;
    }

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
        Map<String, Object> values = (Map<String, Object>) opts.getOrDefault("values", Map.of());
        List<String> errors = (List<String>) opts.getOrDefault("errors", List.of());
        Map<String, List<String>> fieldErrors = (Map<String, List<String>>) opts.getOrDefault("fieldErrors", Map.of());
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
        String body = errorBlock + "\n" +
            "<form method=\"POST\" action=\"" + BASE + "/new\">\n" +
            Layout.csrfField(csrf) + "\n" +
            fields +
            "<p><button type=\"submit\">Create</button> · <a href=\"" + BASE + "\">Cancel</a></p>\n" +
            "</form>";
        Map<String, Object> opts2 = new LinkedHashMap<>();
        opts2.put("title", "New " + ENTITY);
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
        String form = (String) opts.getOrDefault("form", "");
        Map<String, Object> payload = Layout.parseFormBody(form, PROPERTIES);
        AdminApiClient.Response r = api.create(ENTITY, payload);
        if (r.status == 201 && r.body instanceof Map && ((Map<?, ?>) r.body).get("id") != null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", 303);
            out.put("redirect", BASE + "/" + ((Map<?, ?>) r.body).get("id"));
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 400);
        out.put("errors", extractErrorList(r.body));
        out.put("values", payload);
        return out;
    }
}
